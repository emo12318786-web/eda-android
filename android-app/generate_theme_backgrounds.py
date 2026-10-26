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
 ("dark_queen",(255,59,48),(255,151,122),"queen"),
 ("hellhound",(255,50,31),(255,139,72),"hellhound"),
 ("obsidian",(190,202,215),(255,65,65),"obsidian"),
 ("pentagram",(255,36,74),(255,130,151),"pentagram"),
 ("wolf",(190,202,214),(255,65,65),"wolf"),
 ("eclipse",(255,63,47),(255,160,120),"eclipse"),
 ("throne",(255,41,41),(255,144,144),"throne"),
 ("black_sun",(255,75,32),(255,184,107),"sun"),
 ("dark_angel",(255,45,54),(255,129,136),"angel"),
 ("dragon_core",(255,46,31),(255,154,81),"dragon"),
 ("fallen_angel",(224,214,214),(255,78,89),"fallen"),
 ("night_queen",(255,52,84),(255,160,176),"nightqueen"),
 ("space_gate",(193,199,208),(255,65,65),"gate"),
 ("neon_abyss",(255,57,57),(255,139,139),"abyss"),
]

def polygon_star(draw,cx,cy,r1,r2,n,fill,width=2,rotation=-math.pi/2):
    pts=[]
    for i in range(n*2):
        a=rotation+i*math.pi/n; r=r1 if i%2==0 else r2
        pts.append((int(cx+math.cos(a)*r),int(cy+math.sin(a)*r)))
    draw.line(pts+[pts[0]],fill=fill,width=width,joint="curve")

def draw_character(d, cx, cy, accent, secondary, motif):
    """Original offline character art: faces, armour, hoods, horns and wings."""
    ink=(2,2,5,255); deep=(7,3,8,255); face=(15,8,12,255)
    edge=(*accent,210); light=(*secondary,235); red=(*accent,245)

    # Strong cloak/armour silhouette extends below the emblem.
    d.polygon([(cx-18,cy+72),(cx-92,cy+104),(cx-142,cy+214),
               (cx-135,cy+330),(cx+135,cy+330),(cx+142,cy+214),
               (cx+92,cy+104),(cx+18,cy+72)],fill=ink,outline=(*accent,100))
    d.polygon([(cx-52,cy+90),(cx-83,cy+145),(cx-45,cy+218),
               (cx,cy+240),(cx+45,cy+218),(cx+83,cy+145),(cx+52,cy+90)],
              fill=deep,outline=(*secondary,90))
    for side in (-1,1):
        d.line((cx+side*32,cy+116,cx+side*73,cy+172,cx+side*44,cy+231),
               fill=(*accent,110),width=3)
        d.line((cx+side*48,cy+127,cx+side*93,cy+202),
               fill=(*secondary,85),width=2)

    animal = motif in ("hellhound","wolf","dragon","abyss")
    hooded = motif in ("pentagram","eclipse","angel","fallen","gate","sun")
    if animal:
        if motif=="dragon":
            d.polygon([(cx-61,cy-42),(cx-120,cy-137),(cx-45,cy-100),
                       (cx-9,cy-63),(cx+36,cy-93),(cx+102,cy-123),
                       (cx+71,cy-43),(cx+81,cy+35),(cx+31,cy+104),
                       (cx-38,cy+113),(cx-85,cy+38)],fill=ink,outline=edge)
            d.polygon([(cx-72,cy-37),(cx-114,cy-140),(cx-43,cy-79)],
                      fill=deep,outline=red)
            d.polygon([(cx+48,cy-58),(cx+118,cy-145),(cx+88,cy-48)],
                      fill=deep,outline=red)
        else:
            d.polygon([(cx-51,cy-40),(cx-108,cy-139),(cx-40,cy-93),
                       (cx-16,cy-57),(cx+16,cy-57),(cx+40,cy-93),
                       (cx+108,cy-139),(cx+51,cy-40),(cx+75,cy+49),
                       (cx+36,cy+113),(cx,cy+132),(cx-36,cy+113),
                       (cx-75,cy+49)],fill=ink,outline=edge)
            d.polygon([(cx-74,cy-44),(cx-100,cy-128),(cx-47,cy-81)],
                      fill=deep,outline=(*secondary,155))
            d.polygon([(cx+74,cy-44),(cx+100,cy-128),(cx+47,cy-81)],
                      fill=deep,outline=(*secondary,155))
        # Muzzle, nose and cheek-fur contour.
        d.polygon([(cx-43,cy+17),(cx-30,cy+63),(cx,cy+91),
                   (cx+30,cy+63),(cx+43,cy+17),(cx+20,cy-3),(cx-20,cy-3)],
                  fill=face,outline=(*accent,140))
        d.polygon([(cx-15,cy+57),(cx,cy+50),(cx+15,cy+57),
                   (cx+8,cy+69),(cx-8,cy+69)],fill=red)
        d.line((cx-27,cy+78,cx-10,cy+98,cx,cy+91,cx+10,cy+98,cx+27,cy+78),
               fill=light,width=3)
        for side in (-1,1):
            for j in range(5):
                yy=cy+35+j*12
                d.line((cx+side*43,yy,cx+side*(60+j*2),yy+5),
                       fill=(*secondary,75),width=2)
    else:
        if hooded:
            d.polygon([(cx,cy-150),(cx-75,cy-112),(cx-108,cy-20),
                       (cx-94,cy+80),(cx-55,cy+154),(cx+55,cy+154),
                       (cx+94,cy+80),(cx+108,cy-20),(cx+75,cy-112)],
                      fill=ink,outline=edge)
            d.polygon([(cx,cy-140),(cx-57,cy-91),(cx-69,cy+6),
                       (cx-44,cy+103),(cx,cy+135),(cx+44,cy+103),
                       (cx+69,cy+6),(cx+57,cy-91)],
                      fill=face,outline=(*secondary,110))
        elif motif=="obsidian":
            d.polygon([(cx-69,cy-72),(cx-41,cy-112),(cx+41,cy-112),
                       (cx+69,cy-72),(cx+62,cy+58),(cx,cy+125),(cx-62,cy+58)],
                      fill=ink,outline=light)
            d.polygon([(cx-48,cy-53),(cx,cy-81),(cx+48,cy-53),
                       (cx+42,cy+48),(cx,cy+91),(cx-42,cy+48)],
                      fill=deep,outline=edge)
        else:
            d.polygon([(cx-84,cy-72),(cx-69,cy-131),(cx-25,cy-107),
                       (cx,cy-148),(cx+25,cy-107),(cx+69,cy-131),
                       (cx+84,cy-72),(cx+68,cy+75),(cx+40,cy+127),
                       (cx-40,cy+127),(cx-68,cy+75)],
                      fill=ink,outline=edge)
        d.polygon([(cx-45,cy-46),(cx-29,cy-68),(cx+29,cy-68),(cx+45,cy-46),
                   (cx+39,cy+54),(cx+18,cy+98),(cx,cy+111),
                   (cx-18,cy+98),(cx-39,cy+54)],fill=face,
                  outline=(*secondary,120))
        d.polygon([(cx-43,cy-24),(cx-17,cy-30),(cx-25,cy-10),(cx-42,cy-7)],fill=red)
        d.polygon([(cx+43,cy-24),(cx+17,cy-30),(cx+25,cy-10),(cx+42,cy-7)],fill=red)
        d.line((cx-41,cy-37,cx-15,cy-45,cx-5,cy-33),fill=edge,width=4)
        d.line((cx+41,cy-37,cx+15,cy-45,cx+5,cy-33),fill=edge,width=4)
        d.line((cx,cy-11,cx-8,cy+32,cx+4,cy+39),fill=(*secondary,145),width=3)
        d.line((cx-24,cy+67,cx-8,cy+76,cx+8,cy+76,cx+24,cy+67),
               fill=(*accent,180),width=3)
        # Long strands/hood folds give the face a portrait silhouette at phone size.
        for side in (-1,1):
            d.polygon([(cx+side*54,cy-91),(cx+side*83,cy-53),
                       (cx+side*95,cy+60),(cx+side*72,cy+160),
                       (cx+side*58,cy+86)],fill=deep,outline=(*accent,90))
            for j in range(5):
                x0=cx+side*(54+j*6)
                d.line((x0,cy-63+j*5,x0+side*(12+j*2),cy+45+j*7),
                       fill=(*secondary,55+j*8),width=2)
        if motif in ("queen","nightqueen","throne"):
            pts=[(cx-62,cy-82),(cx-52,cy-129),(cx-20,cy-100),
                 (cx,cy-157),(cx+20,cy-100),(cx+52,cy-129),(cx+62,cy-82)]
            d.line(pts,fill=light,width=4,joint="curve")
            d.line((cx-62,cy-82,cx-48,cy-63,cx+48,cy-63,cx+62,cy-82),
                   fill=edge,width=4)
            for x in (cx-52,cx,cx+52):
                d.ellipse((x-4,cy-133,x+4,cy-125),fill=light)
        if motif=="obsidian":
            d.line((cx-37,cy+1,cx,cy+22,cx+37,cy+1),fill=light,width=3)
            d.line((cx,cy-68,cx,cy+90),fill=(*secondary,130),width=2)

    # Angel and fallen angel get broad feathered wings and a unique halo.
    if motif in ("angel","fallen"):
        for side in (-1,1):
            for j in range(8):
                x0=cx+side*75; y0=cy-62+j*19
                x1=cx+side*(135+j*5)
                y1=y0+(j*3 if motif=="fallen" else -j*2)
                d.line((x0,y0,x1,y1),fill=(*secondary,125 if j%2 else 180),width=3)
                d.arc((min(x0,x1)-8,min(y0,y1)-9,max(x0,x1)+8,max(y0,y1)+9),
                      185 if side<0 else 0,285 if side<0 else 100,
                      fill=(*accent,95),width=2)
        d.arc((cx-68,cy-173,cx+68,cy-37),195 if motif=="fallen" else 185,
              345 if motif=="fallen" else 355,fill=light,width=4)
        if motif=="fallen":
            d.line((cx-59,cy-145,cx-22,cy-112),fill=(*accent,160),width=3)
            d.line((cx+30,cy-114,cx+60,cy-150),fill=(*accent,160),width=3)

    # Slit eyes, jaw and small armour details distinguish each mask.
    if animal:
        d.polygon([(cx-43,cy-18),(cx-16,cy-29),(cx-10,cy-14),(cx-31,cy-7)],fill=light)
        d.polygon([(cx+43,cy-18),(cx+16,cy-29),(cx+10,cy-14),(cx+31,cy-7)],fill=light)
        d.line((cx-12,cy-4,cx,cy+12,cx+12,cy-4),fill=(*accent,220),width=3)
    elif motif=="pentagram":
        d.polygon([(cx-47,cy-24),(cx-15,cy-29),(cx-22,cy-9),(cx-43,cy-6)],fill=light)
        d.polygon([(cx+47,cy-24),(cx+15,cy-29),(cx+22,cy-9),(cx+43,cy-6)],fill=light)
    elif motif=="abyss":
        d.ellipse((cx-48,cy-26,cx-12,cy+9),fill=ink,outline=edge,width=2)
        d.ellipse((cx+12,cy-26,cx+48,cy+9),fill=ink,outline=edge,width=2)
        for tooth in range(7):
            tx=cx-24+tooth*8
            d.rectangle((tx,cy+82,tx+4,cy+102),fill=light)
    for side in (-1,1):
        d.line((cx+side*20,cy+129,cx+side*62,cy+162,cx+side*81,cy+218),
               fill=(*accent,125),width=3)
        d.ellipse((cx+side*63-4,cy+179-4,cx+side*63+4,cy+179+4),fill=light)
    if motif=="dragon":
        for row in range(4):
            for col in range(5):
                x=cx-45+col*22+(row%2)*8
                y=cy+120+row*16
                d.arc((x,y,x+16,y+18),0,180,fill=(*secondary,115),width=2)


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
    # Compose a distinct character portrait over the theme emblem.
    draw_character(d, cx, cy, accent, secondary, motif)
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
