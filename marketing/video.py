"""30-Sekunden-Werbevideo (1080×1920, 30 fps) aus den Store-Bildern, mit synthetischer Musik."""
import os, subprocess, sys, wave
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
STORE = os.path.join(ROOT, 'store')
OUTDIR = os.path.join(ROOT, 'video'); os.makedirs(OUTDIR, exist_ok=True)
W, H, FPS, DUR = 1080, 1920, 30, 30.0
FB = '/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf'
FM = '/usr/share/fonts/opentype/inter/Inter-Medium.otf'
ACCENT = (255, 213, 79)

FEATURES = ['01_logo', '02_bauchbinde', '03_untertitel', '04_ringlicht', '06_pro', '07_helfer', '09_teilen']
INTRO, PER, OUTRO, XF = 3.0, 3.0, 6.0, 0.4


def ease(t):
    t = max(0.0, min(1.0, t)); return t * t * (3 - 2 * t)


def bg():
    g = Image.new('RGB', (W, H)); d = ImageDraw.Draw(g)
    for y in range(H):
        t = y / H; d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip((30, 30, 34), (12, 12, 14))))
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); dd = ImageDraw.Draw(layer)
    dd.ellipse([W - 420, -260, W + 320, 480], fill=ACCENT + (34,)); dd.ellipse([-360, H - 520, 360, H + 200], fill=ACCENT + (18,))
    return Image.alpha_composite(g.convert('RGBA'), layer.filter(ImageFilter.GaussianBlur(40))).convert('RGB')


BG = bg()
ICON = Image.open(os.path.join(STORE, 'app_icon_512.png')).convert('RGB')
SLIDES = [Image.open(os.path.join(STORE, f)).convert('RGB') for f in
          [n for n in sorted(os.listdir(STORE)) if n[:2].isdigit() and n[3:-4] in [x[3:] for x in FEATURES]]]


def centered(d, y, text, font, fill):
    d.text((W / 2, y), text, font=font, fill=fill, anchor='ma')


def rounded_icon(size):
    ic = ICON.resize((size, size), Image.LANCZOS)
    m = Image.new('L', (size, size), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=size // 4, fill=255)
    return ic, m


def intro(t):
    img = BG.copy()
    p = ease(t / 0.8)
    size = int(220 + 60 * p)
    ic, m = rounded_icon(size)
    y = int(620 - 40 * p)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    layer.paste(ic.convert('RGBA'), ((W - size) // 2, y), m)
    a = layer.getchannel('A').point(lambda v: int(v * p)); layer.putalpha(a)
    img = Image.alpha_composite(img.convert('RGBA'), layer)
    d = ImageDraw.Draw(img)
    p2 = ease((t - 0.5) / 0.7)
    txt = Image.new('RGBA', (W, H), (0, 0, 0, 0)); td = ImageDraw.Draw(txt)
    centered(td, 990 + int(30 * (1 - p2)), 'MoCam', ImageFont.truetype(FB, 150), (255, 255, 255, int(255 * p2)))
    p3 = ease((t - 1.1) / 0.7)
    centered(td, 1180 + int(30 * (1 - p3)), 'Die Kamera-App für deine Marke', ImageFont.truetype(FM, 52), ACCENT + (int(255 * p3),))
    return Image.alpha_composite(img, txt).convert('RGB')


def feature(i, t):
    """Store-Bild mit langsamem Zoom (Ken Burns)."""
    s = SLIDES[i]
    z = 1.0 + 0.06 * (t / PER)
    w2, h2 = int(W * z), int(H * z)
    im = s.resize((w2, h2), Image.BILINEAR)
    x0, y0 = (w2 - W) // 2, int((h2 - H) * 0.35)
    return im.crop((x0, y0, x0 + W, y0 + H))


def outro(t):
    img = BG.copy().convert('RGBA')
    ic, m = rounded_icon(200)
    p = ease(t / 0.6)
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0)); layer.paste(ic.convert('RGBA'), ((W - 200) // 2, 380), m)
    layer.putalpha(layer.getchannel('A').point(lambda v: int(v * p)))
    img = Image.alpha_composite(img, layer)
    txt = Image.new('RGBA', (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(txt)
    centered(d, 640, 'MoCam', ImageFont.truetype(FB, 120), (255, 255, 255, int(255 * p)))
    lines = [('Open Source', 0.5), ('Kostenlos', 0.9), ('Ohne Werbung · ohne Tracking', 1.3)]
    y = 880
    for text, start in lines:
        q = ease((t - start) / 0.5)
        centered(d, y + int(24 * (1 - q)), text, ImageFont.truetype(FB if text != lines[2][0] else FM, 74 if text != lines[2][0] else 48),
                 (ACCENT if text != lines[2][0] else (230, 230, 235)) + (int(255 * q),))
        y += 110
    q = ease((t - 2.0) / 0.6)
    # Knopf-artiger Hinweis
    bw, bh, by = 760, 110, 1330
    d.rounded_rectangle([(W - bw) // 2, by, (W + bw) // 2, by + bh], radius=55, fill=ACCENT + (int(255 * q),))
    centered(d, by + 28, 'Bald im Play Store', ImageFont.truetype(FB, 50), (20, 20, 20, int(255 * q)))
    q2 = ease((t - 2.6) / 0.6)
    centered(d, by + 170, 'github.com/MoManufaktur/MoCam', ImageFont.truetype(FM, 40), (200, 200, 205, int(255 * q2)))
    img = Image.alpha_composite(img, txt).convert('RGB')
    if t > OUTRO - 0.6:  # sanft ausblenden
        img = Image.blend(img, Image.new('RGB', (W, H)), ease((t - (OUTRO - 0.6)) / 0.6))
    return img


def frame_at(T):
    if T < INTRO:
        base = intro(T)
        if T > INTRO - XF:
            return Image.blend(base, feature(0, 0), ease((T - (INTRO - XF)) / XF))
        return base
    T2 = T - INTRO
    if T2 < PER * len(SLIDES):
        i = int(T2 // PER); t = T2 - i * PER
        cur = feature(i, t)
        if t > PER - XF:
            nxt = feature(i + 1, 0) if i + 1 < len(SLIDES) else outro(0)
            return Image.blend(cur, nxt, ease((t - (PER - XF)) / XF))
        return cur
    return outro(T2 - PER * len(SLIDES))


def music(path):
    """Ruhiger, moderner Hintergrund: Pad-Akkorde + weicher Beat, 100 BPM."""
    sr = 44100; n = int(sr * DUR); t = np.arange(n) / sr
    out = np.zeros(n)
    bpm = 100; beat = 60 / bpm; bar = beat * 4
    chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]  # Am F C G
    f = lambda m: 440 * 2 ** ((m - 69) / 12)
    for k in range(int(DUR / bar) + 1):
        s0 = int(k * bar * sr); s1 = min(n, int((k + 1) * bar * sr) + int(0.3 * sr))
        if s0 >= n: break
        tt = np.arange(s1 - s0) / sr
        env = np.minimum(1, tt / 0.4) * np.exp(-tt * 0.35)
        for m in chords[k % 4]:
            for det in (0.997, 1.003):
                out[s0:s1] += 0.05 * env * np.sin(2 * np.pi * f(m) * det * tt)
        # Bass
        out[s0:s1] += 0.10 * env * np.sin(2 * np.pi * f(chords[k % 4][0] - 12) * tt)
    # Beat ab Sekunde 3
    for b in range(int(DUR / beat)):
        st = b * beat
        if st < INTRO or st > DUR - 1.5: continue
        s0 = int(st * sr); L = int(0.25 * sr); tt = np.arange(min(L, n - s0)) / sr
        kick = np.sin(2 * np.pi * (55 + 90 * np.exp(-tt * 30)) * tt) * np.exp(-tt * 12)
        out[s0:s0 + len(tt)] += 0.35 * kick
        if b % 2 == 1:  # leises Hi-Hat-Rauschen auf 2 und 4
            hh = np.random.default_rng(b).standard_normal(len(tt)) * np.exp(-tt * 60)
            out[s0:s0 + len(tt)] += 0.04 * hh
        # Arpeggio-Glitzern
        m = chords[int(st // bar) % 4][b % 3] + 12
        Larp = int(0.18 * sr); ta = np.arange(min(Larp, n - s0)) / sr
        out[s0:s0 + len(ta)] += 0.035 * np.sin(2 * np.pi * f(m) * ta) * np.exp(-ta * 14)
    fade = np.ones(n); fi = int(0.5 * sr); fo = int(2.0 * sr)
    fade[:fi] = np.linspace(0, 1, fi); fade[-fo:] = np.linspace(1, 0, fo)
    out *= fade; out /= np.max(np.abs(out)) * 1.15
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes((out * 32767).astype(np.int16).tobytes())


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'test':
        for T in [1.5, 2.8, 4.5, 14.9, 27.5]:
            frame_at(T).save(f'/tmp/vtest_{T}.jpg', quality=88)
        sys.exit()
    tmp = os.path.join(OUTDIR, 'bild.tmp.mp4')
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                          '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast',
                          '-pix_fmt', 'yuv420p', tmp], stdin=subprocess.PIPE)
    for k in range(int(DUR * FPS)):
        p.stdin.write(frame_at(k / FPS).tobytes())
    p.stdin.close()
    assert p.wait() == 0
    wav = os.path.join(OUTDIR, 'musik.wav'); music(wav)
    final_tmp = os.path.join(OUTDIR, 'final.tmp.mp4')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-i', wav, '-map', '0:v', '-map', '1:a',
                    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', '-shortest', '-movflags', '+faststart', final_tmp], check=True)
    os.replace(final_tmp, os.path.join(OUTDIR, 'MoCam-Werbevideo-30s.mp4'))
    os.remove(tmp)
    print('fertig')
