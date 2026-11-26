"""MoCam-Logo: App-Icon (mit seiner abgerundeten Umrandung) + Schriftzug „MoCam“, transparenter Hintergrund.
Schrift als Pfade (unabhängig von installierten Schriften). Varianten: weiße Schrift (für dunkle Flächen)
und dunkle Schrift (für helle Flächen), je als SVG und PNG."""
import asyncio, os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'logo')
FONT = '/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf'


def text_path(text, size):
    f = TTFont(FONT)
    gs, cmap, upm = f.getGlyphSet(), f.getBestCmap(), f['head'].unitsPerEm
    s = size / upm
    x, parts = 0.0, []
    hmtx = f['hmtx']
    for ch in text:
        g = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        gs[g].draw(pen)
        parts.append(f'<path transform="translate({x:.2f},0) scale({s:.5f},{-s:.5f})" d="{pen.getCommands()}"/>')
        x += hmtx[g][0] * s - size * 0.01  # leicht enger
    return ''.join(parts), x


def svg(text_color):
    H = 120  # Icon-Höhe
    icon = f'''<g>
<rect x="0" y="0" width="{H}" height="{H}" rx="{H*0.225:.1f}" fill="#1B1B1B" stroke="#3A3A3A" stroke-width="2"/>
<g transform="scale({H/108:.5f})">
<path fill="#FFD54F" d="M34,42h10l4,-6h12l4,6h10a4,4 0,0 1,4 4v24a4,4 0,0 1,-4 4H34a4,4 0,0 1,-4 -4V46a4,4 0,0 1,4 -4z"/>
<path fill="#1B1B1B" d="M54,47a11,11 0,1 1,0 22a11,11 0,1 1,0 -22z"/>
<path fill="#FFD54F" d="M54,52a6,6 0,1 1,0 12a6,6 0,1 1,0 -12z"/></g></g>'''
    size = 92
    paths, tw = text_path('MoCam', size)
    gap = 26
    baseline = H / 2 + size * 0.36  # Großbuchstaben mittig zum Icon
    W = H + gap + tw + 4
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="-2 -2 {W + 4:.1f} {H + 4}" width="{W + 4:.0f}" height="{H + 4}">
{icon}
<g fill="{text_color}" transform="translate({H + gap},{baseline:.1f})">{paths}</g>
</svg>''', W + 4, H + 4


async def main():
    from playwright.async_api import async_playwright
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome')
        for name, color in [('MoCam-Logo-weiss', '#FFFFFF'), ('MoCam-Logo-dunkel', '#1B1B1B')]:
            s, w, h = svg(color)
            open(os.path.join(OUT, name + '.svg'), 'w').write(s)
            scale = 2400 / w
            pg = await b.new_page(viewport={'width': int(w), 'height': int(h)}, device_scale_factor=scale)
            await pg.set_content(f'<html><body style="margin:0;background:transparent">{s}</body></html>')
            await pg.screenshot(path=os.path.join(OUT, name + '.png'), omit_background=True)
            await pg.close()
        await b.close()

asyncio.run(main())
