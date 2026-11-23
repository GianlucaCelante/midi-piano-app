#!/usr/bin/env python3
"""Scarica brani MIDI con licenza libera da Mutopia Project e li registra in un indice.

Regole:
- si scarica un brano solo se la licenza letta dal suo file .ly è nella lista ammessa;
- la licenza, l'origine e l'URL restano nell'indice accanto al brano;
- pause fra le richieste e User-Agent generico (non si fa scraping aggressivo).

Uso:
  python tools/fetch_free_music.py --composer BachJS --limit 5
  python tools/fetch_free_music.py --composer BeethovenLv --instrument Piano --limit 5
  python tools/fetch_free_music.py --source openscore --composer Schubert --limit 5
"""
import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://www.mutopiaproject.org"
UA = "midi-piano-app-personal-fetch/0.1"
DELAY_S = 1.0

# (testo da cercare in minuscolo, etichetta normalizzata, richiede attribuzione)
ALLOWED = [
    ("public domain", "Public Domain", False),
    ("creative commons zero", "CC0", False),
    ("cc0", "CC0", False),
    ("attribution-sharealike", "CC BY-SA", True),
    ("attribution", "CC BY", True),
]


MAX_BYTES = 20 * 1024 * 1024  # tetto per ogni file scaricato


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError(f"file troppo grande (> {MAX_BYTES} byte): {url}")
    time.sleep(DELAY_S)
    return data


def normalize_license(raw: str):
    low = raw.lower()
    if "noncommercial" in low or "non-commercial" in low or "noderiv" in low or "no-deriv" in low:
        return None  # non sono licenze libere per i nostri scopi
    for needle, label, attribution in ALLOWED:
        if needle in low:
            return label, attribution
    return None


def ly_field(ly: str, name: str):
    m = re.search(rf'^\s*{name}\s*=\s*"([^"]*)"', ly, re.MULTILINE)
    return m.group(1).strip() if m else None


def list_mid_urls(composer: str, instrument: str, limit: int):
    found, start = [], 0
    while len(found) < limit:
        q = urllib.parse.urlencode(
            {"Composer": composer, "Instrument": instrument, "searchingfor": "", "startat": start,
             "view": 1, "lilyversion": ""})
        html = get(f"{BASE}/cgibin/make-table.cgi?{q}").decode("utf-8", "replace")
        mids = list(dict.fromkeys(re.findall(r'https://www\.mutopiaproject\.org/ftp/[^"\s]+?\.mid', html)))
        remember_listing(html)
        new = [m for m in mids if m not in found]
        if not new:
            break
        found.extend(new)
        start += 10
    return found[:limit]


LISTING = {}  # url .mid -> (titolo, licenza) letti dall'elenco: servono quando il file .ly non è scaricabile


def remember_listing(html: str):
    """Ogni brano dell'elenco ha titolo, licenza (link a legal.html) e link al .mid: si tengono come ripiego."""
    for block in html.split("piece-info.cgi?id=")[1:]:
        mid = re.search(r'https://www\.mutopiaproject\.org/ftp/[^"\s]+?\.mid', block)
        lic = re.search(r'legal\.html#[^"]*"[^>]*>([^<]*)<', block)
        if not mid or not lic:
            continue
        head = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html[html.find(block) - 700:html.find(block)]))
        m = re.findall(r"([^<>]{3,120}?) by ([^<>]{3,60}?) \(", head)
        title = m[-1][0].strip() if m else None
        LISTING[mid.group(0)] = (title, m[-1][1].strip() if m else None, lic.group(1).strip())


def slug(path: str) -> str:
    """Id leggibile dal percorso, senza segmenti consecutivi uguali (dir e file spesso coincidono)."""
    parts = []
    for part in path.rsplit(".", 1)[0].split("/"):
        norm = re.sub(r"[^a-z0-9]+", "-", part.lower()).strip("-")
        if norm and (not parts or parts[-1] != norm):
            parts.append(norm)
    return "-".join(parts)


OPENSCORE_REPO = "OpenScore/Lieder"
OPENSCORE_LICENSE = "CC0"  # licenza dichiarata dal repository (LICENSE.txt, CC0 1.0)


def fetch_openscore(args, out: Path, by_id: dict):
    """Scarica spartiti MusicXML compressi (.mxl) da OpenScore Lieder (CC0)."""
    tree = json.loads(get(f"https://api.github.com/repos/{OPENSCORE_REPO}/git/trees/main?recursive=1"))
    if tree.get("truncated"):
        print("ATTENZIONE: elenco file troncato da GitHub")
    needle = (args.composer or "").lower()
    paths = [t["path"] for t in tree["tree"]
             if t["type"] == "blob" and t["path"].startswith("scores/") and t["path"].endswith(".mxl")
             and needle in t["path"].lower()]
    saved = 0
    for path in paths[: args.limit]:
        _, composer, collection, piece, _file = path.split("/", 4)[0:5] if path.count("/") >= 4 else (None,) * 5
        if composer is None:
            continue
        sid = "openscore-" + slug(path.split("/", 1)[1])
        try:
            data = get("https://raw.githubusercontent.com/" + OPENSCORE_REPO + "/main/" + urllib.parse.quote(path))
        except Exception as e:  # noqa: BLE001
            print(f"SKIP {sid}: download fallito ({e})")
            continue
        if data[:2] != b"PK":
            print(f"SKIP {sid}: il file scaricato non è un .mxl")
            continue
        (out / f"{sid}.mxl").write_bytes(data)
        by_id[sid] = {
            "id": sid,
            "title": piece.replace("_", " "),
            "composer": composer.replace(",_", ", ").replace("_", " "),
            "file": f"{sid}.mxl",
            "origin": "OpenScore Lieder",
            "license": OPENSCORE_LICENSE,
            "license_text": "Creative Commons Zero v1.0 Universal",
            "attribution_required": False,
            "url": f"https://github.com/{OPENSCORE_REPO}/tree/main/" + urllib.parse.quote("/".join(path.split("/")[:-1])),
        }
        print(f"OK   {sid}: {by_id[sid]['title']} [{OPENSCORE_LICENSE}]")
        saved += 1
    return saved


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", choices=["mutopia", "openscore"], default="mutopia")
    ap.add_argument("--composer", default="", help="Mutopia: codice (BachJS, BeethovenLv...). OpenScore: testo da cercare nel percorso")
    ap.add_argument("--instrument", default="Piano")
    ap.add_argument("--limit", type=int, default=5)
    ap.add_argument("--out", default="app/src/main/assets/songs")
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    index_path = out / "index.json"
    index = json.loads(index_path.read_text("utf-8")) if index_path.exists() else []
    by_id = {e["id"]: e for e in index}

    if args.source == "openscore":
        saved = 0
        try:
            saved = fetch_openscore(args, out, by_id)
        finally:  # l'indice riflette sempre i file già scritti, anche dopo un errore di rete
            index_path.write_text(json.dumps(sorted(by_id.values(), key=lambda e: e["id"]), ensure_ascii=False, indent=2) + "\n", "utf-8")
        print(f"\nSalvati: {saved}. Indice: {index_path}")
        return 0
    if not args.composer:
        ap.error("--composer è obbligatorio per Mutopia")

    saved = skipped = 0
    for mid_url in list_mid_urls(args.composer, args.instrument, args.limit):
        sid = slug(mid_url.split("/ftp/")[1])
        ly = ""
        try:
            ly = get(mid_url.rsplit(".", 1)[0] + ".ly").decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            if mid_url not in LISTING:
                print(f"SKIP {sid}: impossibile leggere i metadati ({e})")
                skipped += 1
                continue
        listed = LISTING.get(mid_url, (None, None, ""))
        raw_license = ly_field(ly, "license") or ly_field(ly, "copyright") or listed[2] or ""
        norm = normalize_license(raw_license)
        if norm is None:
            print(f"SKIP {sid}: licenza non ammessa o ignota ({raw_license!r})")
            skipped += 1
            continue
        label, attribution = norm
        title = " - ".join(p for p in (ly_field(ly, "title"), ly_field(ly, "subtitle")) if p) or listed[0] or sid
        try:
            data = get(mid_url)
        except Exception as e:  # noqa: BLE001
            print(f"SKIP {sid}: download fallito ({e})")
            skipped += 1
            continue
        if data[:4] != b"MThd":
            print(f"SKIP {sid}: il file scaricato non è MIDI")
            skipped += 1
            continue
        (out / f"{sid}.mid").write_bytes(data)
        by_id[sid] = {
            "id": sid,
            "title": title,
            "composer": ly_field(ly, "composer") or listed[1],
            "file": f"{sid}.mid",
            "origin": "Mutopia Project",
            "license": label,
            "license_text": raw_license,
            "attribution_required": attribution,
            "url": mid_url.rsplit("/", 1)[0] + "/",
        }
        print(f"OK   {sid}: {title} [{label}]")
        saved += 1

    index_path.write_text(json.dumps(sorted(by_id.values(), key=lambda e: e["id"]), ensure_ascii=False, indent=2) + "\n", "utf-8")
    print(f"\nSalvati: {saved}, saltati: {skipped}. Indice: {index_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
