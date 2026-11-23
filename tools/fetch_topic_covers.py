#!/usr/bin/env python3
"""Foto di copertina PERTINENTI (ritratti dei compositori, temi dei corsi) da Wikimedia Commons.
Solo Public Domain / CC0. Aggiunge i file a app/src/main/assets/covers, aggiorna CREDITS.json e scrive map.json
(parola chiave minuscola -> file): la copertina di un brano/corso usa la prima parola chiave che compare in id+titolo+autore."""
import json, re, time, urllib.parse, urllib.request
from pathlib import Path
UA = "midi-piano-app-personal-fetch/0.1"
API = "https://commons.wikimedia.org/w/api.php"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/covers"
OK = ("public domain", "cc0", "pd-", "pd ")
# (query, [parole chiave])
TOPICS = [
 ("Ludwig van Beethoven portrait Stieler", ["beethoven"]),
 ("Johann Sebastian Bach portrait Haussmann", ["bach", "bwv"]),
 ("Frederic Chopin portrait Delacroix", ["chopin"]),
 ("Wolfgang Amadeus Mozart portrait Lange", ["mozart"]),
 ("Claude Debussy portrait Nadar", ["debussy"]),
 ("Erik Satie portrait", ["satie"]),
 ("Franz Liszt portrait Lehmann", ["liszt"]),
 ("Franz Schubert portrait Kupelwieser", ["schubert"]),
 ("Johannes Brahms portrait", ["brahms"]),
 ("Pyotr Tchaikovsky portrait", ["tchaikovsky", "cajkovskij", "ciaikovski"]),
 ("Robert Schumann portrait", ["schumann"]),
 ("Georges Bizet portrait", ["bizet", "carmen"]),
 ("Christmas night snow village painting", ["stille nacht", "astro del ciel", "silent night"]),
 ("Tudor lady portrait painting", ["greensleeves"]),
 ("sunset prairie landscape painting", ["amazing grace"]),
 ("metronome", ["ritmo", "figure"]),
 ("harp strings close up", ["arpeggi", "accompagnamenti"]),
 ("piano hands close up photograph", ["mano destra", "melodie", "solo mano destra"]),
 ("left hand piano keys", ["mano sinistra"]),
 ("piano duet four hands", ["due mani"]),
 ("music score notation staff", ["primi passi", "tecnica", "scale"]),
 ("key signature circle of fifths", ["nuove tonalit"]),
 ("grand piano concert stage", ["espressione", "mosse"]),
 ("chord piano keyboard", ["accordi", "rivolti", "bicordi"]),
 ("old folk song sheet music", ["repertorio", "esempio"]),
]
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=60) as r: return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 5: raise
            wait = 45 * (attempt + 1)
            print(f"  429: attendo {wait}s", flush=True); time.sleep(wait)
def api(**p):
    p.update(format="json"); return json.loads(get(API + "?" + urllib.parse.urlencode(p)))
credits = json.loads((OUT / "CREDITS.json").read_text(encoding="utf-8"))
mapping = {}
for q, keys in TOPICS:
    d = api(action="query", generator="search", gsrsearch=f"{q} filetype:bitmap", gsrnamespace=6, gsrlimit=20,
            prop="imageinfo", iiprop="url|extmetadata|mime|size", iiurlwidth=640)
    time.sleep(4)
    chosen = None
    pages = sorted((d.get("query", {}).get("pages", {}) or {}).values(), key=lambda p: p.get("index", 99))
    for page in pages:
        ii = (page.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        lic = (meta.get("LicenseShortName", {}).get("value", "") or "").lower()
        if ii.get("mime") != "image/jpeg" or not any(lic.startswith(k) for k in OK): continue
        if ii.get("width", 0) < 500 or not ii.get("thumburl"): continue
        chosen = (page, ii, meta); break
    if not chosen: print("NESSUNA per:", q); continue
    page, ii, meta = chosen
    slug = re.sub(r"[^a-z0-9]+", "-", page["title"].lower().replace("file:", "")).strip("-")[:40]
    fn = f"t-{keys[0].replace(' ','-')}-{slug}.jpg"
    if not (OUT / fn).exists() or (OUT / fn).stat().st_size < 5000:
        try: (OUT / fn).write_bytes(get(ii["thumburl"]))
        except Exception as e: print("skip", e); continue
    author = re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "") or "sconosciuto")[:80]
    credits = [c for c in credits if c["file"] != fn]
    credits.append({"file": fn, "title": page["title"], "author": author, "license": meta["LicenseShortName"]["value"], "url": ii["descriptionurl"]})
    for k in keys: mapping[k] = fn
    print("ok", keys[0], "->", fn, "|", meta["LicenseShortName"]["value"], flush=True); time.sleep(4)
    (OUT / "CREDITS.json").write_text(json.dumps(credits, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "map.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
(OUT / "CREDITS.json").write_text(json.dumps(credits, ensure_ascii=False, indent=2), encoding="utf-8")
(OUT / "map.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
print(len(mapping), "parole chiave,", len(set(mapping.values())), "immagini")
