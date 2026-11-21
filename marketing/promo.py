"""Baut die Play-Store-Bilder (1080×1920) aus marketing/raw/*.png: Überschrift + Handy-Rahmen
auf MoCam-Farben, dazu Vorstellungsgrafik 1024×500 und App-Icon 512×512."""
import asyncio, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW, OUT = os.path.join(ROOT, 'raw'), os.path.join(ROOT, 'store')
os.makedirs(OUT, exist_ok=True)
FB = '/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf'
FM = '/usr/share/fonts/opentype/inter/Inter-Medium.otf'
W, H = 1080, 1920
ACCENT = (255, 213, 79)

SLIDES = [
    ('01_logo', 'Dein Logo in jedem Video', 'Live eingebrannt – in Foto und Video'),
    ('03_bauchbinde', 'Bauchbinden wie im Fernsehen', 'Name und Funktion automatisch eingeblendet'),
    ('04_untertitel', 'Untertitel automatisch', 'Whisper-KI direkt auf dem Handy – offline, mit eigener Wortliste'),
    ('05_ringlicht', 'Dein Display wird zum Ringlicht', 'Hell ausgeleuchtete Selfie-Videos – automatisch geregelt'),
    ('02_projekt', 'Ein Logo = ein Projekt', 'Ordner, Bauchbinde, Copyright, Intro & Outro – alles pro Projekt'),
    ('06_pro', 'Volle Kontrolle im Pro-Modus', 'ISO, Verschlusszeit, Weißabgleich, manueller Fokus & Peaking'),
    ('07_helfer', 'Profi-Hilfen wie bei der Systemkamera', 'Zebra, Histogramm, Wasserwaage, AE/AF-Sperre'),
    ('09_prompter', 'Teleprompter eingebaut', 'Ablesen und trotzdem in die Kamera schauen'),
    ('08_teilen', 'In Sekunden geteilt', 'WhatsApp Status, Instagram, LinkedIn, Pinterest'),
    ('10_opensource', 'Open Source. Kostenlos. Ohne Werbung.', 'Kein Konto, kein Tracking – getragen von Spenden'),
]


def gradient(w, h, a=(30, 30, 34), b=(12, 12, 14)):
    g = Image.new('RGB', (w, h)); d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / h; d.line([(0, y), (w, y)], fill=tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    return g


def wrap(draw, text, font, maxw):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if draw.textlength(t, font=font) <= maxw: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur); return lines


def deco(img, w, h, big=True):
    layer = Image.new('RGBA', (w, h), (0, 0, 0, 0)); dd = ImageDraw.Draw(layer)
    if big:
        dd.ellipse([w - 420, -260, w + 320, 480], fill=ACCENT + (34,))
        dd.ellipse([-360, h - 520, 360, h + 200], fill=ACCENT + (18,))
    else:
        dd.ellipse([w - 380, -300, w + 200, 300], fill=ACCENT + (30,))
    return Image.alpha_composite(img.convert('RGBA'), layer.filter(ImageFilter.GaussianBlur(40)))


def slide(raw, title, sub):
    img = deco(gradient(W, H), W, H)
    d = ImageDraw.Draw(img)
    fb, fm = ImageFont.truetype(FB, 74), ImageFont.truetype(FM, 38)
    y = 110
    for line in wrap(d, title, fb, W - 140):
        d.text((W / 2, y), line, font=fb, fill='white', anchor='ma'); y += 88
    y += 14
    for line in wrap(d, sub, fm, W - 180):
        d.text((W / 2, y), line, font=fm, fill=ACCENT, anchor='ma'); y += 50
    shot = Image.open(raw).convert('RGB')
    top = max(y + 50, 420)
    ph = H - top + 140
    sw = int(ph * 1080 / 1920 * 0.97)
    sh_img = shot.resize((sw - 36, int((sw - 36) * shot.height / shot.width)), Image.LANCZOS)
    pw, phh = sw, sh_img.height + 36
    x = (W - pw) // 2
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle([x + 10, top + 24, x + pw + 10, top + phh + 24], radius=70, fill=(0, 0, 0, 150))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(28)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x, top, x + pw, top + phh], radius=70, fill=(8, 8, 8), outline=(70, 70, 74), width=3)
    mask = Image.new('L', sh_img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sh_img.width - 1, sh_img.height - 1], radius=54, fill=255)
    img.paste(sh_img, (x + 18, top + 18), mask)
    return img.convert('RGB')


ICON_SVG = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{s}" height="{s}">
<rect width="108" height="108" fill="#1B1B1B"/>
<path fill="#FFD54F" d="M34,42h10l4,-6h12l4,6h10a4,4 0,0 1,4 4v24a4,4 0,0 1,-4 4H34a4,4 0,0 1,-4 -4V46a4,4 0,0 1,4 -4z"/>
<path fill="#1B1B1B" d="M54,47a11,11 0,1 1,0 22a11,11 0,1 1,0 -22z"/>
<path fill="#FFD54F" d="M54,52a6,6 0,1 1,0 12a6,6 0,1 1,0 -12z"/></svg>'''


async def render_icon(size, path):
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome')
        pg = await b.new_page(viewport={'width': size, 'height': size})
        await pg.set_content('<html><body style="margin:0">' + ICON_SVG.format(s=size) + '</body></html>')
        await pg.screenshot(path=path)
        await b.close()


def feature_graphic(icon_path):
    W2, H2 = 1024, 500
    img = deco(gradient(W2, H2, (34, 34, 38), (14, 14, 16)), W2, H2, big=False)
    d = ImageDraw.Draw(img)
    ic = Image.open(icon_path).convert('RGB').resize((104, 104), Image.LANCZOS)
    m = Image.new('L', ic.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, 103, 103], radius=26, fill=255)
    img.paste(ic, (60, 104), m)
    d.text((184, 112), 'MoCam', font=ImageFont.truetype(FB, 66), fill='white')
    fm = ImageFont.truetype(FM, 31)
    y = 252
    for line in ['Dein Logo. Dein Video.', 'Deine Marke – mit einem Klick.']:
        d.text((64, y), line, font=fm, fill=ACCENT); y += 42
    d.text((64, y + 20), 'Open Source · ohne Werbung · ohne Tracking', font=ImageFont.truetype(FM, 22), fill=(200, 200, 205))
    shot = Image.open(os.path.join(RAW, '01_logo.png')).convert('RGB')
    sw = 300; s = shot.resize((sw, int(sw * shot.height / shot.width)), Image.LANCZOS).crop((0, 0, sw, 470))
    x, top = 668, 56
    d.rounded_rectangle([x - 12, top - 12, x + sw + 12, H2 + 40], radius=44, fill=(8, 8, 8), outline=(70, 70, 74), width=2)
    mk = Image.new('L', s.size, 0); ImageDraw.Draw(mk).rounded_rectangle([0, 0, s.width - 1, s.height + 60], radius=34, fill=255)
    img.paste(s, (x, top), mk)
    img.convert('RGB').save(os.path.join(OUT, 'feature_graphic_1024x500.png'), optimize=True)


if __name__ == '__main__':
    for i, (name, title, sub) in enumerate(SLIDES, 1):
        p = os.path.join(RAW, name + '.png')
        if not os.path.exists(p): print('fehlt:', name); continue
        slide(p, title, sub).save(os.path.join(OUT, f'{i:02d}_{name[3:]}.png'), optimize=True)
    icon = os.path.join(OUT, 'app_icon_512.png')
    asyncio.run(render_icon(512, icon))
    feature_graphic(icon)
    print('Store-Bilder in', OUT)
