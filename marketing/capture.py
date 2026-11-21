"""Erzeugt Roh-Screenshots (1080×1920) der MoCam-Oberfläche als originalgetreue Nachbauten
(Layout, Texte und Farben wie in der App). Echte Handy-Screenshots können dieselben Dateinamen
in marketing/raw/ ersetzen – promo.py nimmt dann automatisch die echten Bilder."""
import asyncio, os
from playwright.async_api import async_playwright
import scenes

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'raw')
os.makedirs(OUT, exist_ok=True)

CSS = '''
*{box-sizing:border-box;margin:0;padding:0}
body{width:360px;height:640px;overflow:hidden;font-family:Inter,'Noto Color Emoji',sans-serif;color:#fff;background:#000;position:relative}
.bg{position:absolute;left:0;width:360px}.bg svg{position:absolute;inset:0;width:100%;height:100%}
.sb{position:absolute;top:0;left:0;right:0;height:24px;display:flex;justify-content:space-between;align-items:center;padding:0 14px;font-size:12px;font-weight:600;z-index:9}
.top{position:absolute;top:24px;left:0;right:0;background:rgba(0,0,0,.4);padding:6px 8px 6px 8px;z-index:5}
.chips{display:flex;gap:6px;overflow:hidden;white-space:nowrap}
.chip{background:rgba(0,0,0,.4);border:1px solid rgba(255,255,255,.33);border-radius:16px;padding:6px 11px;font-size:12.5px;flex:none;color:#fff}
.chip.sel{background:#FFD54F;border-color:#FFD54F;color:#000}
.rowc{display:flex;gap:6px;align-items:center;margin-top:6px}
.rec{background:rgba(0,0,0,.4);border:1px solid rgba(255,255,255,.33);border-radius:16px;padding:5px 11px;font-size:13px;font-weight:700;color:#ff5252}
.status{font-size:11.5px;color:rgba(255,255,255,.9);margin-top:5px}
.bottom{position:absolute;bottom:0;left:0;right:0;background:rgba(0,0,0,.53);padding:6px 0 14px;z-index:5}
.slrow{display:flex;align-items:center;padding:0 12px;gap:8px;font-size:12.5px;height:30px}
.track{flex:1;height:3px;background:rgba(255,255,255,.35);border-radius:2px;position:relative}
.track i{position:absolute;left:0;top:0;bottom:0;background:#FFD54F;border-radius:2px}
.track b{position:absolute;top:-6px;width:15px;height:15px;border-radius:50%;background:#FFD54F;margin-left:-7px}
.zc{display:flex;gap:4px}.zc .chip{padding:4px 8px;font-size:11.5px}
.filters{display:flex;gap:6px;padding:6px 8px 0;overflow:hidden;white-space:nowrap}
.modes{display:flex;justify-content:center;gap:24px;margin-top:10px;font-weight:700;font-size:14.5px}
.modes span{padding:6px;color:#fff}.modes .on{color:#FFD54F}
.ctr{height:96px;position:relative;margin:0 28px}
.thumb{position:absolute;left:0;top:21px;width:54px;height:54px;border-radius:10px;border:2px solid #fff;background:#555;overflow:hidden}
.round{position:absolute;top:24px;width:48px;height:48px;border-radius:50%;background:rgba(255,255,255,.33);display:flex;align-items:center;justify-content:center;font-size:20px}
.sw{right:0;top:21px;width:54px;height:54px;font-size:25px}
.shutter{position:absolute;left:50%;top:8px;margin-left:-40px;width:80px;height:80px;border-radius:50%;border:4px solid #fff}
.shutter i{position:absolute;inset:5px;border-radius:50%;background:#fff}
.shutter.video i{background:#E53935}
.shutter.recording i{inset:20px;border-radius:6px;background:#E53935}
.grid{position:absolute;inset:0;z-index:2;pointer-events:none}
.grid i{position:absolute;background:rgba(255,255,255,.45)}
.logo{position:absolute;z-index:3;display:flex;align-items:center;gap:7px;padding:7px 11px;border-radius:12px;background:rgba(20,16,12,.55);border:1.5px solid rgba(255,213,79,.9)}
.logo .m{width:30px;height:30px;border-radius:8px;background:#FFD54F;color:#2a1a08;font:800 15px Inter;display:flex;align-items:center;justify-content:center}
.logo .t{font:800 12px Inter;line-height:1.05;letter-spacing:.5px}.logo .t small{display:block;font:600 8.5px Inter;letter-spacing:2px;color:#FFD54F}
.lt{position:absolute;z-index:3;left:18px;display:flex}
.lt .bar{width:6px;background:#FFD54F}
.lt .box{background:rgba(17,17,17,.82);padding:8px 14px}
.lt .n{font:800 18px Inter}.lt .f{font:500 12px Inter;color:#ddd;margin-top:2px}
.sub{position:absolute;z-index:3;left:24px;right:24px;text-align:center}
.sub span{background:rgba(0,0,0,.62);padding:4px 9px;border-radius:6px;font:700 16px/1.6 Inter;box-decoration-break:clone;-webkit-box-decoration-break:clone}
.panel{position:absolute;left:0;right:0;bottom:0;background:rgba(17,17,17,.93);padding:12px 16px 16px;z-index:8}
.panel h3{color:#FFD54F;font:700 16px Inter}
.hdr{display:flex;justify-content:space-between;align-items:center;margin-bottom:6px}
.sec{color:#FFD54F;font:700 13.5px Inter;margin:12px 0 4px}
.txt{font-size:12px;color:rgba(255,255,255,.8);line-height:1.4}
.wrap{display:flex;flex-wrap:wrap;gap:6px;margin-top:4px}
.entry{border:1px solid rgba(255,255,255,.33);background:rgba(0,0,0,.4);border-radius:16px;padding:10px 14px;margin-top:8px}
.entry b{display:flex;justify-content:space-between;font-size:14.5px}.entry b em{color:#FFD54F;font-style:normal;font-size:18px;line-height:14px}
.entry div{font-size:11.5px;color:rgba(255,255,255,.75);margin-top:3px}
.toggle{display:flex;justify-content:space-between;align-items:center;font-size:13px;margin-top:8px}
.sw2{width:36px;height:20px;border-radius:10px;background:#FFD54F;position:relative}.sw2:after{content:'';position:absolute;right:2px;top:2px;width:16px;height:16px;border-radius:50%;background:#fff}
.share{display:flex;gap:6px;padding:4px 8px 6px;overflow:hidden;white-space:nowrap}
.share .chip{background:#FFD54F;color:#000;border-color:#FFD54F;font-weight:600}
.histo{position:absolute;z-index:4;left:12px;width:130px;height:60px;background:rgba(0,0,0,.45);border-radius:6px;display:flex;align-items:flex-end;padding:3px;gap:1px}
.histo i{flex:1;background:rgba(255,255,255,.85)}
.level{position:absolute;z-index:4;left:50%;top:300px;width:220px;margin-left:-110px;height:2px;background:#5cff7a}
.leveltxt{position:absolute;z-index:4;left:50%;top:306px;transform:translateX(-50%);font:700 12px Inter;color:#5cff7a}
.zebra{position:absolute;z-index:2;background:repeating-linear-gradient(45deg,rgba(25,25,25,.9) 0 3px,transparent 3px 6px)}
.focus{position:absolute;z-index:4;width:70px;height:70px;border:2px solid #FFD54F;border-radius:6px}
.lock{position:absolute;z-index:4;font:700 11px Inter;color:#FFD54F;background:rgba(0,0,0,.5);padding:2px 6px;border-radius:6px}
.prompter{position:absolute;z-index:6;top:84px;left:8px;right:8px;height:222px;overflow:hidden;background:rgba(0,0,0,.65);border:1px solid rgba(255,255,255,.27);border-radius:14px;padding:12px 14px}
.prompter p{font:700 18px/1.42 Inter}
.prompter .cur{color:#FFD54F}
.lightbar{position:absolute;left:16px;right:16px;z-index:7;background:#fff;border:2px solid #222;border-radius:16px;padding:7px 12px;display:flex;align-items:center;gap:8px;color:#111;font-size:13px}
.light .top,.light .bottom{background:rgba(255,255,255,.96)}
.light .chip{background:#fff;border:2px solid #222;color:#111}
.light .round{background:#fff;border:2px solid #222;color:#111}
.light .modes span,.light .slrow,.light .status,.light .sb{color:#111}
.light .modes .on{color:#c85000}
.light .shutter{border-color:#ddd}
.light .track{background:rgba(0,0,0,.25)}
'''

STATUS = '<div class="sb"><span>9:41</span><span>📶 🔋</span></div>'


def chips(items, sel=()):
    return '<div class="chips">' + ''.join(f'<span class="chip{" sel" if i in sel else ""}">{t}</span>' for i, t in enumerate(items)) + '</div>'


def top(items, rec=None, status=None, extra=''):
    r = f'<div class="rowc"><span class="rec">{rec}</span>{extra}</div>' if rec else (f'<div class="rowc">{extra}</div>' if extra else '')
    s = f'<div class="status">{status}</div>' if status else ''
    return f'<div class="top">{chips(items)}{r}{s}</div>'


def bottom(video=False, recording=False, share=False, filt=0, ev='0.0 EV', zoom='1.0x', zpos=8, thumb_bg='#7a5a3a'):
    f = ['Original', 'Lebendig', 'S/W', 'Noir', 'Sepia', 'Vintage', 'Kühl', 'Warm']
    sh = ''
    if share:
        sh = '<div class="share">' + ''.join(f'<span class="chip">{t}</span>' for t in
                                             ['🟢 WhatsApp Status', '📸 Instagram', '💼 LinkedIn', '📌 Pinterest', '⋯ Mehr']) + '</div>'
    cls = 'recording' if recording else ('video' if video else '')
    return f'''<div class="bottom">{sh}
<div class="slrow"><span style="font-size:17px">☀</span><div class="track"><i style="width:50%"></i><b style="left:50%"></b></div><span style="width:52px;text-align:right">{ev}</span></div>
<div class="slrow"><div class="zc"><span class="chip">0.6</span><span class="chip">1x</span><span class="chip">2x</span><span class="chip">5x</span></div><div class="track"><i style="width:{zpos}%"></i><b style="left:{zpos}%"></b></div><span style="width:40px;text-align:right">{zoom}</span></div>
<div class="filters">{''.join(f'<span class="chip{" sel" if i == filt else ""}">{n}</span>' for i, n in enumerate(f))}</div>
<div class="modes"><span class="{'' if video else 'on'}">FOTO</span><span class="{'on' if video else ''}">VIDEO</span></div>
<div class="ctr"><div class="thumb" style="background:{thumb_bg}"></div><div class="round" style="left:64px">🗂</div>
<div class="shutter {cls}"><i></i></div>{'<div class="round" style="right:64px">🔖</div>' if recording else ''}<div class="round sw">⟲</div></div></div>'''


def logo(x, y):
    return f'<div class="logo" style="left:{x}px;top:{y}px"><div class="m">HN</div><div class="t">HOLZWERK<small>NORD</small></div></div>'


def page(bg, body, cls='', shift=0):
    """shift: Motiv nach oben schieben, damit es im sichtbaren Bereich über der unteren Leiste liegt."""
    return (f'<html><head><style>{CSS}</style></head><body class="{cls}">'
            f'<div class="bg" style="top:-{shift}px;height:{640 + shift}px">{bg}</div>{STATUS}{body}</body></html>')


VIDEO_CHIPS = ['⚙️', '🔦 Licht aus', '▭ 9:16', '⏱ Timer aus', '⏳ Limit aus', '🪪 Bauchbinde an', '⏩ Zeitraffer aus', '▦ Raster aus']
PHOTO_CHIPS = ['⚙️', '⚡ Blitz aus', '🎛 Pro', '⏲ Intervall aus', '🎚 Reihe aus', '▭ 3:4', '⏱ Timer aus', '▦ Raster an']


def histo_html(top_px, shape):
    return f'<div class="histo" style="top:{top_px}px">' + ''.join(f'<i style="height:{h}%"></i>' for h in shape) + '</div>'


SCREENS = {}

# 1 Logo im Video (Aufnahme läuft)
SCREENS['01_logo'] = page(scenes.sunset(),
    top(VIDEO_CHIPS[:1] + ['🔦 Licht aus', '▭ 9:16', '🏷 Logo an', '🪪 Bauchbinde aus', '⏱ Timer aus'],
        rec='● 00:12', status='💾 ≈ 3 h 12 min · 🔋 82 %') +
    logo(222, 128) + bottom(video=True, recording=True), shift=170)

# 2 Projekteinstellungen
SCREENS['02_projekt'] = page(scenes.workshop(), logo(30, 40) + '''
<div class="panel"><div class="hdr"><span class="chip">←</span><h3>📁 Projekteinstellungen</h3><span class="chip sel">✓ Fertig</span></div>
<div style="font:700 15px Inter;margin-top:6px">Projekt: Holzwerk Nord</div>
<div class="txt">Anderes Logo wählen = anderes Projekt. Alles hier gilt nur für dieses Projekt.</div>
<div class="sec">📂 Speicherort</div><div class="txt">Fotos: Bilder/MoCam/Holzwerk · Videos: Filme/MoCam/Holzwerk</div>
<div class="wrap"><span class="chip">📁 Ordner: MoCam/Holzwerk – ändern</span></div>
<div class="sec">🏷 Logo</div><div class="txt">Position, Größe und Drehung je Foto/Video und Rück-/Frontkamera.</div>
<div class="wrap"><span class="chip">🏷 Logo einstellen …</span><span class="chip">Logo / Projekt wechseln …</span></div>
<div class="sec">🪪 Bauchbinde</div><div class="txt">Lena Hansen · Inhaberin · Holzwerk Nord</div>
<div class="sec">©️ Copyright</div><div class="txt">An · Lena Hansen · © Holzwerk Nord</div>
<div class="sec">🎬 Intro & Outro</div>
<div class="toggle"><span>An jedes Video dieses Projekts anhängen</span><span class="sw2"></span></div>
<div class="txt" style="color:#fff;margin-top:6px">Intro: Holzwerk_Intro_2026.mp4</div>
<div class="wrap"><span class="chip">▶ Abspielen</span><span class="chip">Ändern</span><span class="chip">Entfernen</span></div>
<div class="txt" style="color:#fff;margin-top:6px">Outro: Holzwerk_Outro.mp4</div>
<div class="wrap"><span class="chip">▶ Abspielen</span><span class="chip">Ändern</span><span class="chip">Entfernen</span></div>
</div>''')

# 3 Bauchbinde
SCREENS['03_bauchbinde'] = page(scenes.interview(),
    top(['⚙️', '🔦 Licht aus', '▭ 9:16', '🏷 Logo an', '🪪 Bauchbinde an', '⏱ Timer aus'], rec='● 00:04', status='💾 ≈ 3 h 10 min · 🔋 81 %') +
    logo(232, 128) +
    '<div class="lt" style="top:262px"><div class="bar"></div><div class="box"><div class="n">Lena Hansen</div><div class="f">Inhaberin · Holzwerk Nord</div></div></div>' +
    bottom(video=True, recording=True, thumb_bg='#6b2f2f'), shift=90)

# 4 Untertitel
SCREENS['04_untertitel'] = page(scenes.interview(),
    top(['⚙️', '🔦 Licht aus', '▭ 9:16', '🏷 Logo an', '🪪 Bauchbinde aus'], status='💾 ≈ 3 h 08 min · 🔋 80 %',
        extra='<span class="rec" style="color:#fff">💬 Untertitel werden erkannt … 64 %</span>') +
    logo(232, 140) +
    '<div class="sub" style="top:232px"><span>Heute zeige ich euch, wie wir das Brett per Laser gravieren.</span></div>' +
    bottom(video=True, share=True, thumb_bg='#6b2f2f'), shift=110)

# 5 Ringlicht (Frontkamera, heller Modus)
SCREENS['05_ringlicht'] = page(scenes.portrait(light=True),
    top(['⚙️', '💡 Licht an', '▭ 9:16', '⏱ Timer aus', '🏷 Logo an', '🪪 Bauchbinde aus'], status='💾 ≈ 3 h 05 min · 🔋 79 %') +
    '<div class="lightbar" style="bottom:262px"><span class="chip" style="padding:4px 9px">Auto</span><span style="min-width:86px">💡 Auto 70 %</span><div class="track" style="opacity:.5"><i style="width:68%;background:#222"></i><b style="left:68%;background:#FFA000"></b></div><span style="font-size:15px">✕</span></div>' +
    bottom(video=True, thumb_bg='#cfcac0'), cls='light', shift=150)

# 6 Pro-Modus
SCREENS['06_pro'] = page('<div style="position:absolute;inset:0">' + scenes.flower() + scenes.peaking_overlay() + '</div>', '''
<div class="panel"><div class="hdr"><span class="chip">←</span><h3>🎛 Pro-Modus</h3><span class="chip sel">✓ Fertig</span></div>
<div class="txt">Diese Kamera erlaubt: ISO/Verschluss, Kelvin-Weißabgleich, manueller Fokus, Weißabgleich-Presets</div>
<div class="sec">ISO</div><div class="wrap">''' + ''.join(f'<span class="chip{" sel" if v == "200" else ""}">{v}</span>' for v in ['Auto', '50', '100', '200', '400', '800', '1600', '3200']) + '''</div>
<div class="sec">Verschlusszeit</div><div class="wrap">''' + ''.join(f'<span class="chip{" sel" if v == "1/250" else ""}">{v}</span>' for v in ['Auto', '1/4000', '1/1000', '1/500', '1/250', '1/125', '1/60', '1/30']) + '''</div>
<div class="sec">Weißabgleich</div><div class="wrap">''' + ''.join(f'<span class="chip{" sel" if i == 1 else ""}">{v}</span>' for i, v in enumerate(['Auto', '☀ Tageslicht', '☁ Bewölkt', '🌳 Schatten', '💡 Glühlampe'])) + '''</div>
<div class="sec">Fokus</div><div class="wrap"><span class="chip">Autofokus</span><span class="chip sel">Manuell</span></div>
<div class="slrow" style="padding:0"><span>Unendlich ↔ Nah</span><div class="track"><i style="width:72%"></i><b style="left:72%"></b></div></div>
<div class="toggle"><span>Fokus-Peaking (scharfe Kanten rot)</span><span class="sw2"></span></div>
</div>''', shift=210)

# 7 Profi-Hilfen (Zebra, Histogramm, Wasserwaage, Raster)
grid = '<div class="grid"><i style="left:120px;top:0;bottom:0;width:1px"></i><i style="left:240px;top:0;bottom:0;width:1px"></i><i style="top:213px;left:0;right:0;height:1px"></i><i style="top:427px;left:0;right:0;height:1px"></i></div>'
SCREENS['07_helfer'] = page(scenes.sunset(),
    '<div class="zebra" style="left:190px;top:222px;width:84px;height:70px;border-radius:50%"></div>' + grid +
    top(['⚙️', '⚡ Blitz aus', '🎛 Pro', '▦ Raster an', '🦓 Zebra an', '📊 Histogramm an', '📐 Wasserwaage an'], status='💾 ≈ 5.840 Fotos · 🔋 78 %') +
    histo_html(118, [4, 6, 10, 18, 30, 44, 52, 48, 40, 34, 30, 28, 30, 36, 46, 58, 70, 78, 74, 60, 44, 30, 20, 14, 12, 16, 26, 40, 30, 18]) +
    '<div class="level" style="top:205px"></div><div class="leveltxt" style="top:211px">0.0°</div>' +
    '<div class="focus" style="left:60px;top:200px"></div><div class="lock" style="left:62px;top:273px">AE/AF 🔒</div>' +
    bottom(filt=1, thumb_bg='#f08a4b'), shift=190)

# 8 Teilen
SCREENS['08_teilen'] = page(scenes.workshop(),
    top(['⚙️', '🔦 Licht aus', '▭ 9:16', '🏷 Logo an', '🪪 Bauchbinde aus'], status='💾 ≈ 3 h 01 min · 🔋 77 %',
        extra='<span class="rec" style="color:#5cff7a">✓ Video gespeichert</span>') +
    logo(222, 128) + bottom(video=True, share=True, thumb_bg='#b07a45'), shift=170)

# 9 Teleprompter
SCREENS['09_prompter'] = page(scenes.portrait(),
    top(['⚙️', '💡 Licht', '▭ 9:16', '📜 Prompter an', '🏷 Logo an'], rec='● 00:21') +
    '''<div class="prompter"><div class="chips" style="margin-bottom:10px"><span class="chip">▶</span><div class="track" style="flex:1;align-self:center"><i style="width:40%"></i><b style="left:40%"></b></div><span class="chip">A−</span><span class="chip">A+</span><span class="chip">✎</span><span class="chip">✕</span></div>
<p style="opacity:.5">Hallo und willkommen bei Holzwerk Nord!</p><p class="cur">Heute zeige ich euch unser neues Schneidebrett aus Eiche –</p><p>graviert mit eurem Motiv.</p></div>''' +
    bottom(video=True, recording=True, thumb_bg='#3a4a5e'))

# 10 Open Source / Einstellungen
SCREENS['10_opensource'] = page(scenes.sunset(), '''
<div class="panel"><h3 style="font-size:17px">⚙️ Einstellungen</h3>
<div class="entry"><b>📁 Projekteinstellungen<em>›</em></b><div>Projekt: Holzwerk Nord · Ordner: MoCam/Holzwerk</div></div>
<div class="entry"><b>💬 Untertitel<em>›</em></b><div>An · 🎯 Whisper Genau · Eingebrannt</div></div>
<div class="entry"><b>🎤 Mikrofon<em>›</em></b><div>Automatisch (intern / USB)</div></div>
<div class="entry"><b>🧭 Ansicht<em>›</em></b><div>Buttonleiste scrollbar</div></div>
<div class="entry"><b>🔧 Grundeinstellungen<em>›</em></b><div>Rück- &amp; Frontkamera: Spiegeln, Ausrichtung</div></div>
<div class="sec" style="margin-top:16px">💛 Unterstützen</div>
<div class="txt">MoCam ist Open Source, kostenlos und werbefrei – das Projekt lebt von Spenden.</div>
<div class="wrap" style="margin-top:8px"><span class="chip" style="padding:18px 22px;font-size:15px">💛 Spenden</span><span class="chip" style="align-self:center">GitHub</span></div>
<div class="wrap" style="margin-top:14px"><span class="chip">Zurücksetzen</span><span class="chip">✓ Fertig</span></div>
<div class="txt" style="text-align:center;margin-top:14px;font-size:11px;color:rgba(255,255,255,.55)">MoCam Version 1.1.47</div>
</div>''')


async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome')
        pg = await b.new_page(viewport={'width': 360, 'height': 640}, device_scale_factor=3)
        for name, html in SCREENS.items():
            await pg.set_content(html)
            await pg.wait_for_timeout(150)
            await pg.screenshot(path=os.path.join(OUT, name + '.png'))
            print('ok', name)
        await b.close()

if __name__ == '__main__':
    asyncio.run(main())
