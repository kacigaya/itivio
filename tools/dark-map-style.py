#!/usr/bin/env python3
"""Builds the dark map style from OpenFreeMap Liberty.

The OpenFreeMap dark style has almost no contrast and no stop or place icons. This script keeps
the Liberty layers, so the dark map shows the same content as the light map, and gives each layer
a dark color. Run it from the repository root after OpenFreeMap changes Liberty:

    python3 tools/dark-map-style.py

A layer or color property without a rule stops the script. Add a rule for it.
"""
import json
import re
import sys
import urllib.request

SOURCE = "https://tiles.openfreemap.org/styles/liberty"
OUTPUT = "app/src/main/assets/map/dark.json"

LAND = "#1e2023"
HALO = "rgba(18,20,22,0.85)"
CASING = "#131517"
LABEL = "#b9bec6"

# "<layer id>:<paint property>" pattern -> color. The first match wins.
RULES = [
    (r"background:background-color", LAND),
    (r".*:text-halo-color", HALO),
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
    (r"\w+_casing:line-color", CASING),
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
    (r"(poi_\w+|airport):text-color", LABEL),
    (r"highway-name-\w+:text-color", "#a4a9b1"),
    (r"label_(other|state):text-color", "#c4c8ce"),
    (r"label_\w+:text-color", "#e6e8eb"),
]


def color_for(layer_id: str, prop: str) -> str:
    key = f"{layer_id}:{prop}"
    for pattern, color in RULES:
        if re.fullmatch(pattern, key):
            return color
    sys.exit(f"No rule for {key}")


def main() -> None:
    request = urllib.request.Request(SOURCE, headers={"User-Agent": "itivio-style-build"})
    with urllib.request.urlopen(request) as response:
        style = json.load(response)

    # The shaded relief raster is light. It has no dark variant.
    style["sources"].pop("ne2_shaded")
    layers = [layer for layer in style["layers"] if layer.get("source") != "ne2_shaded"]
    for layer in layers:
        paint = layer.setdefault("paint", {})
        # The sprite patterns are light. A flat fill replaces them.
        if paint.pop("fill-pattern", None) is not None:
            paint["fill-color"] = None
        for prop in [name for name in paint if name.endswith("color")]:
            paint[prop] = color_for(layer["id"], prop)
        # Liberty relies on the light land for labels without a halo.
        if "text-color" in paint and "text-halo-color" not in paint:
            paint["text-halo-color"] = HALO
            paint["text-halo-width"] = 1
        if not paint:
            del layer["paint"]
    style["layers"] = layers
    style["name"] = "itivio dark"

    with open(OUTPUT, "w", encoding="utf-8") as output:
        json.dump(style, output, ensure_ascii=False, separators=(",", ":"))
        output.write("\n")


if __name__ == "__main__":
    main()
