#!/usr/bin/env python3
"""Build the offline boarding tables from IDFM CSV exports.

Run from the repository root: python3 tools/boarding-positions.py

Sources: IDFM "Positionnement dans la rame" (RATP), "Accès", "Arrêts",
and "Zones d'arrêts".
License: Licence Ouverte 2.0, Île-de-France Mobilités.

idfm.tsv: platform, line, target (transfer stop ID or exit:<access ID>), part
(F/M/R), lift flag (L or empty).
exits.tsv: access ID, exit number (may be empty), name, latitude, longitude.
Only referenced exits with coordinates enter exits.tsv.
stations.tsv: transfer platform, stop area, parent station.
"""
import csv
import io
import math
import urllib.request

BASE = "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/"
SOURCE = BASE + "positionnement-dans-la-rame/exports/csv?delimiter=%3B"
EXITS_SOURCE = BASE + "acces/exports/csv?delimiter=%3B"
OUTPUT = "app/src/main/assets/boarding/idfm.tsv"
EXITS_OUTPUT = "app/src/main/assets/boarding/exits.tsv"
STOPS_SOURCE = BASE + "arrets/exports/csv?delimiter=%3B&select=arrid,zdaid"
AREAS_SOURCE = BASE + "zones-d-arrets/exports/csv?delimiter=%3B&select=zdaid,zdcid"
STATIONS_OUTPUT = "app/src/main/assets/boarding/stations.tsv"
PARTS = {"Avant": "F", "Milieu": "M", "Arrière": "R"}


def download(url: str) -> csv.DictReader:
    request = urllib.request.Request(url, headers={"User-Agent": "itivio-tools"})
    with urllib.request.urlopen(request, timeout=120) as response:
        text = response.read().decode("utf-8-sig")
    return csv.DictReader(io.StringIO(text), delimiter=";")


def coordinates(row: dict[str, str]) -> tuple[float, float] | None:
    point = row.get("accgeopoint", "").strip()
    fields = point.split(",") if point else (row.get("lat", ""), row.get("lon", ""))
    if len(fields) != 2:
        return None
    try:
        lat, lon = map(float, fields)
    except ValueError:
        return None
    if not (math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180):
        return None
    return lat, lon


def write_table(path: str, rows: list[tuple[str, ...]]) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        for row in sorted(rows):
            out.write("\t".join(" ".join(field.split()) for field in row) + "\n")


def platform_stations(platforms: set[str]) -> list[tuple[str, ...]]:
    areas = {row["zdaid"]: row["zdcid"] for row in download(AREAS_SOURCE) if row["zdcid"]}
    return sorted({
        (row["arrid"], row["zdaid"], areas[row["zdaid"]])
        for row in download(STOPS_SOURCE)
        if row["arrid"] in platforms and row["zdaid"] in areas
    })


def main() -> None:
    rows = set()
    referenced = set()
    skipped = 0
    for row in download(SOURCE):
        part = PARTS.get(row["position_average"])
        if (part is None or not row["from_id"] or not row["line_id"] or not row["to_id"]
                or row["to_type"] not in {"stop_point", "access_point"}):
            # Some rows give a car number instead of a part of the train.
            skipped += 1
            continue
        if row["to_type"] == "access_point":
            referenced.add(row["to_id"])
            target = "exit:" + row["to_id"]
        else:
            target = row["to_id"]
        lift = "L" if row["equipment_type"] == "Ascenseur" else ""
        rows.add((row["from_id"], row["line_id"], target, part, lift))

    exits = {}
    for row in download(EXITS_SOURCE):
        access_id = row["accid"]
        if access_id not in referenced:
            continue
        point = coordinates(row)
        if point is None:
            continue
        lat, lon = point
        exits[access_id] = (access_id, row["accshortname"], row["accname"], f"{lat:.6f}", f"{lon:.6f}")

    platforms = {row[2] for row in rows if not row[2].startswith("exit:")}
    stations = platform_stations(platforms)
    write_table(OUTPUT, list(rows))
    write_table(EXITS_OUTPUT, list(exits.values()))
    write_table(STATIONS_OUTPUT, stations)
    print(f"{len(rows)} rows written to {OUTPUT}, {skipped} skipped")
    print(f"{len(exits)} exits written to {EXITS_OUTPUT}")
    print(f"{len(stations)} platforms written to {STATIONS_OUTPUT}")
    print(f"{len(referenced - exits.keys())} referenced exits have no coordinates (including missing access records)")


if __name__ == "__main__":
    main()
