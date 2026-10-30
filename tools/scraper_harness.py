#!/usr/bin/env python3
"""
DesiKitchen standalone scraper harness (no APK). Mirrors the app's import cascade:
  web:     public HTML -> JSON-LD Recipe (RecipeHtmlParser equivalent)
  IG:      embed/captioned -> post page og:description -> caption parse (FREE, stop on RAC)
           -> ScrapeCreators /v1/instagram/post ONLY (1 credit; NEVER /media/transcript) -> caption parse
           -> comments (skipped, not wired) -> OpenRouter text (only recipe-like captions)
           -> OpenRouter Gemini on the REMOTE video URL (stills for photo posts)
           -> <= MAX_VIDEO_MB streamed download only if OpenRouter refused the URL
  YT/TT:   oEmbed / watch page -> caption parse -> desc recipe link -> ScrapeCreators post (no transcript)
           -> OpenRouter text (recipe-like) -> OpenRouter video URL
Each RESULT row carries seconds, sc_post_credits, sc_transcript_calls (always 0) and openrouter_calls.
HARNESS_JSONL=path appends rows and SKIPS URLs that already have a RESULT there (resumable; HARNESS_FRESH=1 ignores it).
Reads CREATORS_* / OPENROUTER_* from ../local.properties. NEVER prints secret values; captions truncated to 80 chars.

Usage: python3 tools/scraper_harness.py [--no-video] [--search "query"] [url ...]
Writes JSON lines to stdout and a markdown table to tools/scraper_harness_report.md (when --report given).
"""
import base64, json, os, re, sys, time, html as htmllib
from urllib.parse import quote
import requests

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = "Mozilla/5.0 (Linux; Android 14; Mobile) DesiKitchen/1.1.2 (recipe import)"
MAX_VIDEO_MB = 8
FORCE = os.environ.get("HARNESS_FORCE_ALL") == "1"  # run every fallback stage even after a public-caption success
MODEL_DEFAULT = "google/gemini-2.5-flash"
# Stage 3: cheapest OpenRouter text model with JSON mode (29 Oct 2026 price list: $0.029 in / $0.03 out per M tokens).
TEXT_MODEL_DEFAULT = "mistralai/mistral-nemo"
# Stage 4: keyframes + audio. Gemini 2.5 Flash-Lite ($0.10/M text+image, $0.30/M audio) or 2.5 Flash.
STILLS_MODEL_DEFAULT = "google/gemini-2.5-flash-lite"
MAX_KEYFRAMES = 12
MAX_DOWNLOAD_MB = 60

def load_props():
    p = {}
    with open(os.path.join(ROOT, "local.properties")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1); p[k.strip()] = v.strip()
    return p

PROPS = load_props()
SECRETS = [v for k, v in PROPS.items() if ("KEY" in k or "TOKEN" in k) and len(v) > 6]
CREATORS_URL = PROPS.get("CREATORS_API_URL", "").rstrip("/")
CREATORS_KEY = PROPS.get("CREATORS_API_KEY", "")
OR_KEY = PROPS.get("OPENROUTER_API_KEY", "")
OR_MODEL = PROPS.get("OPENROUTER_MODEL") or MODEL_DEFAULT
TEXT_MODEL = os.environ.get("OPENROUTER_TEXT_MODEL") or PROPS.get("OPENROUTER_TEXT_MODEL") or TEXT_MODEL_DEFAULT
STILLS_MODEL = os.environ.get("OPENROUTER_STILLS_MODEL") or PROPS.get("OPENROUTER_STILLS_MODEL") or STILLS_MODEL_DEFAULT

def scrub(s):
    s = str(s)
    for sec in SECRETS: s = s.replace(sec, "***")
    return s

def short(s, n=80):
    if s is None: return None
    s = re.sub(r"\s+", " ", s).strip()
    return scrub(s[:n] + ("…" if len(s) > n else ""))

LOG = []
def log(url, stage, ok, **kw):
    rec = {"url": url, "stage": stage, "pass": bool(ok)}
    for k, v in kw.items():
        if v is None: continue
        rec[k] = short(v, 160) if isinstance(v, str) and k != "caption" else (short(v) if k == "caption" else v)
    LOG.append(rec); print(json.dumps(rec, ensure_ascii=False)); sys.stdout.flush()
    return rec

def exc(e): return f"{type(e).__name__}: {scrub(e)[:140]}"

def get(url, accept="text/html,*/*", headers=None, timeout=25):
    h = {"User-Agent": UA, "Accept": accept, "Accept-Language": "en-IN,en;q=0.9"}
    h.update(headers or {})
    return requests.get(url, headers=h, timeout=timeout, allow_redirects=True)

# ---------- parsers ----------
def jsonld_recipe(html):
    for m in re.finditer(r'<script[^>]+application/ld\+json[^>]*>(.*?)</script>', html, re.S | re.I):
        try: data = json.loads(m.group(1).strip())
        except Exception: continue
        stack = [data]
        while stack:
            o = stack.pop()
            if isinstance(o, list): stack.extend(o); continue
            if not isinstance(o, dict): continue
            t = o.get("@type"); t = t if isinstance(t, list) else [t]
            if "Recipe" in t: return o
            if "@graph" in o: stack.append(o["@graph"])
    return None

def count_steps(instr):
    if not instr: return 0
    if isinstance(instr, str): return len([x for x in re.split(r"\n|\. ", instr) if x.strip()])
    n = 0
    for s in instr:
        if isinstance(s, dict) and s.get("@type") == "HowToSection": n += count_steps(s.get("itemListElement"))
        else: n += 1
    return n

ING_HEAD = re.compile(r"^\W*(ingredients?|you(?:'ll)? need|what you need|for the [a-z ]+)\W*:?\s*$", re.I)
STEP_QUAL = r"(?:(?:quick|easy|simple|full|basic|detailed|short|cooking|making|recipe|step[ -]by[ -]step|the|my|our)\s+){0,2}"  # mirrors CaptionRecipeParser.stepQualifier
STEP_HEAD = re.compile(r"^\W*" + STEP_QUAL + r"(method|instructions?|steps?|directions?|how to make|procedure|recipe)\W*:?\s*$", re.I)
OUTRO = re.compile(r"^\W*(tag (a|your)|follow (@|me|us|for)|save (this|for later)|share (this|with)|comment |dm (me|us)|link in (my )?bio|like (and|&) (save|share)|for more (recipes|such)|subscribe)", re.I)
QTY = re.compile(r"(\d|½|¼|¾|cup|tbsp|tsp|teaspoon|tablespoon|gram|\bg\b|kg|ml|pinch|handful|as needed|to taste)", re.I)

def caption_parse(caption):
    """Approximation of CaptionRecipeParser: ingredient lines under an Ingredients heading or bullet/qty lines; numbered steps."""
    if not caption: return 0, 0
    lines = [l.strip() for l in caption.replace("\r", "").split("\n")]
    ing, steps, mode = 0, 0, None
    for l in lines:
        if not l or l.startswith("#"): continue
        if ING_HEAD.match(l): mode = "i"; continue
        if STEP_HEAD.match(l): mode = "s"; continue
        if mode and OUTRO.match(l): mode = "o"; continue  # like the Kotlin parser: outro ends the recipe
        if re.match(r"^\s*(step\s*)?\d+[.)]\s+\S", l, re.I) and len(l) > 25: steps += 1; mode = mode or "s"; continue
        if mode == "i" and len(l) < 80: ing += 1
        elif mode == "s" and len(l) > 15: steps += 1
        elif re.match(r"^[-•*▪️🔸✅👉➡️]\s*", l) and QTY.search(l) and len(l) < 80: ing += 1
    return ing, steps

def parse_embed(html):
    m = re.search(r'<div class="Caption">(.*?)</div>\s*<div class="CaptionComments"|<div class="Caption">(.*?)</div>', html, re.S)
    cap = None
    if m:
        raw = m.group(1) or m.group(2)
        raw = re.sub(r'<a class="CaptionUsername".*?</a>', "", raw, flags=re.S)
        raw = re.sub(r"<br\s*/?>", "\n", raw)
        cap = htmllib.unescape(re.sub(r"<[^>]+>", "", raw)).strip() or None
    vid = re.search(r'\\?"video_url\\?":\s*\\?"(https:[^"]+?)\\?"', html)
    return cap, (vid.group(1).replace("\\/", "/").replace("\\u0026", "&") if vid else None)

def og(html, prop):
    m = re.search(r'<meta[^>]+(?:property|name)="%s"[^>]+content="([^"]*)"' % re.escape(prop), html)
    return htmllib.unescape(m.group(1)) if m else None

# ---------- external APIs ----------
def creators(path, **params):
    if "transcript" in path: COST["sc_transcript"] += 1  # must stay 0 (Sai's rule); reported per URL
    q = "&".join(f"{k}={quote(str(v), safe='')}" for k, v in params.items())
    base = CREATORS_URL if path.startswith("/instagram") or path.startswith("/youtube") else CREATORS_URL.removesuffix("/v1")
    r = requests.get(f"{base}{path}?{q}", headers={"x-api-key": CREATORS_KEY, "Accept": "application/json", "User-Agent": UA}, timeout=60)
    try: j = r.json()
    except Exception: j = None
    return r.status_code, j

def creators_comments(j):
    """Creator-authored comments already inside the /instagram/post response (no extra credit)."""
    if not isinstance(j, dict): return []
    d = j.get("data") if isinstance(j.get("data"), dict) else {}
    o = d.get("xdt_shortcode_media") or d
    owner = ((o.get("owner") or {}).get("username")) if isinstance(o, dict) else None
    out = []
    for key in ("edge_media_to_parent_comment", "edge_media_preview_comment", "edge_media_to_comment"):
        for e in ((o.get(key) or {}).get("edges") or []) if isinstance(o, dict) else []:
            n = (e or {}).get("node") or {}
            t = n.get("text")
            if t: out.append(((n.get("owner") or {}).get("username") == owner, t))
    seen, res = set(), []
    for own, t in out:  # creator's own comments only (mirrors CreatorsApiClient.comments)
        if own and t not in seen: seen.add(t); res.append(t)
    return res

def creators_media(j):
    """Mirrors CreatorsApiClient.parse."""
    if not isinstance(j, dict): return None, None
    d = j.get("data") if isinstance(j.get("data"), dict) else None
    o = (d or {}).get("xdt_shortcode_media") or d or j.get("result") or j
    cap = None
    try: cap = o["edge_media_to_caption"]["edges"][0]["node"]["text"]
    except Exception: cap = (o.get("caption") or {}).get("text") if isinstance(o.get("caption"), dict) else o.get("caption")
    return cap, o.get("video_url") or o.get("videoUrl")

SYS = ("You extract recipes from social-media captions, creator comments and video descriptions. Write down ONLY "
       "ingredients and steps explicitly stated. Never guess. If no recipe, return empty lists. Reply with JSON only: "
       '{"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}')

def openrouter(messages, timeout=120, model=None, max_tokens=1500):
    """One OpenRouter chat call. Returns (http, obj, err). Records model, tokens, USD (usage.cost from the API) in COST."""
    model = model or OR_MODEL
    t0 = time.time()
    r = requests.post("https://openrouter.ai/api/v1/chat/completions", timeout=timeout,
                      headers={"Authorization": f"Bearer {OR_KEY}", "Content-Type": "application/json", "X-Title": "DesiKitchen"},
                      json={"model": model, "temperature": 0, "max_tokens": max_tokens, "response_format": {"type": "json_object"},
                            "usage": {"include": True}, "messages": messages})
    code = r.status_code
    try: body = r.json()
    except Exception: body = None
    u = (body or {}).get("usage") or {}
    call = {"model": (body or {}).get("model") or model, "http": code, "seconds": round(time.time() - t0, 2),
            "prompt_tokens": u.get("prompt_tokens"), "completion_tokens": u.get("completion_tokens"), "usd": u.get("cost")}
    COST["or_detail"].append(call)
    if code != 200 or not body or "choices" not in body:
        return code, None, short(((body or {}).get("error") or {}).get("message") or r.text[:100], 140)
    c = body["choices"][0]["message"].get("content") or ""
    c = c.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try: o = json.loads(c)
    except Exception as e: return code, None, f"bad model JSON ({type(e).__name__})"
    return code, o, None

def or_text(url, text):
    code, o, err = openrouter([{"role": "system", "content": SYS}, {"role": "user", "content": text[:12000]}])
    ing, st = (len(o.get("ingredients") or []), len(o.get("steps") or [])) if o else (0, 0)
    return log(url, "openrouter_text", ing > 0, http=code, error=err, ingredients=ing, steps=st, title=(o or {}).get("title"))

def or_video(url, video_url, caption):
    try:
        r = requests.get(video_url, headers={"User-Agent": UA}, timeout=60, stream=True)
        if r.status_code != 200: return log(url, "video_download", False, http=r.status_code)
        buf = bytearray()
        for chunk in r.iter_content(65536):
            buf += chunk
            if len(buf) > MAX_VIDEO_MB * 1_000_000:
                return log(url, "video_download", False, http=200, error=f"video > {MAX_VIDEO_MB} MB cap")
        log(url, "video_download", True, http=200, mb=round(len(buf) / 1e6, 2))
        prompt = "You transcribe cooking videos. Write ONLY the recipe spoken/shown. Never guess. JSON only: " \
                 '{"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}' + \
                 (f"\nCaption: {caption[:1500]}" if caption else "")
        code, o, err = openrouter([{"role": "user", "content": [{"type": "text", "text": prompt},
                                    {"type": "video_url", "video_url": {"url": "data:video/mp4;base64," + base64.b64encode(bytes(buf)).decode()}}]}], timeout=180)
        ing, st = (len(o.get("ingredients") or []), len(o.get("steps") or [])) if o else (0, 0)
        return log(url, "openrouter_video", ing > 0, http=code, error=err, ingredients=ing, steps=st, title=(o or {}).get("title"))
    except Exception as e:
        return log(url, "openrouter_video", False, error=exc(e))

# ---------- cascades ----------
def run_web(url):
    try:
        r = get(url)
        log(url, "public_html", r.status_code == 200, http=r.status_code, bytes=len(r.content))
        rec = jsonld_recipe(r.text)
        if not rec: return log(url, "jsonld_recipe", False, error="no Recipe JSON-LD") and "FAIL"
        ing, st = len(rec.get("recipeIngredient") or []), count_steps(rec.get("recipeInstructions"))
        log(url, "jsonld_recipe", ing > 0 and st > 0, ingredients=ing, steps=st, title=rec.get("name"))
        return "FULL" if ing and st else "PARTIAL"
    except Exception as e:
        log(url, "public_html", False, error=exc(e)); return "FAIL"

QTY_TOKENS = re.compile(r"(?i)\b\d+(?:[./]\d+)?\s*(?:cups?|tbsp|tsp|teaspoons?|tablespoons?|g|gm|grams?|kg|ml|l|litres?|pinch|inch)\b|[½¼¾]")
def looks_like_recipe(caption):
    """Mirrors InstagramImporter.looksLikeRecipeText: only then is a caption-only text-AI call worth it."""
    return bool(caption) and len(caption) >= 80 and len(QTY_TOKENS.findall(caption)) >= 3

VIDEO_PROMPT = ("You transcribe cooking videos. Write down ONLY the recipe that is spoken aloud or shown as on-screen text in this video. "
                "Never guess or add ingredients, quantities or steps that are not said or shown; if a quantity isn't stated, write the ingredient without one. "
                "Keep the creator's wording, translated to English if needed; if it does not present a recipe, return empty lists. "
                '"title" is the dish name in plain English (e.g. "Bread Pockets"), not the caption\'s chit-chat or hashtags. '
                'Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}')

def or_media_url(url, media_url, caption, part="video_url"):
    """Remote URL straight to OpenRouter (no download). Returns (record, refused)."""
    COST["openrouter"] += 1
    prompt = VIDEO_PROMPT if part == "video_url" else VIDEO_PROMPT.replace("transcribe cooking videos", "read recipe photos").replace("spoken aloud or shown as on-screen text in this video", "visible in this image")
    if caption: prompt += "\nThe post caption (only a hint for the dish name; never take ingredients from it unless they are also said or shown):\n" + caption[:1500]
    try:
        code_, o, err = openrouter([{"role": "user", "content": [{"type": "text", "text": prompt}, {"type": part, part: {"url": media_url}}]}], timeout=180)
    except Exception as e:
        return log(url, "openrouter_" + part, False, error=exc(e)), True
    ing, st = (len(o.get("ingredients") or []), len(o.get("steps") or [])) if o else (0, 0)
    COST["last_media"] = {"title": (o or {}).get("title"), "ingredients": (o or {}).get("ingredients") or [], "steps": (o or {}).get("steps") or []}
    rec = log(url, "openrouter_" + part, ing > 0, http=code_, error=err, ingredients=ing, steps=st, title=(o or {}).get("title"),
              sample=", ".join((o or {}).get("ingredients") or [])[:120] or None)
    return rec, code_ != 200

# ---------- Instagram: Sai's 4-step cascade (1.1.3) ----------
COMMENTS_SYS = ("You read an Instagram cooking reel's caption and its top comments. Some comment or the caption MAY contain the "
                "recipe (often pinned by the creator). Copy ONLY ingredients and steps that are written in that text, in English. "
                "Never guess, never infer from the dish name, never use a viewer's suggestion or question as an ingredient. "
                "If the text does not contain a recipe, return empty lists. Reply with JSON only: "
                '{"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string], "source": "caption"|"comment"|null}')

STILLS_PROMPT = ("These are keyframes (in order) and the audio track of one Instagram cooking reel. Write down ONLY the recipe that is "
                 "spoken in the audio or shown in the frames (on-screen text, visible ingredients being added). Never guess or add "
                 "ingredients, quantities or steps that are not said or shown; if a quantity isn't stated, write the ingredient without one. "
                 "Translate to English if needed. If no recipe is presented, return empty lists. "
                 '"title" is the dish name in plain English (e.g. "Bread Pockets"), not caption chit-chat or hashtags. '
                 'Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}')

STOP = {"and", "the", "for", "with", "some", "few", "cup", "cups", "tbsp", "tsp", "gram", "grams", "taste", "needed", "little",
        "chopped", "finely", "fresh", "small", "large", "medium", "piece", "pieces", "slice", "slices", "pinch", "spoon"}
def grounded(items, source):
    """Never invent: keep an AI ingredient only if one of its content words (>=3 letters) is in the source text."""
    src = (source or "").lower()
    out = []
    for it in items or []:
        words = [w for w in re.findall(r"[a-z\u0900-\u097f]{3,}", str(it).lower()) if w not in STOP]
        if words and any(w in src for w in words): out.append(it)
    return out

def top_comments(sc_json, n=15):
    """All top-level comments in the /instagram/post response (no extra credit), most-liked first, creator's own first."""
    if not isinstance(sc_json, dict): return []
    d = sc_json.get("data") if isinstance(sc_json.get("data"), dict) else {}
    o = d.get("xdt_shortcode_media") or d
    owner = (o.get("owner") or {}).get("username")
    seen, rows = set(), []
    for key in ("edge_media_to_parent_comment", "edge_media_preview_comment", "edge_media_to_comment"):
        for e in (o.get(key) or {}).get("edges") or []:
            nd = (e or {}).get("node") or {}
            t = (nd.get("text") or "").strip()
            if not t or t in seen: continue
            seen.add(t)
            rows.append(((nd.get("owner") or {}).get("username") == owner, (nd.get("edge_liked_by") or {}).get("count") or 0, t))
    rows.sort(key=lambda r: (not r[0], -r[1]))
    return [("[creator] " if own else "") + t for own, _, t in rows[:n]]

def stage(name, t0):
    COST["stages"][name] = round(time.time() - t0, 2)

def finish(res, recipe=None):
    if recipe: COST["recipe"] = recipe
    return res

def stills_audio(url, video_url, caption):
    """Stage 4: stream the CDN video to a temp file, ffmpeg keyframes (-skip_frame nokey, ~512 px) + mono low-bitrate MP3,
    one OpenRouter Gemini call with stills + audio (+ caption hint). Temp media deleted afterwards. Returns (obj|None, info)."""
    import tempfile, shutil, subprocess, glob
    tmp = tempfile.mkdtemp(prefix="dk_stills_")
    info = {}
    try:
        t = time.time()
        r = requests.get(video_url, headers={"User-Agent": UA}, timeout=60, stream=True)
        if r.status_code != 200: log(url, "s4_download", False, http=r.status_code); return None, info
        size = 0
        with open(os.path.join(tmp, "v.mp4"), "wb") as f:
            for chunk in r.iter_content(256 * 1024):
                size += len(chunk); f.write(chunk)
                if size > MAX_DOWNLOAD_MB * 1_000_000: log(url, "s4_download", False, error=f"> {MAX_DOWNLOAD_MB} MB"); return None, info
        stage("4a_download", t); info["video_mb"] = round(size / 1e6, 2)
        log(url, "s4_download", True, http=200, mb=info["video_mb"], seconds=COST["stages"]["4a_download"])
        t = time.time()
        cmd = ["ffmpeg", "-v", "error", "-y", "-skip_frame", "nokey", "-i", os.path.join(tmp, "v.mp4"),
               "-map", "0:v:0", "-vf", "scale=512:-2", "-fps_mode", "vfr", "-q:v", "5", os.path.join(tmp, "f_%03d.jpg"),
               "-map", "0:a:0?", "-vn", "-ac", "1", "-ar", "16000", "-b:a", "32k", os.path.join(tmp, "a.mp3")]
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        frames = sorted(glob.glob(os.path.join(tmp, "f_*.jpg")))
        if len(frames) > MAX_KEYFRAMES:  # evenly sample, always keep first and last
            idx = sorted({round(i * (len(frames) - 1) / (MAX_KEYFRAMES - 1)) for i in range(MAX_KEYFRAMES)})
            frames = [frames[i] for i in idx]
        audio = os.path.join(tmp, "a.mp3")
        has_audio = os.path.exists(audio) and os.path.getsize(audio) > 1000
        stage("4b_ffmpeg", t)
        info.update(keyframes_total=len(glob.glob(os.path.join(tmp, "f_*.jpg"))), keyframes_sent=len(frames),
                    stills_kb=round(sum(os.path.getsize(f) for f in frames) / 1024), audio_kb=round(os.path.getsize(audio) / 1024) if has_audio else 0)
        log(url, "s4_ffmpeg", bool(frames), rc=p.returncode, error=short(p.stderr, 140) if p.returncode else None,
            seconds=COST["stages"]["4b_ffmpeg"], **info)
        if not frames and not has_audio: return None, info
        t = time.time()
        prompt = STILLS_PROMPT + (("\nThe post caption (only a hint for the dish name; never take ingredients from it unless they are "
                                   "also said or shown):\n" + caption[:1500]) if caption else "")
        parts = [{"type": "text", "text": prompt}]
        for f in frames:
            parts.append({"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + base64.b64encode(open(f, "rb").read()).decode()}})
        if has_audio:
            parts.append({"type": "input_audio", "input_audio": {"data": base64.b64encode(open(audio, "rb").read()).decode(), "format": "mp3"}})
        COST["openrouter"] += 1
        code_, o, err = openrouter([{"role": "user", "content": parts}], timeout=180, model=STILLS_MODEL, max_tokens=2000)
        stage("4c_gemini_stills_audio", t)
        ing, st = (len(o.get("ingredients") or []), len(o.get("steps") or [])) if o else (0, 0)
        log(url, "s4_openrouter_stills_audio", ing > 0, http=code_, error=err, model=STILLS_MODEL, ingredients=ing, steps=st,
            title=(o or {}).get("title"), seconds=COST["stages"]["4c_gemini_stills_audio"], usage=COST["or_detail"][-1])
        return o, info
    except Exception as e:
        log(url, "s4_stills_audio", False, error=exc(e)); return None, info
    finally:
        shutil.rmtree(tmp, ignore_errors=True)  # discard temp media
        info["temp_deleted"] = not os.path.exists(tmp)

def run_instagram(url, video=True):
    m = re.search(r"instagram\.com/(?:[\w.]+/)?(p|reel|reels|tv)/([\w-]{5,})", url)
    if not m: log(url, "shortcode", False, error="not a post/reel link"); return "FAIL"
    kind, code = ("reel" if m.group(1) == "reels" else m.group(1)), m.group(2)
    canon = f"https://www.instagram.com/{kind}/{code}/"
    caption, video_url, still = None, None, None
    # ---- Stage 1: public HTML / embed (FREE). Stop if RAC found. ----
    t = time.time()
    try:
        r = get(f"https://www.instagram.com/{kind}/{code}/embed/captioned/")
        caption, video_url = parse_embed(r.text) if r.status_code == 200 else (None, None)
        log(url, "s1_ig_embed", caption is not None, http=r.status_code, caption=caption, has_video=bool(video_url))
    except Exception as e: log(url, "s1_ig_embed", False, error=exc(e))
    if not caption:
        try:
            r = get(canon)
            d = og(r.text, "og:description") if r.status_code == 200 else None
            still = og(r.text, "og:image") if r.status_code == 200 else None
            if d:
                mm = re.search(r'\bon [^:]{3,40}:\s*["“](.*)["”]\.?\s*$', d, re.S); d = mm.group(1) if mm else d
                if len(d) <= 40 or "See Instagram photos" in d: d = None
            caption = d
            log(url, "s1_ig_post_og", d is not None, http=r.status_code, caption=d)
        except Exception as e: log(url, "s1_ig_post_og", False, error=exc(e))
    ing, st = caption_parse(caption)
    stage("1_public_html", t)
    log(url, "s1_caption_parse", ing >= 2, ingredients=ing, steps=st, seconds=COST["stages"]["1_public_html"])
    if ing >= 2 and not FORCE:
        return finish("FULL(stage1 caption)" if st else "PARTIAL(stage1 caption)", {"title": None, "ingredients_count": ing, "steps_count": st})
    # ---- Stage 2: ONE Scrape Creators /instagram/post request (1 credit). Video URL, audio flag, caption, top comments. ----
    t = time.time()
    sc_json, comments, has_audio = None, [], None
    if CREATORS_URL and CREATORS_KEY:
        try:
            COST["sc_post"] += 1
            http, j = creators("/instagram/post", url=canon)
            ok = http == 200 and isinstance(j, dict) and j.get("success", True) is not False
            c2, v2 = creators_media(j) if ok else (None, None)
            if ok:
                sc_json = j
                o = (j.get("data") or {}).get("xdt_shortcode_media") or j.get("data") or {}
                still = still or o.get("thumbnail_src") or o.get("display_url")
                has_audio = o.get("has_audio")
                comments = top_comments(j)
            video_url = v2 or video_url
            caption = c2 if c2 and len(c2) > len(caption or "") else caption
            ing, st = caption_parse(c2)
            stage("2_scrapecreators_post", t)
            log(url, "s2_scrapecreators_post", bool(ok), http=http, error=None if ok else short(json.dumps(j)[:150] if j else "no JSON body", 150),
                caption=c2, has_video=bool(v2), has_audio=has_audio, top_comments=len(comments), caption_ingredients=ing,
                credits_remaining=(j or {}).get("credits_remaining"), seconds=COST["stages"]["2_scrapecreators_post"])
            if ing >= 2 and not FORCE:  # free re-parse of the fuller SC caption
                return finish("FULL(stage2 SC caption)" if st else "PARTIAL(stage2 SC caption)", {"ingredients_count": ing, "steps_count": st})
        except Exception as e: log(url, "s2_scrapecreators_post", False, error=exc(e))
    else: log(url, "s2_scrapecreators_post", False, error="not configured")
    # ---- Stage 3: cheapest text model on top comments (+ caption). Grounded; stop if RAC (>= 3 ingredients). ----
    best = None
    if OR_KEY and (comments or caption):
        t = time.time()
        text = "Caption:\n" + (caption or "(none)")[:3000] + "\n\nTop comments:\n" + "\n".join(f"- {c[:500]}" for c in comments)
        COST["openrouter"] += 1
        try:
            code_, o, err = openrouter([{"role": "system", "content": COMMENTS_SYS}, {"role": "user", "content": text}], model=TEXT_MODEL, max_tokens=1200)
            raw = (o or {}).get("ingredients") or []
            ings = grounded(raw, text)
            steps = (o or {}).get("steps") or []
            if not ings: steps = []
            stage("3_cheap_text_comments", t)
            log(url, "s3_openrouter_comments", len(ings) >= 3, http=code_, error=err, model=TEXT_MODEL, comments=len(comments),
                ingredients_raw=len(raw), ingredients_grounded=len(ings), steps=len(steps), dropped=", ".join(str(x) for x in raw if x not in ings)[:120] or None,
                seconds=COST["stages"]["3_cheap_text_comments"], usage=COST["or_detail"][-1])
            if len(ings) >= 3:
                return finish("PARTIAL(stage3 comments AI)", {"title": o.get("title"), "ingredients": ings, "steps": steps})
        except Exception as e: log(url, "s3_openrouter_comments", False, error=exc(e))
    # ---- Stage 4: keyframes + audio -> Gemini. Full video only as a thin-result re-read (< 4 ingredients). ----
    if not (OR_KEY and video):
        return "FAIL"
    if not video_url:
        if still:  # photo post: the image IS the content
            t = time.time()
            rec, _ = or_media_url(url, still, caption, "image_url")
            stage("4_photo_still", t)
            return "PARTIAL(stage4 photo still)" if rec["pass"] else "FAIL"
        log(url, "s4_stills_audio", False, error="no video URL"); return "FAIL"
    o, info = stills_audio(url, video_url, caption)
    ings = (o or {}).get("ingredients") or []
    if o and ings: best = ("PARTIAL(stage4 keyframes+audio)", {"title": o.get("title"), "servings": o.get("servings"), "ingredients": ings, "steps": o.get("steps") or [], "media": info})
    if len(ings) < 4 and os.environ.get("HARNESS_NO_REREAD") != "1":
        t = time.time()
        log(url, "s4_thin_result_reread", True, note=f"keyframes gave {len(ings)} ingredients (< 4): optional full-video re-read via remote video_url")
        rec, _ = or_media_url(url, video_url, caption, "video_url")
        stage("4d_thin_reread_full_video", t)
        if rec["pass"] and rec.get("ingredients", 0) > len(ings): best = ("PARTIAL(stage4 full-video re-read)", COST.get("last_media"))
    return finish(*best) if best else "FAIL"


def run_social(url):
    is_yt = "youtu" in url
    caption, title = None, None
    try:
        if is_yt:
            r = get("https://www.youtube.com/oembed?format=json&url=" + quote(url, safe=""), accept="application/json")
            title = r.json().get("title") if r.status_code == 200 else None
            log(url, "oembed", r.status_code == 200, http=r.status_code, caption=title)
            w = get(url)
            m = re.search(r'"shortDescription":"((?:\\.|[^"\\])*)"', w.text)
            caption = json.loads('"%s"' % m.group(1)) if m else None
            log(url, "yt_watch_desc", caption is not None, http=w.status_code, caption=caption)
        else:
            r = get("https://www.tiktok.com/oembed?url=" + quote(url, safe=""), accept="application/json")
            caption = r.json().get("title") if r.status_code == 200 else None
            log(url, "oembed", caption is not None, http=r.status_code, caption=caption)
    except Exception as e: log(url, "public", False, error=exc(e))
    ing, st = caption_parse(caption)
    log(url, "caption_parse", ing >= 2, ingredients=ing, steps=st)
    if ing >= 2: return "FULL" if st else "PARTIAL"
    # Description links to the creator's recipe page ("Full Recipe: https://…") -> normal web import.
    for link in re.findall(r"https?://[^\s\"'<>]+", caption or ""):
        if re.search(r"(youtu|instagram|facebook|tiktok|twitter|x\.com|amzn|amazon|bit\.ly/?$)", link): continue
        log(url, "desc_recipe_link", True, link=link)
        res = run_web(link)
        if res != "FAIL": return res + "(via desc link)"
        break
    video_url = None
    if CREATORS_KEY:  # post endpoint only, never a transcript
        try:
            COST["sc_post"] += 1
            path = "/youtube/video" if is_yt else "/v2/tiktok/video"
            http, j = creators(path, url=url)
            c2 = (j or {}).get("description") if is_yt else ((j or {}).get("aweme_detail") or {}).get("desc")
            if not is_yt:
                ul = ((((j or {}).get("aweme_detail") or {}).get("video") or {}).get("play_addr") or {}).get("url_list") or []
                video_url = ul[0] if ul else None
            log(url, "scrapecreators_post", http == 200, http=http, caption=c2, credits_remaining=(j or {}).get("credits_remaining"))
            if c2 and len(c2) > len(caption or ""): caption = c2
        except Exception as e: log(url, "scrapecreators_post", False, error=exc(e))
    if OR_KEY and looks_like_recipe(caption):
        COST["openrouter"] += 1
        text = "\n\n".join(x for x in [title and f"Title: {title}", caption and f"Caption:\n{caption}"] if x)
        try:
            if or_text(url, text)["pass"]: return "PARTIAL(AI text)"
        except Exception as e: log(url, "openrouter_text", False, error=exc(e))
    if OR_KEY and video_url:
        rec, _ = or_media_url(url, video_url, caption)
        if rec["pass"]: return "PARTIAL(AI video URL)"
    return "FAIL"

COST = {"sc_post": 0, "sc_transcript": 0, "openrouter": 0, "or_detail": [], "stages": {}}

def run(url, video=True):
    t = time.time()
    for k in ("sc_post", "sc_transcript", "openrouter"): COST[k] = 0
    COST["or_detail"], COST["stages"] = [], {}
    if "instagram.com" in url: res = run_instagram(url, video)
    elif "youtu" in url or "tiktok.com" in url: res = run_social(url)
    else: res = run_web(url)
    log(url, "RESULT", not res.startswith("FAIL"), result=res, seconds=round(time.time() - t, 1),
        sc_post_credits=COST["sc_post"], sc_transcript_calls=COST["sc_transcript"], openrouter_calls=COST["openrouter"],
        stage_seconds=COST["stages"] or None, openrouter_detail=COST["or_detail"] or None,
        openrouter_usd=round(sum((c.get("usd") or 0) for c in COST["or_detail"]), 6) if COST["or_detail"] else None,
        recipe=COST.get("recipe"))
    return res

def search_reels(query):
    http, j = creators("/v2/instagram/reels/search", query=query)
    reels = (j or {}).get("reels") or (j or {}).get("items") or []
    urls = [r.get("url") or (f"https://www.instagram.com/reel/{r.get('shortcode')}/" if r.get("shortcode") else None) for r in reels if isinstance(r, dict)]
    urls = [u for u in urls if u]
    log(f"search:{query}", "scrapecreators_reels_search", http == 200 and bool(urls), http=http, results=len(urls),
        credits_remaining=(j or {}).get("credits_remaining"), error=None if http == 200 else short(json.dumps(j)[:150] if j else "", 150))
    return urls

if __name__ == "__main__":
    args = sys.argv[1:]
    video = "--no-video" not in args
    args = [a for a in args if a != "--no-video"]
    print(json.dumps({"config": {"CREATORS_API_URL": "present" if CREATORS_URL else "absent", "CREATORS_API_KEY": "present" if CREATORS_KEY else "absent",
                                 "OPENROUTER_API_KEY": "present" if OR_KEY else "absent", "OPENROUTER_MODEL": OR_MODEL}}))
    out = os.environ.get("HARNESS_JSONL")
    done = set()
    if out and os.path.exists(out) and os.environ.get("HARNESS_FRESH") != "1":
        with open(out) as f:
            for line in f:
                try: r = json.loads(line)
                except Exception: continue
                if r.get("stage") == "RESULT": done.add(r.get("url"))
    def run(u, video, _run=run):
        if u in done: print(json.dumps({"url": u, "stage": "SKIP", "pass": True, "note": "already in " + os.path.basename(out)})); return
        _run(u, video)
        if out:
            with open(out, "a") as f:
                for r in LOG: f.write(json.dumps(r, ensure_ascii=False) + "\n")
            LOG.clear()
    i = 0
    while i < len(args):
        if args[i] == "--search":
            for u in search_reels(args[i + 1])[:int(os.environ.get("SEARCH_TAKE", "1"))]: run(u, video)
            i += 2; continue
        run(args[i], video); i += 1
