#!/usr/bin/env python3
"""Foto di copertina dal Metropolitan Museum of Art (Open Access, CC0): strumenti, dipinti di musicisti, spartiti.
API pubblica senza chiave. Aggiunge file a app/src/main/assets/covers, aggiorna CREDITS.json e map.json."""
import json, re, time, urllib.parse, urllib.request
from pathlib import Path
UA = "midi-piano-app-personal-fetch/0.1 (educational, non-commercial)"
BASE = "https://collectionapi.metmuseum.org/public/collection"
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/covers"
# (query di ricerca, [parole chiave])
TOPICS = [
 ("Mozart", ["mozart"]),
 ("Schumann", ["schumann"]),
 ("Tchaikovsky", ["tchaikovsky", "cajkovskij", "ciaikovski"]),
 ("Bizet Carmen", ["bizet", "carmen"]),
 ("metronome", ["ritmo", "figure"]),
 ("harp", ["arpeggi", "accompagnamenti"]),
 ("pianoforte keyboard", ["mano destra", "melodie", "solo mano destra"]),
 ("harpsichord", ["mano sinistra"]),
 ("musicians playing together", ["due mani"]),
 ("musical notation manuscript", ["primi passi", "tecnica", "scale"]),
 ("clavichord", ["nuove tonalit"]),
 ("grand piano", ["espressione", "mosse"]),
 ("lute player", ["accordi", "rivolti", "bicordi"]),
 ("music lesson painting", ["repertorio", "esempio"]),
 ("Christmas night", ["stille nacht", "astro del ciel", "silent night"]),
 ("woman in green dress", ["greensleeves"]),
]
def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read(); return data if binary else json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code not in (403, 429) or attempt == 4: raise
            time.sleep(20 * (attempt + 1))
credits = json.loads((OUT / "CREDITS.json").read_text(encoding="utf-8"))
mapping = json.loads((OUT / "map.json").read_text(encoding="utf-8")) if (OUT / "map.json").exists() else {}
used = {c["title"] for c in credits}
for q, keys in TOPICS:
    if all(k in mapping for k in keys): continue
    ids = get(f"{BASE}/v1.1/search?" + urllib.parse.urlencode({"q": q, "isPublicDomain": "true", "hasImages": "true", "limit": 30, "offset": 0})).get("objectIDs") or []
    time.sleep(0.5)
    chosen = None
    for oid in ids[:25]:
        try: o = get(f"{BASE}/v1/objects/{oid}")
        except urllib.error.HTTPError as e:
            if e.code == 404: continue
            raise
        time.sleep(0.3)
        title = f"Met {oid}: {o.get('title','')}"
        if not o.get("isPublicDomain") or not o.get("primaryImageSmall") or title in used: continue
        if o.get("classification", "") in ("Photographs", "Textiles", "Costume", "Coins"): continue
        chosen = (oid, o, title); break
    if not chosen: print("NESSUNA per:", q); continue
    oid, o, title = chosen
    slug = re.sub(r"[^a-z0-9]+", "-", (o.get("title") or "met").lower()).strip("-")[:30]
    fn = f"t-{keys[0].replace(' ','-')}-met{oid}-{slug}.jpg"
    (OUT / fn).write_bytes(get(o["primaryImageSmall"], binary=True))
    credits.append({"file": fn, "title": title, "author": o.get("artistDisplayName") or "sconosciuto", "license": "CC0 (Met Open Access)", "url": o.get("objectURL")})
    for k in keys: mapping.setdefault(k, fn)
    used.add(title)
    print("ok", keys[0], "->", fn, flush=True)
    (OUT / "CREDITS.json").write_text(json.dumps(credits, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "map.json").write_text(json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8")
print(len(mapping), "parole chiave,", len(set(mapping.values())), "immagini")
