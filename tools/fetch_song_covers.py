#!/usr/bin/env python3
"""Cerca una foto con licenza libera (Openverse) per ogni autore/gruppo di un meta.json di importazione.

meta.json: { "nomefile.mxl": {"title": "...", "composer": "Gruppo", ...}, ... }
Per ogni compositore diverso salva covers/<slug>.jpg (lato massimo 800 px) e aggiunge "cover" e "credit" a ogni voce.

Regole: solo licenze CC0, PDM, BY, BY-SA, BY-NC, BY-NC-SA (niente ND: la foto viene ritagliata); il titolo o i tag della foto
devono contenere il nome dell'autore; si registra sempre chi ha fatto la foto e con quale licenza. Pause fra le richieste.

Uso: python tools/fetch_song_covers.py CARTELLA_CON_meta.json [--skip "Nome autore"]
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

UA = "midi-piano-app-personal-fetch/0.1"
ALLOWED = ["cc0", "pdm", "by", "by-sa", "by-nc", "by-nc-sa"]  # in ordine di preferenza


def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read(15_000_000)
    time.sleep(1.0)
    return data if binary else json.loads(data)


def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")[:40] or "x"


def words(s):
    return [w for w in re.split(r"[^a-z0-9]+", s.lower()) if w and w not in ("and", "the", "x")]


def search(artist):
    q = re.sub(r"\(.*?\)", "", artist).replace("&", " ").strip()
    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode(
        {"q": q, "page_size": 20, "license_type": "all-cc", "category": "photograph"})
    need = words(q)
    best = None
    for r in get(url).get("results", []):
        lic = r.get("license")
        if lic not in ALLOWED or min(r.get("width") or 0, r.get("height") or 0) < 500:
            continue
        text = " ".join([r.get("title") or ""] + [t.get("name", "") for t in (r.get("tags") or [])]).lower()
        if not all(w in text for w in need):
            continue
        key = (ALLOWED.index(lic), -min(r["width"], r["height"]))
        if best is None or key < best[0]:
            best = (key, r)
    return best[1] if best else None


def save_square(data, path):
    try:
        from PIL import Image
        import io
        im = Image.open(io.BytesIO(data)).convert("RGB")
        w, h = im.size
        s = min(w, h)
        im = im.crop(((w - s) // 2, int((h - s) * 0.25), (w - s) // 2 + s, int((h - s) * 0.25) + s)).resize((min(s, 800), min(s, 800)))
        im.save(path, "JPEG", quality=88)
    except ImportError:
        import cv2
        import numpy as np
        im = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
        h, w = im.shape[:2]
        s = min(w, h)
        x0, y0 = (w - s) // 2, int((h - s) * 0.25)
        im = cv2.resize(im[y0:y0 + s, x0:x0 + s], (min(s, 800), min(s, 800)))
        cv2.imwrite(str(path), im, [cv2.IMWRITE_JPEG_QUALITY, 88])


def main():
    folder = Path(sys.argv[1])
    skip = {a.lower() for i, a in enumerate(sys.argv) if i > 0 and sys.argv[i - 1] == "--skip"}
    meta = json.loads((folder / "meta.json").read_text(encoding="utf-8"))
    (folder / "covers").mkdir(exist_ok=True)
    done = {}
    for fname, m in meta.items():
        artist = m.get("composer")
        if not artist or artist.lower() in skip:
            continue
        if artist not in done:
            r = None
            try:
                r = search(artist)
            except Exception as e:
                print("ERRORE", artist, e)
            if r:
                try:
                    data = get(r.get("url") or r["thumbnail"], binary=True)
                    name = slug(artist) + ".jpg"
                    save_square(data, folder / "covers" / name)
                    lic = r["license"].upper().replace("-", " ") + (" " + r["license_version"] if r.get("license_version") else "")
                    done[artist] = (name, f"{r.get('creator') or 'autore ignoto'} · CC {lic}".replace("CC CC0", "CC0").replace("CC PDM", "Pubblico dominio"))
                    print("OK", artist, "->", r["title"][:60], done[artist][1])
                except Exception as e:
                    print("ERRORE foto", artist, e)
                    done[artist] = None
            else:
                print("nessuna foto adatta per", artist)
                done[artist] = None
        if done.get(artist):
            m["cover"], m["credit"] = done[artist]
    (folder / "meta.json").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
