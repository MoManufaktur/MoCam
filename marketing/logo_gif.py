"""Animiertes MoCam-Logo (GIF, transparent). Erstes Bild = vollständiges Logo, damit MoCam nach dem
Abspielen das Startbild zeigt. Ablauf: Kamera „klickt“ (Wippen + Blende + Blitzfunke),
dann gleitet ein Lichtreflex über den Schriftzug, danach Ruhe."""
import asyncio, io, math, os
from PIL import Image
import logo as L

OUT = L.OUT
FPS, DUR = 25, 2.8
H = 120


def ease_out_back(t):
    c1, c3 = 1.70158, 2.70158
    t -= 1
    return 1 + c3 * t ** 3 + c1 * t ** 2


def frame_svg(t, text_color, shine_color):
    # 1) Klick: Icon drückt sich ein (0.35–0.55 s) und federt zurück (0.55–0.95 s)
    if 0.35 <= t < 0.55: sc = 1 - 0.10 * math.sin((t - 0.35) / 0.2 * math.pi / 2)
    elif 0.55 <= t < 0.95: sc = 0.90 + 0.10 * ease_out_back((t - 0.55) / 0.4)
    else: sc = 1.0
    # Blende: innerer Ring schließt und öffnet sich
    iris = 6.0
    if 0.4 <= t < 0.8: iris = 6.0 * abs(math.cos((t - 0.4) / 0.4 * math.pi))
    # Blitzfunke oben rechts an der Kamera
    sp = 0.0
    if 0.5 <= t < 1.0: sp = math.sin((t - 0.5) / 0.5 * math.pi)
    # 2) Lichtreflex über den Schriftzug (1.0–1.9 s)
    size = 92
    paths, tw = L.text_path('MoCam', size)
    shine = None
    if 1.0 <= t < 1.9: shine = -60 + (tw + 120) * ((t - 1.0) / 0.9)
    gap = 26
    baseline = H / 2 + size * 0.36
    W = H + gap + tw + 4
    k = H / 108
    star = ''
    if sp > 0.01:
        r = 16 * sp
        cx, cy = 74 * k, 40 * k
        pts = ' '.join(f'{cx + (r if i % 2 == 0 else r * 0.28) * math.cos(i * math.pi / 4):.2f},'
                       f'{cy + (r if i % 2 == 0 else r * 0.28) * math.sin(i * math.pi / 4):.2f}' for i in range(8))
        star = f'<polygon points="{pts}" fill="#FFF6C8"/>'
    band = ''
    clip = ''
    if shine is not None:
        # Schräger Lichtstreifen, auf die Buchstaben zugeschnitten
        x0 = H + gap + shine
        # clipPath darf keine <g> enthalten – Verschiebung direkt in jeden Buchstaben-Pfad schreiben
        cp = paths.replace('transform="translate(', f'transform="translate({H + gap},{baseline:.1f}) translate(')
        clip = f'<clipPath id="tc">{cp}</clipPath>'
        band = (f'<g clip-path="url(#tc)"><polygon points="{x0:.1f},-10 {x0 + 34:.1f},-10 {x0 - 6:.1f},{H + 10} {x0 - 40:.1f},{H + 10}" '
                f'fill="{shine_color}"/></g>')
    c = H / 2
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 -2 {W + 4:.1f} {H + 4}" width="{W + 4:.0f}" height="{H + 4}"><defs>{clip}</defs>
<g transform="translate({c},{c}) scale({sc:.4f}) translate({-c},{-c})">
<rect x="0" y="0" width="{H}" height="{H}" rx="{H * 0.225:.1f}" fill="#1B1B1B" stroke="#3A3A3A" stroke-width="2"/>
<g transform="scale({k:.5f})">
<path fill="#FFD54F" d="M34,42h10l4,-6h12l4,6h10a4,4 0,0 1,4 4v24a4,4 0,0 1,-4 4H34a4,4 0,0 1,-4 -4V46a4,4 0,0 1,4 -4z"/>
<path fill="#1B1B1B" d="M54,47a11,11 0,1 1,0 22a11,11 0,1 1,0 -22z"/>
<circle cx="54" cy="58" r="{iris:.2f}" fill="#FFD54F"/></g>{star}</g>
<g fill="{text_color}" transform="translate({H + gap},{baseline:.1f})">{paths}</g>{band}
</svg>''', W + 4, H + 4


def to_gif_frame(png_bytes):
    im = Image.open(io.BytesIO(png_bytes)).convert('RGBA')
    alpha = im.getchannel('A')
    rgb = Image.new('RGB', im.size, (0, 0, 0))
    rgb.paste(im, mask=alpha)
    p = rgb.convert('P', palette=Image.ADAPTIVE, colors=255)
    mask = alpha.point(lambda a: 255 if a < 110 else 0)
    p.paste(255, mask=mask)
    p.info['transparency'] = 255
    return p


async def main():
    from playwright.async_api import async_playwright
    async with async_playwright() as pw:
        b = await pw.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, tc, shc in [('MoCam-Logo-animiert-weiss', '#FFFFFF', '#FFD54F'),
                              ('MoCam-Logo-animiert-dunkel', '#1B1B1B', '#FFB300')]:
            _, w, h = frame_svg(0, tc, shc)
            scale = 900 / w
            pg = await b.new_page(viewport={'width': int(w), 'height': int(h)}, device_scale_factor=scale)
            frames, rgba = [], []
            for i in range(int(FPS * DUR)):
                s, _, _ = frame_svg(i / FPS, tc, shc)
                await pg.set_content(f'<html><body style="margin:0;background:transparent">{s}</body></html>')
                png = await pg.screenshot(omit_background=True)
                frames.append(to_gif_frame(png))
                rgba.append(Image.open(io.BytesIO(png)).convert('RGBA'))
            await pg.close()
            # Weiche Kanten: animiertes WebP und APNG (echte Transparenz)
            base = name.replace('.gif', '')
            rgba[0].save(os.path.join(OUT, base + '.webp'), save_all=True, append_images=rgba[1:],
                         duration=int(1000 / FPS), loop=0, lossless=False, quality=90, method=6)
            rgba[0].save(os.path.join(OUT, base + '.apng'), format='PNG', save_all=True, append_images=rgba[1:],
                         duration=int(1000 / FPS), loop=0, disposal=1, blend=0, optimize=True)
            frames[0].save(os.path.join(OUT, name + '.gif'), save_all=True, append_images=frames[1:],
                           duration=int(1000 / FPS), loop=0, disposal=2, transparency=255, optimize=False)
            print(name, frames[0].size, len(frames))
        await b.close()

asyncio.run(main())
