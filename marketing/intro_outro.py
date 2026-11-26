"""Hook-Intro (3 s) und Outro (7 s) für MoCam-Videos, 1080×1920, 30 fps, mit Ton.
Outro: Logo, QR-Code zur neuesten APK (GitHub Release „latest“) und Spenden-Hinweis."""
import math, os, subprocess, sys, wave
import numpy as np
import qrcode
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, 'video'); os.makedirs(OUT, exist_ok=True)
W, H, FPS = 1080, 1920, 30
FB = '/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf'
FM = '/usr/share/fonts/opentype/inter/Inter-Medium.otf'
FS = '/usr/share/fonts/opentype/inter/Inter-SemiBold.otf'
ACC = (255, 213, 79)
APK_URL = 'https://github.com/MoManufaktur/MoCam/releases/latest/download/MoCam.apk'
SR = 44100

LOGO = Image.open(os.path.join(ROOT, 'logo', 'MoCam-Logo-weiss.png')).convert('RGBA')
ICON_W = int(LOGO.height * 1.0)  # Icon ist quadratisch links im Logo
ICON = LOGO.crop((0, 0, ICON_W, LOGO.height))
WORD = LOGO.crop((ICON_W + 1, 0, LOGO.width, LOGO.height))


def ease(t): t = max(0., min(1., t)); return t * t * (3 - 2 * t)
def ease_out(t): t = max(0., min(1., t)); return 1 - (1 - t) ** 3
def back(t):
    t = max(0., min(1., t)); c1, c3 = 1.70158, 2.70158; t -= 1
    return 1 + c3 * t ** 3 + c1 * t ** 2


def background(glow=1.0, cx=W / 2, cy=H * 0.42):
    img = Image.new('RGB', (W, H), (14, 14, 16))
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(layer)
    r = 620
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACC + (int(46 * glow),))
    layer = layer.filter(ImageFilter.GaussianBlur(120))
    return Image.alpha_composite(img.convert('RGBA'), layer)


BG_INTRO = background()
BG_OUTRO = background(0.8, cy=H * 0.30)


def paste_scaled(base, im, cx, cy, scale, alpha=1.0):
    if scale <= 0.01 or alpha <= 0.01: return
    w, h = int(im.width * scale), int(im.height * scale)
    s = im.resize((w, h), Image.LANCZOS)
    if alpha < 1: s.putalpha(s.getchannel('A').point(lambda v: int(v * alpha)))
    base.alpha_composite(s, (int(cx - w / 2), int(cy - h / 2)))


def text(base, xy, s, font, fill, anchor='ma', alpha=1.0):
    layer = Image.new('RGBA', base.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, s, font=font, fill=fill[:3] + (int(255 * alpha),), anchor=anchor)
    base.alpha_composite(layer)


# ---------------- Intro (3,0 s) ----------------
INTRO_DUR = 3.0


def intro_frame(t):
    img = BG_INTRO.copy()
    d = ImageDraw.Draw(img)
    # 0.0–0.35: Lichtstreifen rauscht diagonal durchs Bild
    if t < 0.45:
        p = ease_out(t / 0.45)
        x = -600 + (W + 1200) * p
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
        ld.polygon([(x, 0), (x + 160, 0), (x - 340, H), (x - 500, H)], fill=ACC + (200,))
        img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(18)))
    # 0.25–0.75: Icon springt auf (federnd), Auslöse-Blitz
    p = back((t - 0.25) / 0.5)
    icon_scale = (380 / ICON.height) * p if t > 0.25 else 0
    cy = H * 0.40
    # leichter Zoom-Push über die ganze Zeit
    push = 1 + 0.04 * ease(t / INTRO_DUR)
    paste_scaled(img, ICON, W / 2, cy, icon_scale * push)
    if 0.62 < t < 0.95:  # Blitz
        f = math.sin((t - 0.62) / 0.33 * math.pi)
        flash = Image.new('RGBA', (W, H), (255, 255, 255, int(170 * f)))
        img.alpha_composite(flash)
    # 0.9–1.5: Schriftzug MoCam fährt von unten ein
    q = ease_out((t - 0.9) / 0.55)
    if t > 0.9:
        paste_scaled(img, WORD, W / 2, cy + 300 + 60 * (1 - q), (760 / WORD.width) * push, q)
    # 1.4–2.0: Claim
    for i, (s, start) in enumerate([('Dein Logo.', 1.35), ('Dein Video.', 1.55), ('Deine Marke.', 1.75)]):
        a = ease((t - start) / 0.35)
        if a > 0:
            text(img, (W / 2, cy + 470 + i * 92 + 20 * (1 - a)), s, ImageFont.truetype(FB if i == 2 else FS, 70),
                 ACC if i == 2 else (255, 255, 255), alpha=a)
    # 2.6–3.0: Weißblende zum Video
    if t > 2.6:
        img.alpha_composite(Image.new('RGBA', (W, H), (255, 255, 255, int(255 * ease((t - 2.6) / 0.4)))))
    return img.convert('RGB')


# ---------------- Outro (7,0 s) ----------------
OUTRO_DUR = 7.0


def make_qr(size):
    q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, border=2, box_size=10)
    q.add_data(APK_URL); q.make(fit=True)
    return q.make_image(fill_color=(14, 14, 16), back_color=(255, 255, 255)).convert('RGBA').resize((size, size), Image.NEAREST)


QR = make_qr(480)


def rounded(im, r):
    m = Image.new('L', im.size, 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, im.width - 1, im.height - 1], r, fill=255)
    out = im.copy(); out.putalpha(m); return out


def outro_frame(t):
    img = BG_OUTRO.copy()
    # Weißblende vom Video herein
    a0 = 1 - ease(t / 0.4)
    # Logo oben
    p = back((t - 0.1) / 0.6)
    paste_scaled(img, LOGO, W / 2, 330, 0.36 * max(p, 0) if t > 0.1 else 0)
    # Unterzeile
    a = ease((t - 0.5) / 0.4)
    text(img, (W / 2, 470), 'Kostenlos · Open Source · ohne Werbung', ImageFont.truetype(FM, 40), (225, 225, 230), alpha=a)
    # QR-Karte
    c = ease_out((t - 0.8) / 0.6)
    if c > 0:
        card = Image.new('RGBA', (620, 760), (0, 0, 0, 0)); cd = ImageDraw.Draw(card)
        cd.rounded_rectangle([0, 0, 619, 759], 44, fill=(255, 255, 255, 255), outline=ACC + (255,), width=10)
        card.alpha_composite(QR, (70, 70))
        cd.text((310, 585), 'Neueste Version', font=ImageFont.truetype(FB, 52), fill=(14, 14, 16), anchor='ma')
        cd.text((310, 655), 'scannen & installieren', font=ImageFont.truetype(FM, 38), fill=(90, 90, 96), anchor='ma')
        # dezentes Pulsieren der Karte
        pulse = 1 + 0.015 * math.sin(max(0, t - 1.6) * 4) if t > 1.6 else 1
        paste_scaled(img, card, W / 2, 1000 + 80 * (1 - c), (0.85 + 0.15 * c) * pulse, c)
    a = ease((t - 1.4) / 0.4)
    text(img, (W / 2, 1420), 'github.com/MoManufaktur/MoCam', ImageFont.truetype(FS, 40), ACC, alpha=a)
    # Spenden-Hinweis
    s = ease_out((t - 2.2) / 0.5)
    if s > 0:
        box = Image.new('RGBA', (900, 250), (0, 0, 0, 0)); bd = ImageDraw.Draw(box)
        bd.rounded_rectangle([0, 0, 899, 249], 36, fill=(32, 32, 36, 235), outline=(255, 255, 255, 60), width=2)
        bd.text((450, 38), '💛', font=ImageFont.truetype('/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf', 109) if os.path.exists('/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf') else ImageFont.truetype(FB, 60), anchor='ma', embedded_color=True) if False else None
        bd.text((450, 46), 'Gefällt dir MoCam?', font=ImageFont.truetype(FB, 54), fill=(255, 255, 255), anchor='ma')
        bd.text((450, 122), 'Unterstütze das Projekt über den', font=ImageFont.truetype(FM, 38), fill=(220, 220, 225), anchor='ma')
        bd.text((450, 172), 'Spenden-Button in der App ♥', font=ImageFont.truetype(FB, 40), fill=ACC, anchor='ma')
        paste_scaled(img, box, W / 2, 1640 + 60 * (1 - s), 1.0, s)
    if a0 > 0:
        img.alpha_composite(Image.new('RGBA', (W, H), (255, 255, 255, int(255 * a0))))
    if t > OUTRO_DUR - 0.6:
        img.alpha_composite(Image.new('RGBA', (W, H), (0, 0, 0, int(255 * ease((t - (OUTRO_DUR - 0.6)) / 0.6)))))
    return img.convert('RGB')


# ---------------- Ton ----------------
def tone(f, d, vol, decay=6.0):
    t = np.arange(int(SR * d)) / SR
    return vol * np.sin(2 * np.pi * f * t) * np.exp(-t * decay)


def mix(buf, at, snd):
    i = int(at * SR); j = min(len(buf), i + len(snd)); buf[i:j] += snd[:j - i]


def noise_sweep(d, vol, f0=300, f1=6000):
    n = int(SR * d); rng = np.random.default_rng(1)
    x = rng.standard_normal(n)
    # einfacher Tiefpass mit wachsender Grenzfrequenz -> „Whoosh“
    out = np.zeros(n); y = 0.0
    for k in range(n):
        fc = f0 + (f1 - f0) * (k / n); a = 1 - math.exp(-2 * math.pi * fc / SR)
        y += a * (x[k] - y); out[k] = y
    env = np.sin(np.linspace(0, math.pi, n)) ** 1.5
    return vol * out * env


def intro_audio(path):
    buf = np.zeros(int(SR * INTRO_DUR))
    mix(buf, 0.0, noise_sweep(0.5, 0.5))
    # Auslöser-Klick
    click = np.random.default_rng(2).standard_normal(int(0.03 * SR)) * np.exp(-np.arange(int(0.03 * SR)) / SR * 200)
    mix(buf, 0.62, 0.6 * click); mix(buf, 0.70, 0.4 * click)
    # Bass-Hit + Akkord (A-Dur) beim Schriftzug
    t = np.arange(int(0.8 * SR)) / SR
    mix(buf, 0.9, 0.7 * np.sin(2 * np.pi * (45 + 60 * np.exp(-t * 18)) * t) * np.exp(-t * 5))
    for f in (220, 277.2, 329.6, 440): mix(buf, 0.9, tone(f, 2.0, 0.12, 1.6))
    # Glitzern beim Claim
    for k, f in enumerate((880, 1108.7, 1318.5)): mix(buf, 1.35 + 0.2 * k, tone(f, 0.6, 0.09, 7))
    mix(buf, 2.55, noise_sweep(0.45, 0.35, 2000, 9000))
    write(path, buf)


def outro_audio(path):
    buf = np.zeros(int(SR * OUTRO_DUR))
    mix(buf, 0.0, noise_sweep(0.4, 0.3, 6000, 500))
    for k, f in enumerate((440, 554.4, 659.3, 880)): mix(buf, 0.15 + 0.12 * k, tone(f, 1.2, 0.1, 3))
    for f in (220, 277.2, 329.6): mix(buf, 0.9, tone(f, 5.5, 0.07, 0.5))
    for k, f in enumerate((659.3, 880)): mix(buf, 2.2 + 0.15 * k, tone(f, 0.8, 0.08, 5))
    fo = int(1.0 * SR); buf[-fo:] *= np.linspace(1, 0, fo)
    write(path, buf)


def write(path, buf):
    buf = buf / max(1e-6, np.max(np.abs(buf))) * 0.85
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((buf * 32767).astype(np.int16).tobytes())


def render(name, frame_fn, dur, audio_fn):
    tmp = os.path.join(OUT, name + '.bild.tmp.mp4')
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                          '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast',
                          '-pix_fmt', 'yuv420p', tmp], stdin=subprocess.PIPE)
    for k in range(int(dur * FPS)):
        p.stdin.write(frame_fn(k / FPS).tobytes())
    p.stdin.close(); assert p.wait() == 0
    wav = os.path.join(OUT, name + '.wav'); audio_fn(wav)
    fin = os.path.join(OUT, name + '.tmp.mp4')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-i', wav, '-map', '0:v', '-map', '1:a', '-c:v', 'copy',
                    '-c:a', 'aac', '-b:a', '160k', '-ar', '44100', '-ac', '2', '-shortest', '-movflags', '+faststart', fin], check=True)
    os.replace(fin, os.path.join(OUT, name + '.mp4')); os.remove(tmp); os.remove(wav)


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'test':
        for t in [0.3, 0.8, 1.2, 2.2]: intro_frame(t).resize((360, 640)).save(f'/tmp/io_i{t}.png')
        for t in [0.3, 1.2, 3.5]: outro_frame(t).resize((360, 640)).save(f'/tmp/io_o{t}.png')
        sys.exit()
    render('MoCam-Intro', intro_frame, INTRO_DUR, intro_audio)
    render('MoCam-Outro', outro_frame, OUTRO_DUR, outro_audio)
    print('fertig')
