#!/usr/bin/env python3
"""Builds the boarding position table from the IDFM "Positionnement dans la rame" dataset.

The RATP gives, for each metro and RER platform, the part of the train that stops nearest to
each exit and each transfer. The app reads a small tab-separated copy from its assets, so it
sends no request for this data. Run it from the repository root when the dataset changes:

    python3 tools/boarding-positions.py

Source: https://prim.iledefrance-mobilites.fr/fr/jeux-de-donnees/positionnement-dans-la-rame
License: Licence Ouverte v2.0 (Etalab). Producer: RATP.

Output columns: platform ID, line ID, target (a stop ID for a transfer, "exit" for an exit),
part of the train (F, M, or R), and "L" when the path to the target has a lift.
"""
import csv
import io
import urllib.request

SOURCE = (
    "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/"
    "positionnement-dans-la-rame/exports/csv?delimiter=%3B"
)
OUTPUT = "app/src/main/assets/boarding/idfm.tsv"
PARTS = {"Avant": "F", "Milieu": "M", "Arrière": "R"}


def main() -> None:
    request = urllib.request.Request(SOURCE, headers={"User-Agent": "itivio-tools"})
    with urllib.request.urlopen(request, timeout=120) as response:
        text = response.read().decode("utf-8-sig")
    rows = set()
    skipped = 0
    for row in csv.DictReader(io.StringIO(text), delimiter=";"):
        part = PARTS.get(row["position_average"])
        if part is None or not row["from_id"] or not row["line_id"]:
            # A few rows give a car number in place of a part of the train.
            skipped += 1
            continue
        target = row["to_id"] if row["to_type"] == "stop_point" else "exit"
        lift = "L" if row["equipment_type"] == "Ascenseur" else ""
        rows.add((row["from_id"], row["line_id"], target, part, lift))
    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as out:
        for row in sorted(rows):
            out.write("\t".join(row) + "\n")
    print(f"{len(rows)} rows written to {OUTPUT}, {skipped} skipped")


if __name__ == "__main__":
    main()
