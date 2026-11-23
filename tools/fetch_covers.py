#!/usr/bin/env python3
"""Scarica poche foto di COPERTINA da Wikimedia Commons, solo Public Domain / CC0.
Salva in app/src/main/assets/covers e scrive covers/CREDITS.json (autore, licenza, URL)."""
import json, re, sys, time, urllib.parse, urllib.request
from pathlib import Path
UA = "midi-piano-app-personal-fetch/0.1"
API = "https://commons.wikimedia.org/w/api.php"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/covers"
QUERIES = ["piano keyboard", "grand piano", "sheet music", "pianist hands", "piano keys close up",
           "concert hall interior", "music score manuscript", "sunset landscape", "forest path", "sea cliffs sunrise",
           "mountain lake", "autumn leaves"]
OK = ("public domain", "cc0", "pd-", "pd ")
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r: return r.read()
def api(**p):
    p.update(format="json"); return json.loads(get(API + "?" + urllib.parse.urlencode(p)))
OUT.mkdir(parents=True, exist_ok=True)
credits = []
for q in QUERIES:
    if len(credits) >= 14: break
    d = api(action="query", generator="search", gsrsearch=f"{q} filetype:bitmap", gsrnamespace=6, gsrlimit=15,
            prop="imageinfo", iiprop="url|extmetadata|mime|size", iiurlwidth=640)
    time.sleep(1)
    got = 0
    for page in (d.get("query", {}).get("pages", {}) or {}).values():
        ii = (page.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        lic = (meta.get("LicenseShortName", {}).get("value", "") or "").lower()
        if ii.get("mime") != "image/jpeg" or not any(lic.startswith(k) or lic == k.strip() for k in OK): continue
        if ii.get("width", 0) < 900 or ii.get("thumburl") is None: continue
        title = page["title"]
        slug = re.sub(r"[^a-z0-9]+", "-", title.lower().replace("file:", "")).strip("-")[:50]
        fn = f"{len(credits)+1:02d}-{slug}.jpg"
        try: (OUT / fn).write_bytes(get(ii["thumburl"]))
        except Exception as e: print("skip", title, e); continue
        author = re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "") or "sconosciuto")[:80]
        credits.append({"file": fn, "title": title, "author": author, "license": meta["LicenseShortName"]["value"], "url": ii["descriptionurl"]})
        print("ok", fn, "|", meta["LicenseShortName"]["value"], "|", author); got += 1; time.sleep(1)
        if got >= 2: break
(OUT / "CREDITS.json").write_text(json.dumps(credits, ensure_ascii=False, indent=2), encoding="utf-8")
print(len(credits), "immagini")
