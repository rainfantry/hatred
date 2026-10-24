"""Builds the bundled NSW enforcement-camera list from Transport for NSW's open data.

Put the two CSVs from the "NSW Speed Cameras" dataset in tools/data/ first (not committed):
  fixed.csv     Fixed Speed Cameras
  redlight.csv  Red Light Speed Cameras
  https://opendata.transport.nsw.gov.au/data/dataset/nsw-speed-cameras   (CC BY 4.0)
Then run:  python tools/gen_nsw_cameras.py
Writes app/src/main/assets/cameras/nsw_tfnsw.json: a list in KnownCamera's JSON shape
(what AlprStore saves), one entry per camera point. A site with two or three cameras
(Lat(1)..Lat(3)) gives two or three entries.
"""
import csv, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'data')
OUT = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'cameras', 'nsw_tfnsw.json')
# NSW, generously: a point outside is a typo in the sheet, not a camera.
LAT, LON = (-38.0, -28.0), (140.0, 154.0)

def read(name, kind):
    out = []
    with open(os.path.join(SRC, name), encoding='utf-8-sig', newline='') as f:
        for row_no, r in enumerate(csv.DictReader(f), start=1):
            for i in (1, 2, 3):
                lat, lon = (r.get(f'Lat({i})') or '').strip(), (r.get(f'Long({i})') or '').strip()
                if not lat and not lon:
                    continue
                lat, lon = float(lat), float(lon)
                if not (LAT[0] < lat < LAT[1] and LON[0] < lon < LON[1]):
                    raise SystemExit(f'{name} row {row_no}: {lat},{lon} is not in NSW')
                out.append({
                    # Not an OSM id: the source and row, so a camera keeps its id across refreshes.
                    'osmId': f'tfnsw/{kind.lower()}/{row_no}-{i}',
                    'lat': lat, 'lon': lon,
                    'operator': 'Transport for NSW',
                    'kind': kind,
                })
    return out

cams = read('fixed.csv', 'SPEED') + read('redlight.csv', 'RED_LIGHT')
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(cams, f, separators=(',', ':'))
print(f'{len(cams)} cameras -> {os.path.relpath(OUT, os.path.join(HERE, ".."))}')
