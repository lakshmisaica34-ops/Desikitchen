#!/usr/bin/env python3
"""Compile every Regex("...") / SafeRegex.of("name", "...") literal in app/src/main with the system ICU regex engine (what Android uses).

Why: the JDK and Android (ICU) regex engines disagree on some syntax. 1.1.4 shipped a pattern ([:.)\\-]) that the JDK
accepts but ICU rejects, crashing CaptionRecipeParser's static init on phones. JVM unit tests can't see that; this can.
Needs libicu (e.g. libicu76 / libicu-dev). Usage: python3 tools/icu_regex_check.py [src_root]   (exit 1 on failure)
"""
import ctypes, ctypes.util, glob, os, re, sys

def load_icu():
    for v in range(90, 59, -1):
        try:
            i18n = ctypes.CDLL(f"libicui18n.so.{v}"); uc = ctypes.CDLL(f"libicuuc.so.{v}")
            return getattr(i18n, f"uregex_open_{v}"), getattr(uc, f"u_errorName_{v}"), getattr(i18n, f"uregex_close_{v}")
        except OSError:
            continue
    sys.exit("libicu not found (install libicu)")

op, err_name, close = load_icu()
op.restype = ctypes.c_void_p
op.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p, ctypes.POINTER(ctypes.c_int)]
err_name.restype = ctypes.c_char_p
close.argtypes = [ctypes.c_void_p]

class ParseError(ctypes.Structure):
    _fields_ = [("line", ctypes.c_int32), ("offset", ctypes.c_int32), ("pre", ctypes.c_uint16 * 16), ("post", ctypes.c_uint16 * 16)]

def icu_error(pattern, flags=2):  # 2 = UREGEX_CASE_INSENSITIVE (superset of what the app uses)
    b = pattern.encode("utf-16-le"); pe = ParseError(); st = ctypes.c_int(0)
    h = op(ctypes.create_string_buffer(b), len(b) // 2, flags, ctypes.byref(pe), ctypes.byref(st))
    if h: close(h)
    return (err_name(st.value).decode(), pe.offset) if st.value > 0 else None

def kotlin_unescape(s):
    out, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == "u":
                out.append(chr(int(s[i + 2:i + 6], 16))); i += 6; continue
            out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b"}.get(n, n)); i += 2; continue
        out.append(c); i += 1
    return "".join(out)

LIT = re.compile(r'"((?:\\.|[^"\\])*)"')
# String-template values used inside patterns (keep in sync with CaptionRecipeParser).
CONSTS = {"stepQualifier": r"(?:(?:quick|easy|simple|full|basic|detailed|short|cooking|making|recipe|step[ -]by[ -]step|the|my|our)\s+){0,2}"}

def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")
    n = bad = 0
    for f in sorted(glob.glob(os.path.join(root, "**", "*.kt"), recursive=True)):
        src = open(f, encoding="utf-8").read()
        for m in re.finditer(r'\b(?:Regex|Pattern\.compile)\(\s*|\bSafeRegex\.of\(\s*"[^"]*"\s*,\s*', src):
            rest, parts, j = src[m.end():], [], 0
            while True:
                mm = LIT.match(rest, j)
                if not mm: break
                parts.append(mm.group(1)); j = mm.end()
                k = re.match(r"\s*\+\s*", rest[j:])
                if k: j += k.end()
                else: break
            if not parts: continue
            p = re.sub(r"\$(\w+)", lambda x: CONSTS.get(x.group(1), x.group(0)), kotlin_unescape("".join(parts)))
            n += 1
            e = icu_error(p)
            if e:
                bad += 1
                print(f"ICU FAIL {os.path.relpath(f, root)}:{src[:m.start()].count(chr(10)) + 1} {e[0]} at offset {e[1]}: {p[:140]!r}")
    print(f"checked {n} regex literals, {bad} rejected by ICU")
    sys.exit(1 if bad else 0)

if __name__ == "__main__":
    main()
