# Genera le routine quotidiane (app/src/main/assets/routines). Esercizi originali, sempre a due mani.
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'routines')

def notes(hand, start_beat, items):
    """items: lista di (chiave|None, durata). None = pausa."""
    out, b = [], start_beat
    for key, dur in items:
        if key is not None:
            out.append({"beat": b, "dur": dur, "key": key, "hand": hand})
        b += dur
    return out

def bars(hand, per_bar):
    out = []
    for i, items in enumerate(per_bar):
        out += notes(hand, i * 4.0, items)
    return out

q = lambda *ks: [(k, 1.0) for k in ks]
STEPS = [
    {"hand": "B", "mode": "wait", "speed": 1.0, "loops": 1, "text": "Due mani insieme, senza fretta: premi le note quando compaiono."},
    {"hand": "B", "mode": "tempo", "speed": 0.75, "loops": 1, "text": "Ora a tempo, un po' più piano del normale."},
    {"hand": "B", "mode": "tempo", "speed": 1.0, "loops": 1, "text": "A velocità normale: due mani a tempo."},
]

def exercise(eid, title, text, bpm, right, left):
    return {"id": eid, "type": "exercise", "title": title, "text": text,
            "song": {"title": title, "timeSignature": [4, 4], "keyFifths": 0, "bpm": bpm, "notes": right + left},
            "steps": STEPS}

E = []
E.append(exercise("e1", "Cinque dita", "Le due mani salgono e scendono insieme sulle prime cinque note.", 72,
    bars("R", [q(60, 62, 64, 65), q(67, 65, 64, 62), q(60, 64, 62, 65), q(67, 65, 62, 60)]),
    bars("L", [q(48, 50, 52, 53), q(55, 53, 52, 50), q(48, 52, 50, 53), q(55, 53, 50, 48)])))
E.append(exercise("e2", "Scala di Do a due mani", "Una scala in ottave: le mani sono distanti otto note.", 66,
    bars("R", [q(60, 62, 64, 65), q(67, 69, 71, 72), q(72, 71, 69, 67), q(65, 64, 62, 60)]),
    bars("L", [q(48, 50, 52, 53), q(55, 57, 59, 60), q(60, 59, 57, 55), q(53, 52, 50, 48)])))
chordC, chordG = (48, 52, 55), (43, 47, 50)
def chord(hand, ks, start, dur): return [{"beat": start, "dur": dur, "key": k, "hand": hand} for k in ks]
E.append(exercise("e3", "Accordi e melodia", "La destra canta, la sinistra tiene un accordo per battuta.", 66,
    bars("R", [q(64, 62, 60, 62), [(64, 1.0), (64, 1.0), (64, 2.0)], [(62, 1.0), (62, 1.0), (62, 2.0)], [(64, 1.0), (62, 1.0), (60, 2.0)]]),
    chord("L", chordC, 0.0, 4.0) + chord("L", chordC, 4.0, 4.0) + chord("L", chordG, 8.0, 4.0) + chord("L", chordC, 12.0, 4.0)))
E.append(exercise("e4", "Basso e pause", "Il basso va avanti con calma, la destra respira: conta le pause.", 72,
    bars("R", [[(60, 1.0), (64, 1.0), (67, 1.0), (None, 1.0)], [(67, 1.0), (64, 1.0), (60, 1.0), (None, 1.0)],
               [(62, 1.0), (65, 1.0), (69, 1.0), (None, 1.0)], [(67, 1.0), (None, 1.0), (64, 1.0), (60, 1.0)]]),
    bars("L", [[(48, 2.0), (55, 2.0)], [(48, 2.0), (55, 2.0)], [(53, 2.0), (60, 2.0)], [(43, 2.0), (48, 2.0)]])))
E.append(exercise("e5", "Arpeggi e note lunghe", "La sinistra arpeggia, la destra tiene le note lunghe.", 66,
    bars("R", [[(64, 2.0), (67, 2.0)], [(72, 2.0), (67, 2.0)], [(69, 2.0), (65, 2.0)], [(67, 2.0), (65, 2.0)], [(72, 4.0)]]),
    bars("L", [q(48, 52, 55, 52), q(48, 52, 55, 52), q(53, 57, 60, 57), q(55, 59, 62, 59), [(48, 4.0)]])))

course = {"id": "routine-base", "title": "Routine base", "lessons": [
    {"id": "l1", "title": "Riscaldamento", "description": "Cinque esercizi a due mani per scaldare le dita e allenare le basi.", "slides": E}]}
index = {"courses": [{"file": "base.json", "level": 1, "description": "Per cominciare: note vicine, accordi semplici e un po' di pause, sempre a due mani."}]}
os.makedirs(OUT, exist_ok=True)
json.dump(course, open(os.path.join(OUT, 'base.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
json.dump(index, open(os.path.join(OUT, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print("routine scritta:", len(E), "esercizi")
