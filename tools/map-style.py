#!/usr/bin/env python3
"""Builds the light and dark map styles from OpenFreeMap Liberty.

Both styles keep the Liberty layers, so the two themes show the same roads, stops, and places.
Each layer gets a color from the rules of its theme:

- light: calm neutral land and muted water in the hues of the app theme, so the line colors of
  routes and stops are the strongest colors on the map.
- dark: the OpenFreeMap dark style has almost no contrast and no stop or place icons.

Run it from the repository root after OpenFreeMap changes Liberty:

    python3 tools/map-style.py

A layer or color property without a rule stops the script. Add a rule for it.
"""
import json
import re
import sys
import urllib.request

SOURCE = "https://tiles.openfreemap.org/styles/liberty"

LIGHT_LAND = "#eef1ee"
LIGHT_LABEL = "#5f6763"
LIGHT = [
    (r"background:background-color", LIGHT_LAND),
    (r"highway-name-path:text-halo-color", LIGHT_LAND),
    (r".*:text-halo-color", "rgba(255,255,255,0.85)"),
    # Land use
    (r"park:fill-color", "#d7e8d5"),
    (r"park:fill-outline-color|park_outline:line-color", "#c4dcc3"),
    (r"landuse_residential:fill-color", "#e8ebe8"),
    (r"landcover_wood:fill-color", "#cbe2c8"),
    (r"landcover_grass:fill-color", "#d3e7ce"),
    (r"landcover_ice:fill-color", "#e8f0f0"),
    (r"landcover_wetland:fill-color", "#d5e7e2"),
    (r"landcover_sand:fill-color", "#efe9d3"),
    (r"landuse_(pitch|track):fill-color", "#d8e6ce"),
    (r"landuse_cemetery:fill-color", "#dae4d4"),
    (r"landuse_hospital:fill-color", "#f2e3e3"),
    (r"landuse_school:fill-color", "#ece9da"),
    (r"waterway_\w+:line-color", "#a9cadc"),
    (r"water:fill-color", "#b9d5e4"),
    (r"aeroway_fill:fill-color", "#e3e6e2"),
    (r"aeroway_\w+:line-color", "#f7f8f6"),
    (r"building:fill-color", "#e0e3df"),
    (r"building:fill-outline-color", "#d2d6d1"),
    (r"building-3d:fill-extrusion-color", "#e0e3df"),
    (r"boundary_3:line-color", "#b3b9b5"),
    (r"boundary_\w+:line-color", "#7d8480"),
    # Roads: white fills on light grey casings. Major roads keep a pale warm tint for hierarchy.
    (r"\w+_motorway(_link)?_casing:line-color", "#d9b886"),
    (r"\w+_(link|secondary_tertiary|trunk_primary)_casing:line-color", "#e0d3b0"),
    (r"\w+_casing:line-color", "#d3d8d4"),
    (r"\w+_motorway(_link)?:line-color", "#f5dcae"),
    (r"\w+_(link|secondary_tertiary|trunk_primary):line-color", "#fbf3dc"),
    (r"\w+_path_pedestrian:line-color", "#ffffff"),
    (r"road_area_pattern:fill-color", "#f7f8f6"),
    (r"\w+_(service_track|minor|street):line-color", "#ffffff"),
    (r"\w+_rail(_hatching)?:line-color", "#a7adaa"),
    # Labels
    (r"waterway_line_label:text-color", "#5a87a2"),
    (r"water\w+_label:text-color", "#466b84"),
    (r"poi_transit:text-color", "#426277"),
    (r"(poi_\w+|airport):text-color", LIGHT_LABEL),
    (r"highway-name-path:text-color", "#8a918d"),
    (r"highway-name-\w+:text-color", LIGHT_LABEL),
    (r"label_(other|state):text-color", "#4f5753"),
    (r"label_\w+:text-color", "#1f2522"),
]

DARK_LAND = "#1e2023"
DARK_CASING = "#131517"
DARK_LABEL = "#b9bec6"
DARK = [
    (r"background:background-color", DARK_LAND),
    (r".*:text-halo-color", "rgba(18,20,22,0.85)"),
    # Land use
    (r"park:fill-color", "#1d3325"),
    (r"park:fill-outline-color|park_outline:line-color", "#274531"),
    (r"landuse_residential:fill-color", "#212427"),
    (r"landcover_wood:fill-color", "#2c5a3a"),
    (r"landcover_grass:fill-color", "#2f5c3c"),
    (r"landcover_ice:fill-color", "#3a4245"),
    (r"landcover_wetland:fill-color", "#1f3a3a"),
    (r"landcover_sand:fill-color", "#37331f"),
    (r"landuse_(pitch|track):fill-color", "#27402f"),
    (r"landuse_cemetery:fill-color", "#26332a"),
    (r"landuse_hospital:fill-color", "#35262d"),
    (r"landuse_school:fill-color", "#2e2f24"),
    (r"waterway_\w+:line-color", "#1d4270"),
    (r"water:fill-color", "#16314f"),
    (r"aeroway_fill:fill-color", "#2a2d31"),
    (r"aeroway_\w+:line-color", "#3a3e44"),
    (r"building:fill-color", "#26292d"),
    (r"building:fill-outline-color", "#31353a"),
    # The extrusion light makes this color brighter on screen.
    (r"building-3d:fill-extrusion-color", "#202326"),
    (r"boundary_\w+:line-color", "#6f757e"),
    # Roads: a casing darker than the land and a fill lighter than the land.
    (r"\w+_motorway(_link)?_casing:line-color", "#2b2416"),
    (r"\w+_casing:line-color", DARK_CASING),
    (r"\w+_motorway(_link)?:line-color", "#7a643c"),
    (r"\w+_path_pedestrian:line-color", "#4d535b"),
    (r"road_area_pattern:fill-color", "#2b2e33"),
    (r"\w+_service_track:line-color", "#3a3f46"),
    (r"\w+_(minor|street):line-color", "#474d56"),
    (r"\w+_(link|secondary_tertiary|trunk_primary):line-color", "#636a76"),
    (r"\w+_rail(_hatching)?:line-color", "#6a7079"),
    # Labels
    (r"water\w+_label:text-color", "#7fb2ea"),
    (r"poi_transit:text-color", "#8fbcf5"),
    (r"(poi_\w+|airport):text-color", DARK_LABEL),
    (r"highway-name-\w+:text-color", "#a4a9b1"),
    (r"label_(other|state):text-color", "#c4c8ce"),
    (r"label_\w+:text-color", "#e6e8eb"),
]

THEMES = [
    ("light", LIGHT, "app/src/main/assets/map/light.json"),
    ("dark", DARK, "app/src/main/assets/map/dark.json"),
]


def color_for(rules: list, layer_id: str, prop: str) -> str:
    key = f"{layer_id}:{prop}"
    for pattern, color in rules:
        if re.fullmatch(pattern, key):
            return color
    sys.exit(f"No rule for {key}")


def themed(source: dict, name: str, rules: list) -> dict:
    style = json.loads(json.dumps(source))
    # The shaded relief raster is busy at low zoom and has no dark variant. Both themes drop it.
    style["sources"].pop("ne2_shaded")
    layers = [layer for layer in style["layers"] if layer.get("source") != "ne2_shaded"]
    for layer in layers:
        paint = layer.setdefault("paint", {})
        # The sprite patterns do not follow the theme. A flat fill replaces them.
        if paint.pop("fill-pattern", None) is not None:
            paint["fill-color"] = None
        for prop in [name for name in paint if name.endswith("color")]:
            paint[prop] = color_for(rules, layer["id"], prop)
        # Some Liberty labels have no halo. Each label gets one, so it reads on every fill.
        if "text-color" in paint and "text-halo-color" not in paint:
            paint["text-halo-color"] = color_for(rules, layer["id"], "text-halo-color")
            paint["text-halo-width"] = 1
        if not paint:
            del layer["paint"]
    style["layers"] = layers
    style["name"] = f"itivio {name}"
    return style


def main() -> None:
    request = urllib.request.Request(SOURCE, headers={"User-Agent": "itivio-style-build"})
    with urllib.request.urlopen(request) as response:
        source = json.load(response)
    for name, rules, output_path in THEMES:
        with open(output_path, "w", encoding="utf-8") as output:
            json.dump(themed(source, name, rules), output, ensure_ascii=False, separators=(",", ":"))
            output.write("\n")


if __name__ == "__main__":
    main()
