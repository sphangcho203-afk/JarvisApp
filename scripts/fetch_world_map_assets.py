#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "helix-ui" / "public" / "geodata"
GENERATED_MODULE = ROOT / "helix-ui" / "src" / "generatedWorldMapAsset.ts"

# Natural Earth 50m is the global operational LOD. It preserves all country and
# territory geometry at full-Earth scale without forcing a phone to transform
# the much larger 10m dataset every animation frame. Regional 10m and vector
# tile layers are loaded separately when FRIDAY enters deep zoom.
ASSETS = {
    "countries-50m.geojson": [
        "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson",
        "https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_admin_0_countries.geojson",
    ],
    "places-50m.geojson": [
        "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_populated_places_simple.geojson",
        "https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@master/geojson/ne_50m_populated_places_simple.geojson",
    ],
}

MINIMUM_FEATURES = {
    "countries-50m.geojson": 170,
    "places-50m.geojson": 500,
}


def download_json(urls: list[str]) -> dict[str, Any]:
    errors: list[str] = []
    for url in urls:
        try:
            request = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "FRIDAY-World-Map-Asset-Builder/0.17",
                    "Accept": "application/geo+json,application/json,*/*",
                },
            )
            with urllib.request.urlopen(request, timeout=45) as response:
                payload = response.read()
            value = json.loads(payload.decode("utf-8"))
            if not isinstance(value, dict) or not isinstance(value.get("features"), list):
                raise ValueError("GeoJSON FeatureCollection expected")
            return value
        except (urllib.error.URLError, TimeoutError, ValueError, json.JSONDecodeError) as error:
            errors.append(f"{url}: {error}")
    raise RuntimeError("Unable to retrieve geospatial asset:\n" + "\n".join(errors))


def normalize_country_collection(collection: dict[str, Any]) -> dict[str, Any]:
    features: list[dict[str, Any]] = []
    for raw in collection.get("features", []):
        if not isinstance(raw, dict):
            continue
        geometry = raw.get("geometry")
        properties = raw.get("properties")
        if not isinstance(geometry, dict) or geometry.get("type") not in {"Polygon", "MultiPolygon"}:
            continue
        if not isinstance(properties, dict):
            properties = {}
        features.append(
            {
                "type": "Feature",
                "properties": {
                    "ADMIN": properties.get("ADMIN"),
                    "NAME": properties.get("NAME"),
                    "NAME_LONG": properties.get("NAME_LONG"),
                    "ISO_A2": properties.get("ISO_A2"),
                    "ISO_A3": properties.get("ISO_A3"),
                    "CONTINENT": properties.get("CONTINENT"),
                    "SUBREGION": properties.get("SUBREGION"),
                    "POP_EST": properties.get("POP_EST"),
                    "GDP_MD": properties.get("GDP_MD"),
                    "LABEL_X": properties.get("LABEL_X"),
                    "LABEL_Y": properties.get("LABEL_Y"),
                    "LABELRANK": properties.get("LABELRANK"),
                    "MIN_ZOOM": properties.get("MIN_ZOOM"),
                },
                "geometry": geometry,
            }
        )
    return {"type": "FeatureCollection", "features": features}


def normalize_place_collection(collection: dict[str, Any]) -> dict[str, Any]:
    features: list[dict[str, Any]] = []
    for raw in collection.get("features", []):
        if not isinstance(raw, dict):
            continue
        geometry = raw.get("geometry")
        properties = raw.get("properties")
        if not isinstance(geometry, dict) or geometry.get("type") != "Point":
            continue
        if not isinstance(properties, dict):
            properties = {}
        features.append(
            {
                "type": "Feature",
                "properties": {
                    "NAMEPAR": properties.get("NAMEPAR"),
                    "NAME": properties.get("NAME"),
                    "ADM0NAME": properties.get("ADM0NAME"),
                    "SOV0NAME": properties.get("SOV0NAME"),
                    "SCALERANK": properties.get("SCALERANK"),
                    "POP_MAX": properties.get("POP_MAX"),
                    "CAPIN": properties.get("CAPIN"),
                    "WORLDCITY": properties.get("WORLDCITY"),
                },
                "geometry": geometry,
            }
        )
    return {"type": "FeatureCollection", "features": features}


def write_generated_module(countries_json: str, places_json: str) -> None:
    # Store JSON as strings so TypeScript does not infer a multi-megabyte literal
    # type. Runtime parsing is synchronous and happens once during map startup.
    content = (
        "// Generated by scripts/fetch_world_map_assets.py. Do not hand-edit.\n"
        f"export const PACKAGED_COUNTRIES_JSON = {json.dumps(countries_json, ensure_ascii=False)}\n"
        f"export const PACKAGED_PLACES_JSON = {json.dumps(places_json, ensure_ascii=False)}\n"
    )
    GENERATED_MODULE.write_text(content, encoding="utf-8")
    print(f"Generated {GENERATED_MODULE.relative_to(ROOT)}: {GENERATED_MODULE.stat().st_size / (1024 * 1024):.2f} MiB")


def main() -> int:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    serialized: dict[str, str] = {}
    for filename, urls in ASSETS.items():
        collection = download_json(urls)
        normalized = normalize_country_collection(collection) if filename.startswith("countries") else normalize_place_collection(collection)
        feature_count = len(normalized["features"])
        minimum = MINIMUM_FEATURES[filename]
        if feature_count < minimum:
            raise RuntimeError(f"{filename} contains only {feature_count} features; expected at least {minimum}")
        compact = json.dumps(normalized, separators=(",", ":"), ensure_ascii=False)
        destination = OUTPUT / filename
        destination.write_text(compact, encoding="utf-8")
        serialized[filename] = compact
        size_mb = destination.stat().st_size / (1024 * 1024)
        print(f"Prepared {filename}: {feature_count} features, {size_mb:.2f} MiB")

    write_generated_module(
        serialized["countries-50m.geojson"],
        serialized["places-50m.geojson"],
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FRIDAY WORLD MAP ASSET FAILURE: {error}", file=sys.stderr)
        raise
