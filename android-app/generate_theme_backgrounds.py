#!/usr/bin/env python3
"""Generate original offline dark-fantasy EDA wallpapers (no stock photos/network)."""
from __future__ import annotations
import math, random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
W, H = 420, 900

# id, accent RGB, secondary RGB, motif
THEMES = [
 ("dark_queen",(255,35,75),(255,150,180),"queen"),
 ("hellhound",(255,55,30),(255,155,85),"hellhound"),
 ("obsidian",(210,220,235),(255,55,75),"obsidian"),
 ("pentagram",(255,25,85),(255,125,160),"pentagram"),
 ("wolf",(220,225,235),(255,55,65),"wolf"),
 ("eclipse",(255,65,55),(255,170,120),"eclipse"),
 ("throne",(255,40,40),(255,145,145),"throne"),
 ("black_sun",(255,90,35),(255,190,100),"sun"),
 ("dark_angel",(255,45,75),(225,225,240),"angel"),
 ("dragon_core",(255,40,25),(255,150,65),"dragon"),
 ("fallen_angel",(255,50,85),(215,215,230),"fallen"),
 ("night_queen",(255,45,110),(255,170,205),"nightqueen"),
 ("space_gate",(255,45,90),(155,200,255),"gate"),
 ("neon_abyss",(255,40,95),(100,245,215),"abyss"),
]

def polygon_star(draw,cx,cy,r1,r2,n,fill,width=2,rotation=-math.pi/2):
    pts=[]
    for i in range(n*2):
        a=rotation+i*math.pi/n; r=r1 if i%2==0 else r2
        pts.append((int(cx+math.cos(a)*r),int(cy+math.sin(a)*r)))
    draw.line(pts+[pts[0]],fill=fill,width=width,joint="curve")

def build(theme):
    tid, accent, secondary, motif = theme
    random.seed(tid)
    # Rich bespoke procedural art: no downloaded photography and no generic landscapes.
    im=Image.new("RGB",(W,H),(3,2,7)); px=im.load()
    glow={"queen":(57,3,22),"hellhound":(65,5,4),"obsidian":(13,18,27),
          "pentagram":(55,2,23),"wolf":(12,17,28),"eclipse":(48,5,10),
          "throne":(58,2,5),"sun":(55,13,2),"angel":(35,5,17),
          "dragon":(60,2,2),"fallen":(32,4,15),"nightqueen":(55,3,25),
          "gate":(8,9,35),"abyss":(2,28,25)}[motif]
    for y in range(H):
        for x in range(W):
            dx=(x-W*.5)/(W*.8); dy=(y-H*.34)/(H*.66)
            g=max(0,1-math.sqrt(dx*dx+dy*dy))
            wave=(math.sin(x*.025+y*.013)+1)*.5
            px[x,y]=tuple(min(255,int((3,2,7)[i]+glow[i]*g*g*.9+accent[i]*wave*g*.045)) for i in range(3))
    im=im.convert("RGBA")
    # Nebula haze and fine stars, never realistic trees/mountains/photos.
    haze=Image.new("RGBA",(W,H),(0,0,0,0)); hd=ImageDraw.Draw(haze)
    for _ in range(34):
        x=random.randint(-40,W+40); y=random.randint(30,H-80); r=random.randint(12,85)
        hd.ellipse((x-r,y-r,x+r,y+r),fill=(*accent,random.randint(2,11)))
    haze=haze.filter(ImageFilter.GaussianBlur(28)); im=Image.alpha_composite(im,haze)
    d=ImageDraw.Draw(im)
    for _ in range(210):
        x=random.randrange(W); y=random.randrange(H); r=random.choices([1,2,3],[.78,.19,.03])[0]
        c=(*secondary,random.randint(35,150))
        d.ellipse((x-r,y-r,x+r,y+r),fill=c)
    cx,cy=W//2,int(H*.36); red=(*accent,235); pale=(*secondary,205); dim=(*accent,70)
    # Ornamental circles / occult geometry as a backdrop, not a sticker on a photo.
    for r,w in [(145,2),(132,1),(118,1)]:
        d.ellipse((cx-r,cy-r,cx+r,cy+r),fill=dim,width=w)
    for i in range(36):
        a=i*math.tau/36
        d.line((cx+math.cos(a)*135,cy+math.sin(a)*135,cx+math.cos(a)*(145 if i%3==0 else 140),cy+math.sin(a)*(145 if i%3==0 else 140)),fill=red if i%3==0 else dim,width=2)
    if motif in ("queen","nightqueen"):
        # Elaborate floating crown
        pts=[(cx-92,cy+25),(cx-82,cy-48),(cx-40,cy-8),(cx-15,cy-78),(cx+15,cy-78),(cx+40,cy-8),(cx+82,cy-48),(cx+92,cy+25)]
        d.line(pts,fill=red,width=5,joint="curve"); d.line((cx-92,cy+25,cx-75,cy+57,cx+75,cy+57,cx+92,cy+25),fill=pale,width=3)
        for x,y in pts[1::2]: d.ellipse((x-6,y-6,x+6,y+6),fill=pale)
        if motif=="nightqueen": d.arc((cx-110,cy-120,cx+110,cy+100),190,350,fill=pale,width=3)
    elif motif=="hellhound":
        # Infernal hound head with horns and burning eyes
        d.polygon([(cx-78,cy-38),(cx-112,cy-105),(cx-45,cy-73),(cx-15,cy-45),(cx+15,cy-45),(cx+45,cy-73),(cx+112,cy-105),(cx+78,cy-38),(cx+62,cy+48),(cx,cy+102),(cx-62,cy+48)],fill=red,width=4)
        d.line((cx-55,cy-12,cx-20,cy+2,cx-45,cy+13),fill=pale,width=4); d.line((cx+55,cy-12,cx+20,cy+2,cx+45,cy+13),fill=pale,width=4)
        d.polygon([(cx-13,cy+36),(cx+13,cy+36),(cx,cy+53)],fill=red)
        for j in range(5): d.arc((cx-130-j*4,cy+35+j*7,cx-20,cy+130+j*5),200,320,fill=(*accent,65),width=2)
    elif motif=="obsidian":
        d.polygon([(cx,cy-112),(cx+62,cy-35),(cx+42,cy+72),(cx,cy+110),(cx-42,cy+72),(cx-62,cy-35)],fill=pale,width=4)
        d.line((cx,cy-112,cx,cy+110),fill=red,width=2); d.line((cx-62,cy-35,cx+42,cy+72),fill=dim,width=2); d.line((cx+62,cy-35,cx-42,cy+72),fill=dim,width=2)
    elif motif=="pentagram":
        polygon_star(d,cx,cy,104,42,5,red,4)
        d.ellipse((cx-12,cy-12,cx+12,cy+12),fill=pale,width=2)
    elif motif=="wolf":
        d.arc((cx-115,cy-115,cx+115,cy+115),205,335,fill=pale,width=3)
        d.polygon([(cx-72,cy-30),(cx-63,cy-100),(cx-20,cy-62),(cx+20,cy-62),(cx+63,cy-100),(cx+72,cy-30),(cx+52,cy+46),(cx,cy+93),(cx-52,cy+46)],fill=red,width=4)
        d.line((cx-52,cy-5,cx-20,cy+5),fill=pale,width=4); d.line((cx+52,cy-5,cx+20,cy+5),fill=pale,width=4)
    elif motif in ("eclipse","sun"):
        d.ellipse((cx-72,cy-72,cx+72,cy+72),fill=red,width=5)
        d.ellipse((cx-53,cy-53,cx+53,cy+53),fill=pale,width=2)
        if motif=="eclipse": d.ellipse((cx-32,cy-62,cx+78,cy+48),fill=(3,2,7,255))
        else:
            for i in range(24):
                a=i*math.tau/24; d.line((cx+math.cos(a)*83,cy+math.sin(a)*83,cx+math.cos(a)*103,cy+math.sin(a)*103),fill=red,width=3)
    elif motif=="throne":
        d.polygon([(cx-88,cy+90),(cx-88,cy-72),(cx-48,cy-38),(cx,cy-112),(cx+48,cy-38),(cx+88,cy-72),(cx+88,cy+90)],fill=red,width=5)
        d.line((cx-105,cy+90,cx+105,cy+90),fill=pale,width=5); d.rectangle((cx-55,cy+10,cx+55,cy+70),fill=dim,width=3)
    elif motif in ("angel","fallen"):
        # Feathered wings around a central sigil
        for side in (-1,1):
            for j in range(8):
                yy=cy-75+j*20
                x0=cx+side*12; x1=cx+side*(48+j*8); y1=yy+(-1 if motif=="angel" else 1)*(20+j*2)
                d.line((x0,yy,x1,y1),fill=red if j%2==0 else pale,width=3)
                d.arc((min(x0,x1)-8,min(yy,y1)-9,max(x0,x1)+8,max(yy,y1)+9),190 if side<0 else 0,290 if side<0 else 100,fill=dim,width=2)
        d.line((cx,cy-45,cx,cy+72),fill=pale,width=3); polygon_star(d,cx,cy,30,12,6,red,2)
    elif motif=="dragon":
        # Coiled dragon / horned silhouette
        d.arc((cx-105,cy-110,cx+105,cy+105),35,310,fill=red,width=6)
        d.polygon([(cx+15,cy-75),(cx+70,cy-112),(cx+56,cy-56),(cx+100,cy-30),(cx+40,cy-20),(cx+10,cy+15)],fill=pale,width=4)
        d.line((cx-62,cy+44,cx-20,cy+70,cx+25,cy+55),fill=red,width=4)
        for j in range(4): d.line((cx-85+j*12,cy+50+j*5,cx-105+j*12,cy+80+j*5),fill=dim,width=2)
    elif motif=="gate":
        for r in (105,85,65):
            d.arc((cx-r,cy-r,cx+r,cy+r),0,360,fill=red if r==85 else pale,width=3)
        d.polygon([(cx,cy-65),(cx+42,cy),(cx,cy+65),(cx-42,cy)],fill=red,width=3)
        for i in range(9):
            y=cy-55+i*14; d.line((cx-25,y,cx+25,y),fill=dim,width=2)
    elif motif=="abyss":
        polygon_star(d,cx,cy,95,70,8,red,3)
        d.ellipse((cx-42,cy-42,cx+42,cy+42),fill=pale,width=3)
        for i in range(16):
            a=i*math.tau/16
            d.line((cx+math.cos(a)*112,cy+math.sin(a)*112,cx+math.cos(a)*128,cy+math.sin(a)*128),fill=red,width=2)
    # Gothic spires and arches frame the composition; no landscape imagery.
    arch=Image.new("RGBA",(W,H),(0,0,0,0)); ad=ImageDraw.Draw(arch)
    for side in (0,W):
        x=side
        ad.polygon([(x-35,H),(x+22,H),(x+18,H*.53),(x+2,H*.44),(x-15,H*.53)],fill=(0,0,0,105))
        ad.line((x+3,H*.57,x+2,H*.22,x+22,H*.12,x+38,H*.22,x+38,H*.57),fill=(*accent,48),width=2)
    ad.arc((W*.10,H*.08,W*.90,H*.70),190,350,fill=(*accent,45),width=2)
    im=Image.alpha_composite(im,arch)
    # Strong bottom/edge readability gradient for UI overlays.
    shade=Image.new("RGBA",(W,H),(0,0,0,0)); sd=ImageDraw.Draw(shade)
    for y in range(H):
        a=int(175*(y/H)**2)
        sd.line((0,y,W,y),fill=(0,0,0,a))
    for x in range(W//2):
        a=int(95*(1-x/(W/2)))
        sd.line((x,0,x,H),fill=(0,0,0,a)); sd.line((W-1-x,0,W-1-x,H),fill=(0,0,0,a))
    im=Image.alpha_composite(im,shade).convert("RGB")
    OUT.mkdir(parents=True,exist_ok=True)
    target=OUT/f"bg_theme_{tid}.webp"; im.save(target,"WEBP",quality=91,method=6)
    # Thumbnails are purposefully artwork crops, not separately composed stock photos.
    icon=im.crop((W//2-145,int(H*.36)-145,W//2+145,int(H*.36)+145))
    icon.save(OUT/f"ic_theme_{tid}.webp","WEBP",quality=92,method=6)
    print(f"{target.name}: {target.stat().st_size//1024} KiB")

def main():
    for theme in THEMES: build(theme)
    print(f"Generated {len(THEMES)} original offline fantasy themes")
if __name__=="__main__": main()
