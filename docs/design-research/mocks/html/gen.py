import os
S="#5BAF8A";F="#EAF6EE";D="#3F8F6C";P="#F3FAF6";INK="#17171A";MU="#8A8A8E";HL="#E8E8EA"
CSS=f"""
html{{background:#FFFFFF}}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{font-family:Inter,Roboto,sans-serif;background:#F1F1F3;color:{INK};padding:36px 40px 40px;display:inline-block}}
.hdr{{font-size:15px;color:{MU};letter-spacing:.02em;margin-bottom:6px}}
.ttl{{font-size:26px;font-weight:700;margin-bottom:24px}}
.row{{display:flex;gap:40px;align-items:flex-start}}
.col{{display:flex;flex-direction:column;align-items:center;gap:14px}}
.tag{{font-size:14px;font-weight:700;letter-spacing:.08em;padding:7px 14px;border-radius:999px}}
.tag.bad{{background:#FDECEC;color:#B3261E}} .tag.good{{background:{F};color:{D}}} .tag.n{{background:#fff;color:{INK};border:1px solid {HL}}}
.note{{font-size:14px;color:{MU};max-width:410px;text-align:center;line-height:1.45}}
.cap{{margin-top:26px;font-size:20px;font-weight:600;background:#fff;border:1px solid {HL};border-radius:16px;padding:14px 20px;display:inline-block}}
.cap b{{color:{D}}}
.bezel{{background:{INK};padding:10px;border-radius:58px;box-shadow:0 20px 50px rgba(23,23,26,.18)}}
.phone{{width:390px;height:844px;background:#fff;border-radius:48px;overflow:hidden;position:relative}}
.sb{{height:50px;display:flex;justify-content:space-between;align-items:center;padding:8px 30px 0;font-size:15px;font-weight:600}}
.sb .r{{display:flex;gap:6px;align-items:center}}
.notch{{position:absolute;top:10px;left:50%;transform:translateX(-50%);width:120px;height:34px;background:{INK};border-radius:20px;z-index:9}}
.pill{{height:56px;border-radius:999px;background:{S};color:{INK};font-weight:700;font-size:17px;display:flex;align-items:center;justify-content:center;gap:8px}}
.opill{{height:48px;border-radius:999px;background:{F};border:1.5px solid {S};color:{D};font-weight:600;font-size:15px;display:flex;align-items:center;justify-content:center;gap:8px;padding:0 18px}}
.card{{background:#fff;border:1px solid {HL};border-radius:24px;padding:16px;box-shadow:0 6px 20px rgba(23,23,26,.05)}}
.field{{height:52px;border-radius:16px;background:{P};border:1px solid {HL};display:flex;align-items:center;padding:0 16px;color:{MU};font-size:15px;gap:10px}}
.muted{{color:{MU};font-size:14px}}
.tabs{{position:absolute;left:0;right:0;bottom:0;height:92px;background:#fff;border-top:1px solid {HL};display:flex;justify-content:space-around;padding:10px 6px 0}}
.tab{{display:flex;flex-direction:column;align-items:center;gap:5px;font-size:11px;color:{MU};width:72px}}
.tab .ic{{width:56px;height:32px;border-radius:16px;display:flex;align-items:center;justify-content:center}}
.tab.on{{color:{D};font-weight:700}} .tab.on .ic{{background:{F}}}
.home{{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);width:134px;height:5px;border-radius:3px;background:{INK}}}
.food{{position:relative;overflow:hidden}}
.chip{{height:40px;border-radius:999px;display:flex;align-items:center;justify-content:center;font-size:15px;font-weight:600;flex:1}}
"""
def ico(kind,c):
    p={'col':'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
       'disc':'<circle cx="12" cy="12" r="9"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>',
       'ast':'<path d="M4 5h16v11H9l-5 4z"/>',
       'sav':'<path d="M6 3h12v18l-6-4-6 4z"/>',
       'pro':'<circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/>',
       'link':'<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
       'clip':'<rect x="6" y="4" width="12" height="17" rx="2"/><rect x="9" y="2" width="6" height="4" rx="1"/>',
       'back':'<path d="M15 5l-7 7 7 7"/>','x':'<path d="M6 6l12 12M18 6L6 18"/>','next':'<path d="M9 5l7 7-7 7"/>','heart':'<path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>',
       'play':'<path d="M7 5l12 7-12 7z"/>','check':'<path d="M5 12l5 5 9-10"/>'}[kind]
    return f'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="{c}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">{p}</svg>'
def tabs():
    t=[('col','Collections'),('disc','Discover'),('ast','Assistant'),('sav','Saved'),('pro','Profile')]
    return '<div class="tabs">'+''.join(f'<div class="tab {"on" if n=="Discover" else ""}"><div class="ic">{ico(k,D if n=="Discover" else MU)}</div>{n}</div>' for k,n in t)+'</div><div class="home"></div>'
SB=f'<div class="notch"></div><div class="sb"><span>9:41</span><span class="r"><svg width="18" height="12"><rect x="0" y="8" width="3" height="4" fill="{INK}"/><rect x="5" y="5" width="3" height="7" fill="{INK}"/><rect x="10" y="2" width="3" height="10" fill="{INK}"/><rect x="15" y="0" width="3" height="12" fill="{INK}"/></svg><svg width="26" height="12"><rect x=".5" y=".5" width="22" height="11" rx="3" fill="none" stroke="{INK}"/><rect x="2" y="2" width="17" height="8" rx="1.5" fill="{INK}"/></svg></span></div>'
def food(h,kind='dal',r=0,extra=''):
    bg={'dal':'linear-gradient(135deg,#F3D9A4,#E7B566)','curry':'linear-gradient(135deg,#F2C29A,#D9854C)','biry':'linear-gradient(135deg,#F5E2BE,#E2A95C)'}[kind]
    bowl={'dal':('#F7F1E6','#E9A93B','#D98E25'),'curry':('#F7F1E6','#C9652E','#A84E22'),'biry':('#F7F1E6','#EBC27A','#D59A45')}[kind]
    s=h*0.78
    return f'''<div class="food" style="height:{h}px;background:{bg};border-radius:{r}px;{extra}">
<div style="position:absolute;width:{s}px;height:{s}px;border-radius:50%;background:{bowl[0]};left:50%;top:50%;transform:translate(-50%,-50%);box-shadow:0 10px 30px rgba(120,60,10,.25)"></div>
<div style="position:absolute;width:{s*.74}px;height:{s*.74}px;border-radius:50%;background:radial-gradient(circle at 40% 35%,{bowl[1]},{bowl[2]});left:50%;top:50%;transform:translate(-50%,-50%)"></div>
<div style="position:absolute;width:{s*.09}px;height:{s*.09}px;border-radius:50%;background:#6E9B4A;left:46%;top:44%"></div>
<div style="position:absolute;width:{s*.07}px;height:{s*.07}px;border-radius:50%;background:#7FAE55;left:55%;top:52%"></div>
<div style="position:absolute;width:{s*.06}px;height:{s*.06}px;border-radius:50%;background:#FFF6DC;left:43%;top:56%;opacity:.8"></div></div>'''
def page(hdr,ttl,body,cap,name):
    html=f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body><div class="hdr">{hdr}</div><div class="ttl">{ttl}</div>{body}<div><div class="cap">{cap}</div></div></body></html>'
    open(name+'.html','w').write(html)
def phone(inner): return f'<div class="bezel"><div class="phone">{SB}{inner}</div></div>'

# 01
def btnphone(label_color,good):
    ratio='2.6 : 1' if not good else '6.8 : 1'
    return phone(f'''<div style="padding:24px 24px 0">
<div class="muted" style="margin-bottom:6px">Recipe · Step overview</div>
<div style="font-size:26px;font-weight:700;margin-bottom:18px">Dal tadka</div>
{food(250,'dal',24)}
<div style="margin-top:20px;font-size:15px;line-height:1.6;color:{INK}">Yellow lentils tempered with cumin, garlic and dried chilli. 6 steps.</div>
<div style="position:absolute;left:24px;right:24px;bottom:300px">
<div class="pill" style="color:{label_color};font-size:19px">{ico('play',label_color)} Start cooking</div></div>
<div style="position:absolute;left:24px;right:24px;bottom:120px;border:1.5px dashed {HL};border-radius:20px;padding:18px;display:flex;justify-content:space-between;align-items:center">
<div><div class="muted">Label text contrast</div><div style="font-size:24px;font-weight:700;margin-top:4px">{ratio}</div></div>
<div class="tag {'good' if good else 'bad'}">{'PASSES AA' if good else 'FAILS AA'}</div></div></div><div class="home"></div>''')
b=f'''<div class="row">
<div class="col"><div class="tag bad">BEFORE</div>{btnphone('#FFFFFF',False)}<div class="note">SoftSage fill + white label. Text washes out, hard to read in a bright kitchen.</div></div>
<div class="col"><div class="tag good">AFTER · Sai pick A · already shipping</div>{btnphone(INK,True)}<div class="note">SoftSage fill + Ink (#17171A) label. Same soft green, crisp readable text.</div></div></div>'''
page('DesiKitchen · Soft UI proposal 01','PillButton label contrast',b,'Contrast A — <b>Ink on SoftSage</b>','01-pillbutton-contrast')

# Discover chrome
def discover(overlay=''):
    return f'''<div style="padding:14px 22px 0">
<div class="muted">Good evening</div>
<div style="font-size:28px;font-weight:700;margin:2px 0 18px">What's the plan, Sai?</div>
<div class="card">
<div style="font-weight:600;font-size:16px;margin-bottom:12px">Import a recipe</div>
<div class="field">{ico('link',MU)} Paste a YouTube or Instagram link</div>
<div class="pill" style="margin-top:12px;height:52px">Import</div></div>
<div class="opill" style="margin-top:14px">{ico('clip',D)} Paste link from clipboard</div>
<div class="muted" style="text-align:center;margin-top:12px">3 of 5 imports left this week</div>
<div style="display:flex;justify-content:space-between;align-items:baseline;margin:24px 0 12px"><div style="font-size:19px;font-weight:700">Recently saved</div><div style="color:{D};font-weight:600;font-size:14px">See all</div></div>
<div style="display:flex;gap:14px">
<div style="flex:1">{food(150,'dal',20)}<div style="font-weight:600;margin-top:8px;font-size:15px">Dal tadka</div><div class="muted" style="font-size:13px">@ranveerbrar</div></div>
<div style="flex:1">{food(150,'curry',20)}<div style="font-weight:600;margin-top:8px;font-size:15px">Paneer butter masala</div><div class="muted" style="font-size:13px">@hebbarskitchen</div></div></div>
</div>{overlay}{tabs()}'''
b=f'<div class="row"><div class="col">{phone(discover())}</div><div class="col" style="align-items:flex-start;max-width:330px;gap:14px;padding-top:40px">'+''.join(f'<div class="card" style="font-size:15px;line-height:1.5"><b style="color:{D}">{a}</b><br>{c}</div>' for a,c in [('Paste is the hero','One SoftCard at the top: link field + Import. No scrolling to find it.'),('Clipboard shortcut','SoftOutlinePill on SoftSageFill pastes the copied link in one tap.'),('Quota stays quiet','Muted line, not a banner: 3 of 5 imports left this week.'),('Discover tab active','SoftSageFill indicator behind the icon.')])+'</div></div>'
page('DesiKitchen · Soft UI proposal 02','Discover — paste link as hero',b,'Discover: <b>paste-first hero</b> card','02-discover-paste-hero')

# 03
def importpill(state):
    if state==1:
        inner=f'''<div style="display:flex;align-items:center;gap:12px"><div style="width:36px;height:36px;border-radius:50%;background:#fff;display:flex;align-items:center;justify-content:center">{ico('link',D)}</div>
<div style="flex:1"><div style="font-weight:600;font-size:15px">Importing from Instagram…</div>
<div style="height:4px;border-radius:2px;background:#fff;margin-top:8px"><div style="width:58%;height:4px;border-radius:2px;background:{S}"></div></div></div>
<div style="color:{D};font-weight:700;font-size:14px;padding-left:4px">Cancel</div></div>'''
    else:
        inner=f'''<div style="display:flex;align-items:center;gap:12px"><div style="width:36px;height:36px;border-radius:50%;background:{S};display:flex;align-items:center;justify-content:center">{ico('check',INK)}</div>
<div style="flex:1"><div style="font-weight:600;font-size:15px">Recipe ready</div><div class="muted" style="font-size:13px">Dal makhani · from Instagram</div></div>
<div style="background:{S};color:{INK};font-weight:700;font-size:14px;height:36px;padding:0 16px;border-radius:999px;display:flex;align-items:center">Review</div></div>'''
    return f'<div style="position:absolute;left:14px;right:14px;bottom:104px;background:{F};border:1px solid #CFE8D9;border-radius:999px;padding:12px 18px 12px 12px;box-shadow:0 10px 28px rgba(23,23,26,.12)">{inner}</div>'
b=f'''<div class="row">
<div class="col"><div class="tag n">STATE 1 · IMPORTING</div>{phone(discover(importpill(1)))}<div class="note">Floating SoftSageFill pill above tabs. Thin SoftSage progress. Cancel any time. User can keep browsing.</div></div>
<div class="col"><div class="tag good">STATE 2 · DONE</div>{phone(discover(importpill(2)))}<div class="note">Same pill flips to "Recipe ready". Review opens the recipe.</div></div></div>'''
page('DesiKitchen · Soft UI proposal 03','Import status pill — in progress and ready',b,'Import status: <b>floating pill</b>, not a blocking screen','03-import-status-pill')

# 04
ings=[('1 cup','toor dal, rinsed'),('1 medium','onion, finely chopped'),('2','tomatoes, chopped'),('1 tsp','cumin seeds'),('4 cloves','garlic, sliced'),('½ tsp','turmeric'),('2 tbsp','ghee')]
il=''.join(f'<div style="display:flex;gap:14px;padding:13px 0;border-bottom:1px solid {HL};font-size:16px"><div style="width:78px;color:{D};font-weight:600">{q}</div><div>{n}</div></div>' for q,n in ings)
det=f'''<div style="position:absolute;top:0;left:0;right:0">{food(360,'dal',0)}</div>
<div style="position:absolute;top:0;left:0;right:0;z-index:10">{SB}</div>
<div style="position:absolute;top:58px;left:18px;width:42px;height:42px;border-radius:50%;background:rgba(255,255,255,.92);display:flex;align-items:center;justify-content:center;z-index:10">{ico('back',INK)}</div>
<div style="position:absolute;top:58px;right:18px;width:42px;height:42px;border-radius:50%;background:rgba(255,255,255,.92);display:flex;align-items:center;justify-content:center;z-index:10">{ico('heart',INK)}</div>
<div style="position:absolute;top:310px;left:0;right:0;bottom:0;background:#fff;border-radius:29px 29px 0 0;padding:12px 24px 0;box-shadow:0 -6px 20px rgba(23,23,26,.08)">
<div style="width:40px;height:5px;border-radius:3px;background:{HL};margin:0 auto 16px"></div>
<div style="font-size:26px;font-weight:700">Dal tadka</div>
<div class="opill" style="display:inline-flex;height:36px;margin-top:10px;font-size:14px;padding:0 14px"><span style="width:20px;height:20px;border-radius:50%;background:{S};display:inline-block"></span>@ranveerbrar</div>
<div style="display:flex;gap:6px;background:{P};border:1px solid {HL};border-radius:999px;padding:4px;margin:18px 0 6px">
<div class="chip" style="background:{F};color:{D};border:1.5px solid {S}">Ingredients</div><div class="chip" style="color:{MU}">Steps</div></div>
{il}</div>
<div style="position:absolute;left:0;right:0;bottom:0;height:120px;background:linear-gradient(rgba(255,255,255,0),#fff 35%)"></div>
<div style="position:absolute;left:24px;right:24px;bottom:34px" class="pill">{ico('play',INK)} Start cooking</div><div class="home"></div>'''
b=f'<div class="row"><div class="col"><div class="bezel"><div class="phone">{det.replace(SB,SB,1)}</div></div></div><div class="col" style="align-items:flex-start;max-width:330px;gap:14px;padding-top:40px">'+''.join(f'<div class="card" style="font-size:15px;line-height:1.5"><b style="color:{D}">{a}</b><br>{c}</div>' for a,c in [('Full-bleed food photo','Hero fills the top edge to edge.'),('SoftSheet overlaps hero','White sheet with ~29dp top corners slides over the photo.'),('Ingredients | Steps switch','SoftChip toggle; Ingredients selected in SoftSageFill.'),('Plain ingredient lines','No checkboxes, no time, no servings, no nutrition.'),('Sticky Start cooking','SoftSage PillButton with Ink label stays at the bottom.')])+'</div></div>'
page('DesiKitchen · Soft UI proposal 04','Recipe detail — SoftSheet over hero',b,'Detail: <b>SoftSheet</b> over full-bleed hero','04-detail-softsheet')

# 05
segs=''.join(f'<div style="flex:1;height:6px;border-radius:3px;background:{S if i<3 else HL}"></div>' for i in range(6))
cook=f'''<div style="padding:10px 24px 0">
<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px"><div style="width:42px;height:42px;border-radius:50%;border:1px solid {HL};display:flex;align-items:center;justify-content:center">{ico('x',INK)}</div><div style="font-weight:600">Dal tadka</div><div class="muted" style="font-weight:600">3 / 6</div></div>
<div style="display:flex;gap:6px">{segs}</div>
<div style="margin-top:22px">{food(230,'dal',24)}</div>
<div style="display:flex;align-items:center;gap:12px;margin-top:26px"><div style="width:52px;height:52px;border-radius:50%;background:{F};color:{D};font-size:24px;font-weight:800;display:flex;align-items:center;justify-content:center">3</div><div class="muted" style="font-weight:600;letter-spacing:.06em">STEP 3 OF 6</div></div>
<div style="font-size:24px;line-height:1.45;font-weight:500;margin-top:16px">Heat ghee in a small pan. Add cumin seeds and let them crackle, then add the sliced garlic and fry until golden.</div>
</div>
<div style="position:absolute;left:24px;right:24px;bottom:40px;display:flex;gap:12px">
<div class="opill" style="flex:1;height:56px;font-size:17px">{ico('back',D)} Previous</div>
<div class="pill" style="flex:1.4">Next {ico('next',INK)}</div></div><div class="home"></div>'''
b=f'<div class="row"><div class="col">{phone(cook)}</div><div class="col" style="align-items:flex-start;max-width:330px;gap:14px;padding-top:40px">'+''.join(f'<div class="card" style="font-size:15px;line-height:1.5"><b style="color:{D}">{a}</b><br>{c}</div>' for a,c in [('Segment progress','6 SoftSage segments; 3 filled = step 3 of 6.'),('NumberBadge','SoftSageFill circle with SoftSageDeep "3".'),('Big step text','Readable from arm\'s length at the stove.'),('Two clear actions','Previous = SoftOutlinePill. Next = SoftSage PillButton, Ink text.')])+'</div></div>'
page('DesiKitchen · Soft UI proposal 05','Cook mode — one step at a time',b,'Cook mode: <b>3 / 6</b> with Previous · Next','05-cook-mode')
