from PIL import Image
import numpy as np
src=Image.open('brand/desikitchen-logo.png').convert('L')
a=np.array(src).astype(np.float32)
# alpha: ink coverage (bg ~254 -> 0, black -> 255)
alpha=np.clip((250-a)/(250-20),0,1)*255
alpha=alpha.astype(np.uint8)
def crop(x0,x1):
    sub=alpha[:,x0:x1+1]
    ys=np.where((sub>40).any(1))[0]
    return sub[ys.min():ys.max()+1]
INK=(0x17,0x17,0x1A); SAGE=(0x5B,0xAF,0x8A); WHITE=(255,255,255)
def glyph(mask,color):
    h,w=mask.shape
    im=Image.new('RGBA',(w,h),color+(0,))
    im.putalpha(Image.fromarray(mask))
    return im
D=crop(129,391)
Dw=glyph(D,WHITE); Dk=glyph(D,INK)
def place(g,size,frac,bg=None):
    c=Image.new('RGBA',(size,size),(bg+(255,)) if bg else (0,0,0,0))
    gw,gh=g.size; s=frac*size/max(gw,gh)
    g2=g.resize((round(gw*s),round(gh*s)),Image.LANCZOS)
    c.alpha_composite(g2,((size-g2.width)//2,(size-g2.height)//2))
    return c
# adaptive foreground 432px (108dp@xxxhdpi); keep D within ~44% (safe zone is 66/108=61%)
fg=place(Dw,432,0.44); fg.save('mocks/icon/d-fork-foreground-white.png')
fg.save('app/src/main/res/drawable-nodpi/ic_launcher_foreground.png')
place(Dk,512,0.70).save('mocks/icon/d-fork-ink-transparent.png')
place(Dk,512,0.62,WHITE).save('mocks/icon/d-fork-ink-on-white.png')
splash=place(Dk,288,0.62); splash.save('app/src/main/res/drawable-nodpi/splash_d_mark.png')
# preview: white D on sage, circle + squircle masks
prev=place(Dw,512,0.52,SAGE); prev.save('mocks/icon/d-fork-white-on-sage-512.png')
m=Image.new('L',(512,512),0)
from PIL import ImageDraw
ImageDraw.Draw(m).ellipse((0,0,511,511),fill=255)
circ=prev.copy(); circ.putalpha(m); circ.save('mocks/icon/d-fork-preview-circle.png')
# full wordmark, transparent ink, trimmed
ys=np.where((alpha>40).any(1))[0]; xs=np.where((alpha>40).any(0))[0]
W=alpha[ys.min()-8:ys.max()+9, xs.min()-8:xs.max()+9]
wm=glyph(W,INK); wm=wm.resize((1200,round(W.shape[0]*1200/W.shape[1])),Image.LANCZOS)
wm.save('app/src/main/res/drawable-nodpi/desikitchen_logo.png'); wm.save('mocks/icon/desikitchen-logo-ink-transparent.png')
print(D.shape, wm.size)
