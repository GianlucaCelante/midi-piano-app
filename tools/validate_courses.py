#!/usr/bin/env python3
"""Valida i corsi JSON in app/src/main/assets/courses (solo libreria standard).

Uso: python tools/validate_courses.py [cartella_corsi]
Esce con codice 0 se tutto e' valido, 1 altrimenti.
"""
import json
import math
import os
import re
import sys

EPS = 1e-6
KEY_MIN, KEY_MAX = 36, 84
LESSONS_MIN, LESSONS_MAX = 5, 6
SLIDES_MIN, SLIDES_MAX = 3, 5
BARS_MIN, BARS_MAX = 4, 16
STEPS_MIN, STEPS_MAX = 2, 4
LOOPS_MIN, LOOPS_MAX = 1, 3
HANDS = ("R", "L")
STEP_HANDS = ("R", "L", "B")
MODES = ("wait", "tempo")
SPEEDS = (0.5, 0.75, 1.0)
EXPECTED_COURSES = 14

errors = []


def err(where, msg):
    errors.append(f"{where}: {msg}")


def is_num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def is_str(v):
    return isinstance(v, str) and v.strip() != ""


def req(obj, field, typ_check, where, desc):
    if not isinstance(obj, dict) or field not in obj:
        err(where, f"campo mancante '{field}'")
        return None
    v = obj[field]
    if not typ_check(v):
        err(where, f"campo '{field}' non valido (atteso {desc})")
        return None
    return v


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        err(path, f"impossibile leggere il JSON: {e}")
        return None


def check_song(song, where, stats):
    if not isinstance(song, dict):
        err(where, "song non e' un oggetto")
        return None
    req(song, "title", is_str, where, "stringa non vuota")
    ts = song.get("timeSignature")
    barlen = None
    if (isinstance(ts, list) and len(ts) == 2 and all(isinstance(x, int) and not isinstance(x, bool) for x in ts)
            and ts[0] > 0 and ts[1] in (1, 2, 4, 8, 16)):
        barlen = ts[0] * 4.0 / ts[1]
    else:
        err(where, "timeSignature deve essere [numeratore, denominatore] valido")
    kf = song.get("keyFifths")
    if not (isinstance(kf, int) and not isinstance(kf, bool) and -7 <= kf <= 7):
        err(where, "keyFifths deve essere un intero tra -7 e 7")
    bpm = song.get("bpm")
    if not (is_num(bpm) and 30 <= bpm <= 200):
        err(where, "bpm deve essere un numero tra 30 e 200")
    notes = song.get("notes")
    if not isinstance(notes, list) or not notes:
        err(where, "notes deve essere una lista non vuota")
        return None
    hands_used = set()
    by_key = {}
    max_end = 0.0
    for i, n in enumerate(notes):
        w = f"{where} nota#{i}"
        if not isinstance(n, dict):
            err(w, "non e' un oggetto")
            continue
        b, d, k, h = n.get("beat"), n.get("dur"), n.get("key"), n.get("hand")
        ok = True
        if not (is_num(b) and b >= 0):
            err(w, "beat deve essere un numero >= 0"); ok = False
        if not (is_num(d) and d > 0):
            err(w, "dur deve essere un numero > 0"); ok = False
        if not (isinstance(k, int) and not isinstance(k, bool) and KEY_MIN <= k <= KEY_MAX):
            err(w, f"key deve essere un intero {KEY_MIN}..{KEY_MAX}"); ok = False
        if h not in HANDS:
            err(w, "hand deve essere 'R' o 'L'"); ok = False
        if not ok:
            continue
        hands_used.add(h)
        by_key.setdefault(k, []).append((b, b + d))
        max_end = max(max_end, b + d)
        stats["notes"] += 1
    for k, spans in by_key.items():
        spans.sort()
        for (s1, e1), (s2, e2) in zip(spans, spans[1:]):
            if s2 < e1 - EPS:
                err(where, f"sovrapposizione sulla nota {k}: [{s1},{e1}] e [{s2},{e2}]")
    if barlen:
        bars = math.ceil(max_end / barlen - EPS)
        if not (BARS_MIN <= bars <= BARS_MAX):
            err(where, f"lunghezza di {bars} battute fuori dai limiti {BARS_MIN}..{BARS_MAX}")
        stats["bars"].append(bars)
    return hands_used


def check_steps(steps, hands_used, where):
    if not isinstance(steps, list) or not (STEPS_MIN <= len(steps) <= STEPS_MAX):
        err(where, f"steps deve avere da {STEPS_MIN} a {STEPS_MAX} elementi")
        return
    for i, s in enumerate(steps):
        w = f"{where} passo#{i}"
        if not isinstance(s, dict):
            err(w, "non e' un oggetto")
            continue
        h = s.get("hand")
        if h not in STEP_HANDS:
            err(w, "hand deve essere R, L o B")
        elif hands_used is not None and h != "B" and h not in hands_used:
            err(w, f"il passo usa la mano {h} ma il brano non ha note per quella mano")
        if s.get("mode") not in MODES:
            err(w, "mode deve essere 'wait' o 'tempo'")
        if s.get("speed") not in SPEEDS or isinstance(s.get("speed"), bool):
            err(w, "speed deve essere 0.5, 0.75 o 1.0")
        lp = s.get("loops")
        if not (isinstance(lp, int) and not isinstance(lp, bool) and LOOPS_MIN <= lp <= LOOPS_MAX):
            err(w, f"loops deve essere un intero {LOOPS_MIN}..{LOOPS_MAX}")
        if not is_str(s.get("text")):
            err(w, "text mancante o vuoto")
    # difficolta' non decrescente: la velocita' non deve scendere tra passi consecutivi
    # della stessa modalita'/mano (controllo morbido sulla velocita')
    speeds = [s.get("speed") for s in steps if isinstance(s, dict) and s.get("mode") == "tempo"]
    if speeds != sorted(speeds):
        err(where, "le velocita' dei passi 'tempo' devono essere non decrescenti")


def sentence_count(text):
    return len([p for p in re.split(r"(?<=[.!?])\s+", text.strip()) if p])


def check_course(course, where, stats):
    req(course, "id", is_str, where, "stringa")
    req(course, "title", is_str, where, "stringa")
    lessons = req(course, "lessons", lambda v: isinstance(v, list), where, "lista")
    if lessons is None:
        return
    if not (LESSONS_MIN <= len(lessons) <= LESSONS_MAX):
        err(where, f"{len(lessons)} lezioni: attese da {LESSONS_MIN} a {LESSONS_MAX}")
    lesson_ids = set()
    for li, les in enumerate(lessons):
        lw = f"{where} lezione#{li}"
        lid = req(les, "id", is_str, lw, "stringa")
        if lid in lesson_ids:
            err(lw, f"id lezione duplicato '{lid}'")
        lesson_ids.add(lid)
        req(les, "title", is_str, lw, "stringa")
        req(les, "description", is_str, lw, "stringa")
        slides = req(les, "slides", lambda v: isinstance(v, list), lw, "lista")
        if slides is None:
            continue
        stats["lessons"] += 1
        if not (SLIDES_MIN <= len(slides) <= SLIDES_MAX):
            err(lw, f"{len(slides)} slide: attese da {SLIDES_MIN} a {SLIDES_MAX}")
        slide_ids = set()
        for si, sl in enumerate(slides):
            sw = f"{lw} slide#{si}"
            sid = req(sl, "id", is_str, sw, "stringa")
            if sid in slide_ids:
                err(sw, f"id slide duplicato '{sid}'")
            slide_ids.add(sid)
            req(sl, "title", is_str, sw, "stringa")
            t = sl.get("type") if isinstance(sl, dict) else None
            if t == "text":
                body = req(sl, "body", is_str, sw, "stringa")
                if body is not None and not (2 <= sentence_count(body) <= 5):
                    err(sw, f"body con {sentence_count(body)} frasi: attese 2-5")
                stats["text"] += 1
            elif t == "exercise":
                req(sl, "text", is_str, sw, "stringa")
                hands = check_song(sl.get("song"), sw + " song", stats)
                check_steps(sl.get("steps"), hands, sw)
                stats["exercises"] += 1
            else:
                err(sw, "type deve essere 'text' o 'exercise'")


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "courses")
    base = os.path.normpath(base)
    index = load(os.path.join(base, "index.json"))
    if index is None:
        print("ERRORE: index.json non leggibile")
        for e in errors:
            print(" -", e)
        return 1
    courses = index.get("courses") if isinstance(index, dict) else None
    if not isinstance(courses, list) or not courses:
        err("index.json", "'courses' deve essere una lista non vuota")
        courses = []
    if len(courses) != EXPECTED_COURSES:
        err("index.json", f"attesi {EXPECTED_COURSES} corsi, trovati {len(courses)}")
    ids, files = set(), set()
    total = {"courses": 0, "lessons": 0, "exercises": 0, "text": 0, "notes": 0, "bars": []}
    rows = []
    for i, c in enumerate(courses):
        w = f"index.json corso#{i}"
        cid = req(c, "id", is_str, w, "stringa")
        req(c, "title", is_str, w, "stringa")
        req(c, "description", is_str, w, "stringa")
        lv = req(c, "level", lambda v: isinstance(v, int) and not isinstance(v, bool) and 1 <= v <= 5, w, "intero 1..5")
        fn = req(c, "file", is_str, w, "stringa")
        if cid in ids:
            err(w, f"id corso duplicato '{cid}'")
        ids.add(cid)
        if fn in files:
            err(w, f"file duplicato '{fn}'")
        files.add(fn)
        if fn is None:
            continue
        data = load(os.path.join(base, fn))
        if data is None:
            continue
        if cid is not None and data.get("id") != cid:
            err(fn, f"id del corso '{data.get('id')}' diverso da quello in index.json '{cid}'")
        before = dict(total)
        stats = {"lessons": 0, "exercises": 0, "text": 0, "notes": 0, "bars": []}
        check_course(data, fn, stats)
        total["courses"] += 1
        for k in ("lessons", "exercises", "text", "notes"):
            total[k] += stats[k]
        total["bars"].extend(stats["bars"])
        rows.append((cid, data.get("title"), lv, stats))
    print("=== RIEPILOGO ===")
    for cid, title, lv, st in rows:
        print(f"- {title} ({cid}, livello {lv}): {st['lessons']} lezioni, {st['exercises']} esercizi, "
              f"{st['text']} slide di testo, {st['notes']} note")
    if total["bars"]:
        print(f"Battute per esercizio: min {min(total['bars'])}, max {max(total['bars'])}")
    print(f"TOTALE: {total['courses']} corsi, {total['lessons']} lezioni, {total['exercises']} esercizi, "
          f"{total['notes']} note")
    if errors:
        print(f"\nERRORI ({len(errors)}):")
        for e in errors:
            print(" -", e)
        return 1
    print("\nOK: tutti i controlli superati.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
