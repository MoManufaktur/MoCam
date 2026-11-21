"""Kamera-Hintergründe als SVG (360×640) für die UI-Nachbauten der Werbebilder."""


def _turbine(x, y, h, rot=0, op=0.9):
    blade = h * 0.42
    return f'''<g opacity="{op}" fill="#141824">
      <polygon points="{x-2.2},{y} {x+2.2},{y} {x+1.1},{y-h} {x-1.1},{y-h}"/>
      <g transform="translate({x},{y-h}) rotate({rot})">
        <path d="M0,0 C2,-{blade*0.3} 2,-{blade*0.8} 0,-{blade} C-1.2,-{blade*0.8} -1.6,-{blade*0.3} 0,0Z"/>
        <path transform="rotate(120)" d="M0,0 C2,-{blade*0.3} 2,-{blade*0.8} 0,-{blade} C-1.2,-{blade*0.8} -1.6,-{blade*0.3} 0,0Z"/>
        <path transform="rotate(240)" d="M0,0 C2,-{blade*0.3} 2,-{blade*0.8} 0,-{blade} C-1.2,-{blade*0.8} -1.6,-{blade*0.3} 0,0Z"/>
        <circle r="2.6"/></g></g>'''


def sunset():
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#22305a"/><stop offset=".38" stop-color="#7d4a7a"/><stop offset=".62" stop-color="#f08a4b"/><stop offset=".78" stop-color="#ffd27a"/></linearGradient>
<radialGradient id="sun" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#fff6d8"/><stop offset=".45" stop-color="#ffe08a" stop-opacity=".95"/><stop offset="1" stop-color="#ffb35c" stop-opacity="0"/></radialGradient>
<linearGradient id="field" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3a3a3f"/><stop offset="1" stop-color="#121216"/></linearGradient></defs>
<rect width="360" height="640" fill="url(#sky)"/>
<circle cx="230" cy="430" r="95" fill="url(#sun)"/>
<path d="M0,455 C60,430 120,440 180,450 C240,460 300,438 360,446 L360,640 L0,640Z" fill="#4b3a4e" opacity=".75"/>
{_turbine(70, 470, 120, 15)}{_turbine(160, 462, 150, 75)}{_turbine(262, 474, 105, 40)}{_turbine(330, 470, 80, 100, .8)}
<path d="M0,480 C80,465 160,470 240,482 C300,490 330,484 360,480 L360,640 L0,640Z" fill="url(#field)"/>
<g stroke="#2b2b31" stroke-width="1" opacity=".6">{''.join(f'<line x1="{i*24-40}" y1="640" x2="{180+(i*24-180)*0.25}" y2="485"/>' for i in range(20))}</g>
</svg>'''


def workshop():
    bok = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#ffcf7a" opacity="{o}"/>' for x, y, r, o in
                  [(40, 90, 26, .18), (120, 60, 18, .22), (300, 120, 34, .15), (250, 40, 14, .25), (330, 230, 22, .14),
                   (20, 260, 30, .12), (180, 180, 12, .2), (90, 200, 16, .16)])
    grain = ''.join(f'<path d="M0,{430+i*14} C120,{424+i*14} 240,{438+i*14} 360,{430+i*14}" stroke="#5a3a1e" stroke-width="1.2" fill="none" opacity=".35"/>' for i in range(16))
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs><linearGradient id="wall" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2a1d14"/><stop offset="1" stop-color="#5b3b22"/></linearGradient>
<filter id="b"><feGaussianBlur stdDeviation="6"/></filter>
<linearGradient id="tab" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#b07a45"/><stop offset="1" stop-color="#6e4422"/></linearGradient>
<linearGradient id="board" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e2b77d"/><stop offset="1" stop-color="#c08a4e"/></linearGradient></defs>
<rect width="360" height="640" fill="url(#wall)"/><g filter="url(#b)">{bok}</g>
<rect y="420" width="360" height="220" fill="url(#tab)"/>{grain}
<g transform="translate(180,395) rotate(-6)">
<rect x="-110" y="-70" width="220" height="140" rx="14" fill="#3b2412" opacity=".35" transform="translate(6,10)"/>
<rect x="-110" y="-70" width="220" height="140" rx="14" fill="url(#board)"/>
{''.join(f'<path d="M-104,{-60+i*12} C-40,{-64+i*12} 40,{-56+i*12} 104,{-60+i*12}" stroke="#a87442" stroke-width=".9" fill="none" opacity=".5"/>' for i in range(11))}
<text x="0" y="-8" text-anchor="middle" font-family="Georgia,serif" font-size="30" font-weight="700" fill="#4a2a10" opacity=".85">HOLZWERK</text>
<text x="0" y="22" text-anchor="middle" font-family="Georgia,serif" font-size="15" letter-spacing="6" fill="#4a2a10" opacity=".75">NORD · 2026</text>
</g>
<g opacity=".9"><rect x="262" y="300" width="34" height="120" rx="6" fill="#2f5d50"/><rect x="268" y="288" width="22" height="16" rx="3" fill="#ddd"/></g>
</svg>'''


def _person(x=180, y=640, s=1.0, sweater="#2d3e50", skin1="#f1c7a5", skin2="#d9a07c", hair="#3b2a20"):
    return f'''<g transform="translate({x},{y}) scale({s})">
<defs><radialGradient id="face" cx=".42" cy=".38" r=".7"><stop offset="0" stop-color="{skin1}"/><stop offset="1" stop-color="{skin2}"/></radialGradient></defs>
<path d="M-150,0 C-150,-120 -90,-165 0,-170 C90,-165 150,-120 150,0Z" fill="{sweater}"/>
<path d="M-34,-168 L-30,-215 L30,-215 L34,-168 C20,-150 -20,-150 -34,-168Z" fill="{skin2}"/>
<ellipse cx="0" cy="-285" rx="72" ry="88" fill="url(#face)"/>
<path d="M-78,-285 C-84,-370 -30,-395 10,-392 C70,-390 92,-340 78,-280 C70,-320 40,-345 0,-340 C-40,-336 -66,-320 -78,-285Z" fill="{hair}"/>
<ellipse cx="-26" cy="-290" rx="7" ry="5" fill="#3a2a22"/><ellipse cx="26" cy="-290" rx="7" ry="5" fill="#3a2a22"/>
<path d="M-24,-244 C-10,-232 10,-232 24,-244" stroke="#9c5a4a" stroke-width="4" fill="none" stroke-linecap="round"/>
<path d="M-38,-312 C-28,-318 -16,-318 -10,-314 M10,-314 C16,-318 28,-318 38,-312" stroke="{hair}" stroke-width="4" fill="none" stroke-linecap="round"/>
</g>'''


def portrait(light=False):
    bg = ('#f3f1ec', '#d8d4cc') if light else ('#2b3442', '#151a22')
    bok = '' if light else ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#8fb4ff" opacity="{o}"/>' for x, y, r, o in
                                   [(50, 120, 30, .12), (300, 80, 24, .14), (320, 260, 36, .1), (30, 300, 20, .1)])
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs><linearGradient id="bgp" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{bg[0]}"/><stop offset="1" stop-color="{bg[1]}"/></linearGradient>
<filter id="b"><feGaussianBlur stdDeviation="8"/></filter></defs>
<rect width="360" height="640" fill="url(#bgp)"/><g filter="url(#b)">{bok}</g>
{_person(180, 640, 1.05, "#3a4a5e", "#f6d2b4" if light else "#e9bd99", "#dfa781" if light else "#c58c68")}
</svg>'''


def interview():
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs><linearGradient id="r" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#3d3127"/><stop offset="1" stop-color="#1f1a16"/></linearGradient>
<filter id="b"><feGaussianBlur stdDeviation="5"/></filter></defs>
<rect width="360" height="640" fill="url(#r)"/>
<g filter="url(#b)"><rect x="210" y="60" width="130" height="300" fill="#e8d9b8" opacity=".55"/>
<rect x="272" y="60" width="6" height="300" fill="#3d3127" opacity=".6"/><rect x="210" y="200" width="130" height="5" fill="#3d3127" opacity=".6"/>
<circle cx="60" cy="300" r="46" fill="#3f6b3a" opacity=".8"/><circle cx="90" cy="260" r="36" fill="#4f8048" opacity=".7"/><rect x="50" y="330" width="40" height="60" fill="#6b4a2c"/>
<rect x="0" y="470" width="360" height="170" fill="#6d4b2e" opacity=".8"/></g>
{_person(170, 660, 1.0, "#6b2f2f", "#f0c8a6", "#d39b78", "#7a4b2a")}
</svg>'''


def flower():
    petals = ''.join(f'<ellipse cx="0" cy="-58" rx="26" ry="60" fill="url(#pet)" transform="rotate({a})"/>' for a in range(0, 360, 30))
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<defs><radialGradient id="g" cx=".5" cy=".4" r=".8"><stop offset="0" stop-color="#5f8f4a"/><stop offset="1" stop-color="#1c2e1a"/></radialGradient>
<linearGradient id="pet" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffd7e6"/><stop offset="1" stop-color="#e86a9a"/></linearGradient>
<filter id="b"><feGaussianBlur stdDeviation="9"/></filter></defs>
<rect width="360" height="640" fill="url(#g)"/>
<g filter="url(#b)">{''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#d8f0a0" opacity="{o}"/>' for x, y, r, o in [(40,80,28,.2),(320,140,40,.15),(60,520,34,.12),(300,560,30,.15),(200,40,20,.2)])}</g>
<path d="M180,330 C176,420 186,520 170,640" stroke="#3d6b2f" stroke-width="10" fill="none"/>
<g transform="translate(180,300)">{petals}<circle r="30" fill="#f2b632"/><circle r="18" fill="#d98b1c"/></g>
</svg>'''


def peaking_overlay():
    """Rote Kanten an den scharfen Blütenblättern (Fokus-Peaking)."""
    petals = ''.join(f'<ellipse cx="0" cy="-58" rx="26" ry="60" fill="none" stroke="#ff2222" stroke-width="2.2" stroke-dasharray="7 4" transform="rotate({a})"/>' for a in range(0, 360, 30))
    return f'''<svg viewBox="0 0 360 640" preserveAspectRatio="xMidYMid slice" xmlns="http://www.w3.org/2000/svg">
<g transform="translate(180,300)">{petals}<circle r="30" fill="none" stroke="#ff2222" stroke-width="2.4"/></g></svg>'''
