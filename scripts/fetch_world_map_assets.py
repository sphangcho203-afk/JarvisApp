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


def first(properties: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = properties.get(key)
        if value is not None and value != "":
            return value
    return None


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
                    "ADMIN": first(properties, "ADMIN", "admin"),
                    "NAME": first(properties, "NAME", "name"),
                    "NAME_LONG": first(properties, "NAME_LONG", "name_long"),
                    "ISO_A2": first(properties, "ISO_A2", "iso_a2"),
                    "ISO_A3": first(properties, "ISO_A3", "iso_a3"),
                    "CONTINENT": first(properties, "CONTINENT", "continent"),
                    "SUBREGION": first(properties, "SUBREGION", "subregion"),
                    "POP_EST": first(properties, "POP_EST", "pop_est"),
                    "GDP_MD": first(properties, "GDP_MD", "gdp_md"),
                    "LABEL_X": first(properties, "LABEL_X", "label_x"),
                    "LABEL_Y": first(properties, "LABEL_Y", "label_y"),
                    "LABELRANK": first(properties, "LABELRANK", "labelrank"),
                    "MIN_ZOOM": first(properties, "MIN_ZOOM", "min_zoom"),
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
        capital = first(properties, "ADM0CAP", "adm0cap")
        world_city = first(properties, "WORLDCITY", "worldcity")
        features.append(
            {
                "type": "Feature",
                "properties": {
                    "NAMEPAR": first(properties, "NAMEPAR", "namepar"),
                    "NAME": first(properties, "NAME", "name", "NAMEASCII", "nameascii"),
                    "ADM0NAME": first(properties, "ADM0NAME", "adm0name"),
                    "SOV0NAME": first(properties, "SOV0NAME", "sov0name"),
                    "SCALERANK": first(properties, "SCALERANK", "scalerank"),
                    "POP_MAX": first(properties, "POP_MAX", "pop_max"),
                    "CAPIN": "capital" if capital in {1, "1", True} else first(properties, "CAPIN", "capin"),
                    "WORLDCITY": "world" if world_city in {1, "1", True} else None,
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
