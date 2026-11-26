#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["uharfbuzz==0.57.0"]
# ///
"""Builds the README images in assets/readme/light/ and assets/readme/dark/.

GitHub shows README SVGs as images, and an SVG image cannot load a font. So the script shapes
each text with HarfBuzz and writes it as outlines of the Google Sans Flex font of the app
(round terminals, as in Theme.kt). Colors come from the light and dark schemes in Theme.kt.
Icons are the Material Symbols vector drawables of the app.

Run it from the repository root after a change to the texts, colors, or font:

    uv run tools/readme-art.py

A text that does not fit its box stops the script. Shorten the text or change the layout.
"""
import re
from pathlib import Path

import uharfbuzz as hb

FONT = Path("app/src/main/res/font/google_sans_flex.ttf")
DRAWABLES = Path("app/src/main/res/drawable")
LOGO = Path("assets/logo.svg")
OUT = Path("assets/readme")

THEMES = {
    "light": {
        "banner": "#0C6B59",
        "primary": "#0C6B59", "on_primary": "#FFFFFF",
        "primary_container": "#A1F2DB", "on_primary_container": "#005142",
        "secondary_container": "#CDE9DE", "on_secondary_container": "#334C44",
        "surface_container_low": "#EFF5F1",
        "on_surface": "#171D1B", "on_surface_variant": "#3F4945", "outline_variant": "#BFC9C4",
    },
    "dark": {
        "banner": "#005142",
        "primary": "#85D6BF", "on_primary": "#00382D",
        "primary_container": "#005142", "on_primary_container": "#A1F2DB",
        "secondary_container": "#334C44", "on_secondary_container": "#CDE9DE",
        "surface_container_low": "#171D1B",
        "on_surface": "#DEE4E0", "on_surface_variant": "#BFC9C4", "outline_variant": "#3F4945",
    },
}

TAGLINE = "Public transport for Android. Your data stays on your phone."
HEADINGS = {
    "screenshots": "Screenshots", "features": "Features", "install": "Install",
    "build": "Build from source", "privacy": "Privacy", "faq": "FAQ", "credits": "Credits",
}
BUTTONS = {"download": ("Download APK", True), "website": ("Website", False), "privacy": ("Privacy policy", False)}
BADGES = {
    "adfree": "No ads", "private": "No analytics", "android": "Android 8+",
    "m3e": "Material 3 Expressive", "gpl": "GPL-3.0-or-later",
}
FEATURES = [
    ("directions", "Journey planning", "All modes. Leave now, leave at a time, or arrive by a time."),
    ("subway", "Where to board", "The part of the train nearest to your transfer or exit, in Paris."),
    ("schedule", "Live times", "Countdowns and delays where the transit agency publishes them."),
    ("directions_walk", "Walking directions", "The next turn, the distance to it, and the arrival time."),
    ("warning", "Disruptions", "Service alerts for your saved stops and for the stops near you."),
    ("cloud_off", "No account", "Saved places, searches, and settings stay on your device."),
]

_blob = hb.Blob.from_file_path(str(FONT))
_face = hb.Face(_blob)
_fonts: dict[tuple[int, int], hb.Font] = {}
_glyphs: dict[tuple[int, float, int], str] = {}
UPEM = _face.upem


def num(v: float) -> str:
    s = f"{v:.1f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def font(wght: int, size: float) -> hb.Font:
    key = (wght, min(144, max(6, round(size))))
    if key not in _fonts:
        f = hb.Font(_face)
        f.set_variations({"wght": wght, "ROND": 100, "opsz": key[1], "wdth": 100, "slnt": 0})
        _fonts[key] = f
    return _fonts[key]


def shape(text: str, size: float, wght: int):
    f = font(wght, size)
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(f, buf, {"kern": True, "liga": True})
    return f, buf.glyph_infos, buf.glyph_positions


def width(text: str, size: float, wght: int) -> float:
    _, _, pos = shape(text, size, wght)
    return sum(p.x_advance for p in pos) * size / UPEM


class PathPen:
    def __init__(self, ox: float, oy: float, s: float):
        self.ox, self.oy, self.s, self.d = ox, oy, s, []

    def _p(self, pt) -> str:
        return f"{num(self.ox + pt[0] * self.s)} {num(self.oy - pt[1] * self.s)}"

    def moveTo(self, p):
        self.d.append("M" + self._p(p))

    def lineTo(self, p):
        self.d.append("L" + self._p(p))

    def qCurveTo(self, *pts):
        self.d.append("Q" + " ".join(self._p(p) for p in pts))

    def curveTo(self, *pts):
        self.d.append("C" + " ".join(self._p(p) for p in pts))

    def closePath(self):
        self.d.append("Z")

    endPath = closePath


def text(s: str, x: float, baseline: float, size: float, wght: int, fill: str, anchor: str = "start") -> str:
    f, infos, pos = shape(s, size, wght)
    scale = size / UPEM
    if anchor == "middle":
        x -= sum(p.x_advance for p in pos) * scale / 2
    pen_x, uses = x, []
    for info, p in zip(infos, pos):
        key = (wght, size, info.codepoint)
        if key not in _glyphs:
            pen = PathPen(0, 0, scale)
            f.draw_glyph_with_pen(info.codepoint, pen)
            _glyphs[key] = "".join(pen.d)
        if _glyphs[key]:
            gid = f"g{list(_glyphs).index(key)}"
            uses.append(f'<use href="#{gid}" x="{num(pen_x + p.x_offset * scale)}" y="{num(baseline - p.y_offset * scale)}"/>')
        pen_x += p.x_advance * scale
    return f'<g fill="{fill}">{"".join(uses)}</g>'


def wrap(s: str, size: float, wght: int, max_w: float) -> list[str]:
    lines = greedy(s, size, wght, max_w)
    for ln in lines:
        if width(ln, size, wght) > max_w:
            raise SystemExit(f"Text does not fit in {max_w}px: {ln!r}")
    count = len(lines)
    lo, hi = 0.0, max_w
    while hi - lo > 1:
        mid = (lo + hi) / 2
        lo, hi = (lo, mid) if len(greedy(s, size, wght, mid)) <= count else (mid, hi)
    return greedy(s, size, wght, hi)


def greedy(s: str, size: float, wght: int, max_w: float) -> list[str]:
    lines, line = [], ""
    for word in s.split():
        trial = f"{line} {word}".strip()
        if line and width(trial, size, wght) > max_w:
            lines.append(line)
            line = word
        else:
            line = trial
    lines.append(line)
    return lines


def icon(name: str, x: float, y: float, size: float, fill: str) -> str:
    xml = (DRAWABLES / f"ic_{name}.xml").read_text()
    vw = float(re.search(r'viewportWidth="([\d.]+)"', xml).group(1))
    paths = "".join(f'<path d="{d}"/>' for d in re.findall(r'pathData="([^"]+)"', xml))
    return f'<g fill="{fill}" transform="translate({num(x)} {num(y)}) scale({size / vw:.5f})">{paths}</g>'


def logo(x: float, y: float, size: float) -> str:
    svg = LOGO.read_text()
    body = re.search(r"<defs>.*</g>", svg, re.S).group(0)
    body = re.sub(r">\s+<", "><", body).replace('id="mask"', 'id="logo"').replace("url(#mask)", "url(#logo)")
    s = size / 72
    return f'<g transform="translate({num(x - 18 * s)} {num(y - 18 * s)}) scale({s:.5f})">{body}</g>'


def svg(w: float, h: float, label: str, body: str) -> str:
    w, h = round(w), round(h)
    defs = "".join(f'<path id="g{i}" d="{d}"/>' for i, d in enumerate(_glyphs.values()) if d)
    _glyphs.clear()
    body = f"<defs>{defs}</defs>{body}" if defs else body
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
            f'role="img" aria-label="{label}">{body}</svg>\n')


def banner(c: dict, mobile: bool) -> str:
    if mobile:
        w, logo_size, word, tag, pad = 640, 128, 76, 30, 48
        lines = wrap(TAGLINE, tag, 400, w - 2 * pad)
        y = pad
        parts = [logo((w - logo_size) / 2, y, logo_size)]
        y += logo_size + 24 + word * 0.74
        parts.append(text("itivio", w / 2, y, word, 800, "#FFFFFF", "middle"))
        for i, ln in enumerate(lines):
            parts.append(text(ln, w / 2, y + 52 + i * 42, tag, 400, "#FFFFFF", "middle"))
        h = y + 52 + (len(lines) - 1) * 42 + pad
    else:
        w, h, logo_size, word, tag, pad = 1200, 256, 144, 84, 28, 56
        tx = pad + logo_size + 36
        if tx + width(TAGLINE, tag, 400) > w - pad:
            raise SystemExit("The tagline does not fit in the banner")
        parts = [logo(pad, (h - logo_size) / 2, logo_size),
                 text("itivio", tx, 134, word, 800, "#FFFFFF"),
                 text(TAGLINE, tx + 2, 184, tag, 400, "#FFFFFF")]
    return svg(w, h, "itivio", f'<rect width="{w}" height="{round(h)}" rx="32" fill="{c["banner"]}"/>' + "".join(parts))


def heading(c: dict, label: str) -> str:
    size = 32
    w = width(label, size, 700)
    return svg(w + 2, size * 1.3, label, text(label, 0, size, size, 700, c["on_surface"]))


def pill(label: str, height: float, size: float, wght: int, pad: float, fill: str, ink: str, stroke: str | None = None) -> str:
    w = round(width(label, size, wght) + 2 * pad)
    baseline = height / 2 + size * 0.716 / 2
    edge = f' stroke="{stroke}" stroke-width="1.5"' if stroke else ""
    inset = 0.75 if stroke else 0
    shape_ = (f'<rect x="{inset}" y="{inset}" width="{num(w - 2 * inset)}" height="{num(height - 2 * inset)}" '
              f'rx="{num(height / 2 - inset)}" fill="{fill}"{edge}/>')
    return svg(w, height, label, shape_ + text(label, pad, baseline, size, wght, ink))


def features(c: dict, mobile: bool) -> str:
    if mobile:
        cols, w, gap, pad, chip, ico, ts, bs, lh = 1, 640, 20, 36, 64, 34, 36, 28, 40
    else:
        cols, w, gap, pad, chip, ico, ts, bs, lh = 2, 1200, 24, 32, 56, 30, 28, 22, 32
    cw = (w - gap * (cols - 1)) / cols
    cards = [(name, title, wrap(body, bs, 400, cw - 2 * pad)) for name, title, body in FEATURES]
    rows = [cards[i:i + cols] for i in range(0, len(cards), cols)]
    title_y = pad + chip + 20 + ts * 0.95
    body_y = title_y + lh * 0.45 + bs
    parts, y = [], 0.0
    for row in rows:
        ch = body_y + (max(len(b) for _, _, b in row) - 1) * lh + pad - bs * 0.1
        for i, (name, title, body) in enumerate(row):
            x = i * (cw + gap)
            parts.append(f'<rect x="{num(x)}" y="{num(y)}" width="{num(cw)}" height="{num(ch)}" rx="28" fill="{c["surface_container_low"]}"/>')
            parts.append(f'<circle cx="{num(x + pad + chip / 2)}" cy="{num(y + pad + chip / 2)}" r="{chip / 2}" fill="{c["primary_container"]}"/>')
            parts.append(icon(name, x + pad + (chip - ico) / 2, y + pad + (chip - ico) / 2, ico, c["on_primary_container"]))
            parts.append(text(title, x + pad, y + title_y, ts, 700, c["on_surface"]))
            for j, ln in enumerate(body):
                parts.append(text(ln, x + pad, y + body_y + j * lh, bs, 400, c["on_surface_variant"]))
        y += ch + gap
    label = "Features: " + ", ".join(t for _, t, _ in FEATURES)
    return svg(w, y - gap, label, "".join(parts))


def main():
    for theme, c in THEMES.items():
        out = OUT / theme
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.svg"):
            old.unlink()
        files = {"banner.svg": banner(c, False), "banner-m.svg": banner(c, True),
                 "features.svg": features(c, False), "features-m.svg": features(c, True)}
        for key, label in HEADINGS.items():
            files[f"h-{key}.svg"] = heading(c, label)
        for key, (label, filled) in BUTTONS.items():
            fill, ink = (c["primary"], c["on_primary"]) if filled else (c["secondary_container"], c["on_secondary_container"])
            files[f"btn-{key}.svg"] = pill(label, 48, 17, 600, 24, fill, ink)
        for key, label in BADGES.items():
            files[f"badge-{key}.svg"] = pill(label, 28, 14, 500, 12, "none", c["on_surface_variant"], c["outline_variant"])
        for name, content in files.items():
            (out / name).write_text(content)
        print(f"{out}: {len(files)} files")


if __name__ == "__main__":
    main()
