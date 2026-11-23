# Genera quattro corsi in piu' (app/src/main/assets/courses). Esercizi originali, generati da regole musicali semplici.
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'courses')

def N(beat, dur, key, hand):
    return {"beat": round(beat, 6), "dur": round(dur, 6), "key": key, "hand": hand}

def line(hand, items, start=0.0):
    """items: (chiave|None, durata); None = pausa."""
    out, b = [], start
    for k, d in items:
        if k is not None:
            out.append(N(b, d, k, hand))
        b += d
    return out

def chord(hand, keys, beat, dur):
    return [N(beat, dur, k, hand) for k in keys]

MAJ = [0, 2, 4, 5, 7, 9, 11]
MIN = [0, 2, 3, 5, 7, 8, 10]
HARM = [0, 2, 3, 5, 7, 8, 11]

def scale(tonic, steps, n):
    """n gradi della scala (anche oltre l'ottava) a partire dalla tonica."""
    return [tonic + 12 * (i // 7) + steps[i % 7] for i in range(n)]

def triad(tonic, steps, degree):
    s = scale(tonic, steps, degree + 5)
    return [s[degree], s[degree + 2], s[degree + 4]]

def step(hand, mode, speed, loops, text):
    return {"hand": hand, "mode": mode, "speed": speed, "loops": loops, "text": text}

R_STEPS = [step("R", "wait", 1.0, 1, "Mano destra: premi ogni nota quando compare, senza fretta."),
           step("R", "tempo", 0.75, 2, "Ora a tempo, un po' più piano del normale."),
           step("R", "tempo", 1.0, 2, "Velocità normale, con un battito regolare.")]
L_STEPS = [step("L", "wait", 1.0, 1, "Mano sinistra: premi ogni nota quando compare, senza fretta."),
           step("L", "tempo", 0.75, 2, "Ora a tempo, un po' più piano del normale."),
           step("L", "tempo", 1.0, 2, "Velocità normale, con un battito regolare.")]
B_STEPS = [step("L", "wait", 1.0, 1, "Prima la sinistra da sola: è lei che regge il ritmo."),
           step("R", "wait", 1.0, 1, "Ora la destra da sola."),
           step("B", "tempo", 0.75, 2, "Le due mani insieme, a tempo e un po' più piano."),
           step("B", "tempo", 1.0, 2, "Le due mani insieme, a velocità normale.")]
B_SHORT = [step("B", "wait", 1.0, 1, "Le due mani insieme, senza fretta."),
           step("B", "tempo", 0.75, 2, "Ora a tempo, un po' più piano."),
           step("B", "tempo", 1.0, 2, "Velocità normale.")]

def ex(eid, title, text, notes, steps, bpm, ts=(4, 4), fifths=0):
    return {"id": eid, "type": "exercise", "title": title, "text": text,
            "song": {"title": title, "timeSignature": list(ts), "keyFifths": fifths, "bpm": bpm, "notes": notes}, "steps": steps}

def txt(sid, title, body):
    return {"id": sid, "type": "text", "title": title, "body": body}

def lesson(lid, title, desc, slides):
    return {"id": lid, "title": title, "description": desc, "slides": slides}

# ----------------------------------------------------------------------------------------------
# 1. Più tonalità
# ----------------------------------------------------------------------------------------------
def scale_ex(eid, name, tonic, steps, fifths, bpm):
    s = scale(tonic, steps, 15)
    up = s[:8]
    down = list(reversed(s[:7]))
    seq = [(k, 1.0) for k in up + down] + [(s[0], 4.0)]
    return ex(eid, f"Scala di {name}", "Su e giù per la scala, una nota per battito: ascolta dove cadono i tasti neri.", line("R", seq), R_STEPS, bpm, fifths=fifths)

def melody_ex(eid, name, tonic, steps, fifths, bpm, degrees, chords, text):
    s = scale(tonic, steps, 15)
    mel, b = [], 0.0
    for bar in degrees:
        for d, dur in bar:
            mel.append(N(b, dur, s[d], "R")); b += dur
    left = []
    for i, deg in enumerate(chords):
        left += chord("L", [k - 12 for k in triad(tonic, steps, deg)], i * 4.0, 4.0)
    return ex(eid, f"Melodia in {name}", text, mel + left, B_STEPS, bpm, fifths=fifths)

MEL1 = [[(0, 1), (1, 1), (2, 1), (0, 1)], [(2, 1), (3, 1), (4, 2)], [(4, 1), (5, 1), (4, 1), (3, 1)], [(2, 1), (1, 1), (0, 2)]]
MEL2 = [[(4, 2), (2, 1), (4, 1)], [(5, 1), (4, 1), (3, 2)], [(2, 1), (3, 1), (4, 1), (2, 1)], [(1, 2), (0, 2)]]
MEL3 = [[(0, 2), (2, 2)], [(4, 3), (2, 1)], [(3, 1), (2, 1), (1, 1), (2, 1)], [(0, 4)]]
CH_PLAIN = [0, 3, 4, 0]
CH_PLAIN2 = [0, 4, 3, 0]

def key_lesson(lid, name, desc, tonic, steps, fifths, accidentals_text, mel_a, mel_b, bpm):
    return lesson(lid, desc, f"Le alterazioni di {name} e una breve melodia.", [
        txt("s1", f"{name}: cosa cambia", accidentals_text),
        scale_ex("s2", name, tonic, steps, fifths, bpm),
        melody_ex("s3", name, tonic, steps, fifths, bpm, mel_a, CH_PLAIN, f"La destra canta in {name}, la sinistra regge con gli accordi."),
        melody_ex("s4", name + ": seconda frase", tonic, steps, fifths, bpm, mel_b, CH_PLAIN2, "Un'altra frase nella stessa tonalità: ormai le alterazioni sono tue."),
    ])

course_keys = {"id": "piu-tonalita", "title": "Più tonalità", "lessons": [
    key_lesson("l1", "Re maggiore", "Re maggiore", 62, MAJ, 2,
               "Re maggiore ha due alterazioni: fa diesis e do diesis. Sono i tasti neri subito a destra del fa e del do. Se li ricordi in anticipo, la scala scorre da sola. Suona piano e ascolta il suono luminoso della tonalità.", MEL1, MEL2, 72),
    key_lesson("l2", "La maggiore", "La maggiore", 69, MAJ, 3,
               "La maggiore ha tre alterazioni: fa diesis, do diesis e sol diesis. Il pollice parte dal la e le dita centrali trovano i tasti neri. Procedi piano, finché la posizione della mano non diventa naturale.", MEL1, MEL3, 72),
    key_lesson("l3", "Si bemolle maggiore", "Si bemolle maggiore", 58, MAJ, -2,
               "Si bemolle maggiore ha due bemolli: si bemolle e mi bemolle. Sono i tasti neri subito a sinistra del si e del mi. La mano si adatta bene alla tastiera, perché le dita lunghe cadono sui tasti neri.", MEL2, MEL1, 66),
    key_lesson("l4", "Mi minore", "Mi minore", 64, MIN, 1,
               "Mi minore ha un solo diesis: il fa diesis. È la tonalità relativa di sol maggiore, cioè usa le stesse note ma parte dal mi. Il suono è più scuro e malinconico, anche se la mano si muove in modo simile.", MEL1, MEL3, 66),
    key_lesson("l5", "Re minore", "Re minore", 62, MIN, -1,
               "Re minore ha un solo bemolle: il si bemolle. È la relativa di fa maggiore. Ascolta come il solo bemolle cambia il carattere della scala: più grave, più intenso.", MEL2, MEL3, 66),
]}

# ----------------------------------------------------------------------------------------------
# 2. Accompagnamenti per la sinistra
# ----------------------------------------------------------------------------------------------
C3, F3, G3 = 48, 53, 55

def alberti(root, third, fifth, start, bars=1):
    out, b = [], start
    for _ in range(bars):
        for k in (root, fifth, third, fifth, root, fifth, third, fifth):
            out.append(N(b, 0.5, k, "L")); b += 0.5
    return out

alb_C = (48, 52, 55); alb_F = (41, 45, 48); alb_G = (43, 47, 50)
def alb(c, start): return alberti(c[0], c[1], c[2], start)

def alberti_prog(chs):
    out = []
    for i, c in enumerate(chs): out += alb(c, i * 4.0)
    return out

mel_alb1 = line("R", [(64, 2), (62, 2), (60, 2), (62, 2), (65, 2), (64, 2), (62, 2), (60, 2)])
mel_alb2 = line("R", [(67, 2), (65, 2), (64, 2), (62, 2), (64, 2), (62, 2), (60, 4)])

def octaves(root_keys, start_beat=0.0, dur=1.0):
    out, b = [], start_beat
    for r in root_keys:
        out += [N(b, dur, r, "L"), N(b, dur, r + 12, "L")]; b += dur
    return out

def boogie(root, start):
    pat = [0, 4, 7, 9, 10, 9, 7, 4]
    return [N(start + i * 0.5, 0.5, root + p, "L") for i, p in enumerate(pat)]

def walking(keys, start=0.0):
    return [N(start + i, 1.0, k, "L") for i, k in enumerate(keys)]

def stride(bass, ch, bars, start=0.0):
    out = []
    for i in range(bars):
        b0 = start + i * 4.0
        out += [N(b0, 1.0, bass[i], "L")] + chord("L", ch[i], b0 + 1, 1.0) + [N(b0 + 2, 1.0, bass[i] + 7, "L")] + chord("L", ch[i], b0 + 3, 1.0)
    return out

course_left = {"id": "sinistra-pattern", "title": "Pattern per la mano sinistra", "lessons": [
    lesson("l1", "Il basso di Alberti", "Accordo spezzato in crome: la sinistra scorre sotto la melodia.", [
        txt("s1", "Un accordo che scorre", "Il basso di Alberti suona le note di un accordo una alla volta: fondamentale, quinta, terza, quinta. È il modo classico di accompagnare con la sinistra. Tieni il polso morbido e le crome regolari, senza strappi."),
        ex("s2", "Alberti su Do", "Quattro battute dello stesso accordo, per prendere il movimento.", alberti_prog([alb_C, alb_C, alb_C, alb_C]), L_STEPS, 60),
        ex("s3", "Alberti e melodia", "La sinistra scorre sulle crome, la destra canta note lunghe.", alberti_prog([alb_C, alb_C, alb_F, alb_C]) + mel_alb1, B_STEPS, 60),
        ex("s4", "Alberti con tre accordi", "Do, Fa e Sol: la sinistra cambia posizione, il ritmo no.", alberti_prog([alb_C, alb_F, alb_G, alb_C]) + mel_alb2, B_STEPS, 60),
    ]),
    lesson("l2", "Ottave pulsanti", "La sinistra suona due ottave insieme: un basso potente.", [
        txt("s1", "Il suono pieno", "Suonare la stessa nota in due ottave dà un basso pieno e deciso. Allarga la mano senza irrigidirla: pollice e mignolo fanno il lavoro. Cerca un battito uguale in tutte le note."),
        ex("s2", "Ottave sul battito", "Una nota a battito, in ottava, salendo di grado.", octaves([48, 50, 52, 53, 55, 53, 52, 50, 48, 43, 48, 50, 52, 50, 48, 48]), L_STEPS, 66),
        ex("s3", "Ottave e melodia", "Il basso pulsa, la destra canta sopra.", octaves([36, 36, 41, 41, 43, 43, 36, 36], 0.0, 2.0) + line("R", [(64, 1), (65, 1), (67, 2), (69, 1), (67, 1), (65, 2), (67, 1), (64, 1), (62, 2), (60, 4)]), B_STEPS, 66),
    ]),
    lesson("l3", "Boogie-woogie", "Il motore del blues: un pattern saltellante da ripetere.", [
        txt("s1", "Il ritmo che cammina", "Il boogie-woogie ripete un pattern di otto crome sulla sinistra: fondamentale, terza, quinta, sesta e settima in discesa. Cambia solo la nota di partenza. Tienilo regolare, come un motore, e il blues parte da solo."),
        ex("s2", "Boogie in Do", "Il pattern sulle prime quattro battute, tutto su Do.", boogie(48, 0.0) + boogie(48, 4.0) + boogie(48, 8.0) + boogie(48, 12.0), L_STEPS, 84),
        ex("s3", "Boogie con tre accordi", "Do, Fa, Do, Sol: il pattern si sposta e il ritmo resta.", boogie(48, 0.0) + boogie(48, 4.0) + boogie(53, 8.0) + boogie(48, 12.0) + line("R", [(72, 4), (72, 4), (77, 4), (72, 4)]), B_STEPS, 84),
    ]),
    lesson("l4", "Il basso che cammina", "Una nota a battito, passo dopo passo.", [
        txt("s1", "Camminare sulle note", "Un basso che cammina suona una nota per battito, seguendo la scala o i salti dell'accordo. Dà movimento alla musica senza riempirla. Scegli bene la diteggiatura per non interrompere il passo."),
        ex("s2", "Camminata su Do", "Quattro battute: su per l'accordo di Do, giù per la scala.", walking([48, 52, 55, 52, 48, 50, 52, 53, 55, 53, 52, 50, 48, 52, 55, 48]), L_STEPS, 78),
        ex("s3", "Camminata e melodia", "La destra tiene note lunghe, la sinistra passeggia.", walking([48, 52, 55, 52, 53, 57, 60, 57, 55, 59, 62, 59, 48, 52, 55, 48]) + line("R", [(64, 4), (65, 4), (67, 4), (64, 4)]), B_STEPS, 78),
    ]),
    lesson("l5", "Basso e accordo", "La sinistra salta fra una nota bassa e un accordo.", [
        txt("s1", "Un passo e uno schiaffo", "Nello stile stride la sinistra alterna una nota bassa e un accordo più in alto. Il salto va deciso in anticipo con lo sguardo e con il polso. Comincia piano, quasi senza far suonare l'accordo, poi dai forza."),
        ex("s2", "Stride in Do", "Basso, accordo, quinta, accordo: sempre uguale.", stride([48, 48, 41, 43], [[60, 64, 67], [60, 64, 67], [60, 65, 69], [59, 62, 67]], 4), L_STEPS, 72),
        ex("s3", "Stride con melodia", "Il basso salta, la destra canta con calma.", stride([48, 48, 41, 43], [[60, 64, 67], [60, 64, 67], [60, 65, 69], [59, 62, 67]], 4) + line("R", [(76, 2), (74, 2), (72, 4), (77, 2), (76, 2), (74, 4)]), B_STEPS, 72),
    ]),
]}

# ----------------------------------------------------------------------------------------------
# 3. Ritmi di oggi
# ----------------------------------------------------------------------------------------------
def bass_q(keys, start=0.0):
    return [N(start + i, 1.0, k, "L") for i, k in enumerate(keys)]

def offbeats(keys_per_bar, start=0.0):
    out = []
    for i, ks in enumerate(keys_per_bar):
        for j, k in enumerate(ks):
            out.append(N(start + i * 4.0 + j + 0.5, 0.5, k, "R"))
    return out

def tresillo(keys_per_bar):
    out = []
    for i, ks in enumerate(keys_per_bar):
        b = i * 4.0
        out += [N(b, 1.5, ks[0], "R"), N(b + 1.5, 1.5, ks[1], "R"), N(b + 3.0, 1.0, ks[2], "R")]
    return out

def sixteenths(keys_per_beat, start=0.0):
    out, b = [], start
    for k in keys_per_beat:
        for j in range(4):
            out.append(N(b + j * 0.25, 0.25, k, "R"))
        b += 1.0
    return out

def run16(keys, start=0.0):
    return [N(start + i * 0.25, 0.25, k, "R") for i, k in enumerate(keys)]

def shuffle(keys_per_beat, start=0.0, hand="R"):
    out, b = [], start
    for a, c in keys_per_beat:
        out += [N(b, 0.666667, a, hand), N(b + 0.666667, 0.333333, c, hand)]
        b += 1.0
    return out

def funk(chord_keys, bars, start=0.0, hits=(0.0, 0.75, 1.5, 2.5, 3.5)):
    out = []
    for i in range(bars):
        for h in hits:
            out += chord("R", chord_keys[i], start + i * 4.0 + h, 0.25)
    return out

C_CH, F_CH = [60, 64, 67], [60, 65, 69]

course_rhythm = {"id": "ritmi-di-oggi", "title": "Ritmi di oggi", "lessons": [
    lesson("l1", "Il levare e il tresillo", "Suonare fra i battiti e dividere otto crome in 3+3+2.", [
        txt("s1", "Fra un battito e l'altro", "Il levare cade a metà fra due battiti: è la nota che parte quando il piede sale. Molta musica pop vive di questi colpi un po' fuori dal conto. Batti il tempo con il piede e suona fra un battito e l'altro."),
        ex("s2", "Note sul levare", "La sinistra batte il tempo, la destra suona solo fra i battiti.", bass_q([48, 48, 48, 48, 43, 43, 43, 43, 45, 45, 45, 45, 41, 41, 41, 41]) + offbeats([[64, 67, 64, 67], [62, 67, 62, 67], [64, 69, 64, 69], [65, 69, 65, 69]]), B_STEPS, 80),
        ex("s3", "Il ritmo 3+3+2", "Un ritmo molto usato: due note lunghe e una corta in ogni battuta.", bass_q([48, 48, 48, 48, 53, 53, 53, 53, 55, 55, 55, 55, 48, 48, 48, 48]) + tresillo([[64, 67, 64], [65, 69, 65], [62, 67, 71], [60, 64, 60]]), B_STEPS, 84),
    ]),
    lesson("l2", "Sedicesimi", "Quattro note per ogni battito: precisione e leggerezza.", [
        txt("s1", "Quattro per battito", "I sedicesimi dividono il battito in quattro parti uguali. Parti piano, con un metronomo lento, e ascolta che le note siano tutte uguali. Un tocco leggero e vicino ai tasti ti permette di arrivare lontano senza fatica."),
        ex("s2", "Quattro sulla stessa nota", "Ripetere la stessa nota in sedicesimi, cambiando a ogni battito.", sixteenths([60, 60, 62, 62, 64, 64, 62, 62, 60, 60, 59, 59, 60, 60, 60, 60]), R_STEPS, 56),
        ex("s3", "Scala in sedicesimi", "Una scala di Do veloce, ma leggera: un giro per battuta.", run16([60, 62, 64, 65, 67, 69, 71, 72, 72, 71, 69, 67, 65, 64, 62, 60]) + run16([60, 62, 64, 65, 67, 69, 71, 72, 72, 71, 69, 67, 65, 64, 62, 60], 4.0) + [N(8.0, 4.0, 60, "R")] + [N(12.0, 4.0, 60, "R")], R_STEPS, 56),
    ]),
    lesson("l3", "Swing e shuffle", "Il ritmo ternario: due crome dove la prima è più lunga.", [
        txt("s1", "Il ritmo saltellante", "Nello swing le due crome di un battito non sono uguali: la prima è lunga il doppio della seconda. È come dividere il battito in tre e suonare la prima e la terza parte. Dai un piccolo accento alla nota lunga e lasciati andare."),
        ex("s2", "Shuffle in Do", "Coppie lunga-breve su una scala semplice.", shuffle([(60, 62), (64, 62), (60, 62), (64, 60), (65, 64), (62, 64), (65, 64), (60, 60), (67, 65), (64, 65), (67, 65), (62, 62), (64, 62), (60, 62), (64, 62), (60, 60)]), R_STEPS, 84),
        ex("s3", "Shuffle con il basso", "La sinistra tiene il tempo, la destra swinga sopra.", bass_q([48, 48, 48, 48, 53, 53, 53, 53, 55, 55, 55, 55, 48, 48, 48, 48]) + shuffle([(72, 71), (72, 69), (72, 71), (72, 69), (77, 76), (77, 74), (77, 76), (77, 74), (79, 77), (79, 76), (79, 77), (79, 76), (72, 71), (72, 69), (72, 67), (72, 72)]), B_STEPS, 84),
    ]),
    lesson("l4", "Pausa e colpo", "Accordi corti e pause: lo stile funk.", [
        txt("s1", "Il silenzio fa ritmo", "Nel funk gli accordi sono brevissimi e le pause contano quanto le note. Ogni colpo va staccato in fretta, come se il tasto scottasse. Conta i sedicesimi dentro le pause, e il groove viene da solo."),
        ex("s2", "Colpi su Do", "Accordo di Do in cinque colpi per battuta.", funk([C_CH] * 4, 4) + [N(i * 4.0, 1.0, 48, "L") for i in range(4)], B_STEPS, 72),
        ex("s3", "Colpi su Do e Fa", "Due accordi che si alternano, sempre sullo stesso ritmo.", funk([C_CH, C_CH, F_CH, F_CH], 4) + [N(0.0, 1.0, 48, "L"), N(4.0, 1.0, 48, "L"), N(8.0, 1.0, 41, "L"), N(12.0, 1.0, 41, "L")], B_STEPS, 72),
    ]),
    lesson("l5", "Due contro tre", "Una mano suona in due, l'altra in tre: la coordinazione.", [
        txt("s1", "Il conto incrociato", "In due contro tre una mano divide la battuta in due parti uguali, l'altra in tre. Il primo colpo cade insieme, gli altri no. Conta ad alta voce 'uno-e-due-e-tre-e' e collega le note alle sillabe."),
        ex("s2", "Due contro tre", "La destra suona due note lunghe, la sinistra tre note per battuta.", [N(i * 3.0, 1.5, k, "R") for i, k in enumerate([67, 64, 65, 62, 64, 60, 62, 59])] [:8] + [N(i * 1.0, 1.0, k, "L") for i, k in enumerate([48, 52, 55] * 4)], B_SHORT, 66, ts=(3, 4)),
        ex("s3", "Tre contro quattro", "Tre note lunghe nella destra, quattro nella sinistra.", [N(i * (4 / 3), 4 / 3, k, "R") for i, k in enumerate([64, 67, 64] * 4)] + [N(i * 1.0, 1.0, k, "L") for i, k in enumerate([48, 52, 55, 52] * 4)], B_SHORT, 60),
    ]),
]}

# ----------------------------------------------------------------------------------------------
# 4. Canzoni con gli accordi
# ----------------------------------------------------------------------------------------------
GIRO = [[0, 4, 5, 3]]  # I V vi IV, come gradi della scala
CELLS_A = {"C": [64, 67, 64, 60], "G": [62, 67, 71, 67], "Am": [64, 69, 64, 60], "F": [65, 69, 65, 60]}
CELLS_B = {"C": [67, 64, 60, 64], "G": [67, 71, 74, 71], "Am": [69, 72, 69, 64], "F": [69, 65, 60, 65]}
CH = {"C": [48, 52, 55], "G": [43, 47, 50], "Am": [45, 48, 52], "F": [41, 45, 48]}

def giro(shift, fifths, names, title_suffix):
    seq = ["C", "G", "Am", "F"] * 2
    left, right = [], []
    for i, c in enumerate(seq):
        left += chord("L", [k + shift for k in CH[c]], i * 4.0, 4.0)
        cells = CELLS_A if i < 4 else CELLS_B
        right += [N(i * 4.0 + j, 1.0, k + shift, "R") for j, k in enumerate(cells[c])]
    return left, right

def giro_lesson(lid, name, shift, fifths, chords_text, bpm):
    left, right = giro(shift, fifths, None, name)
    return lesson(lid, f"Il giro in {name}", f"Quattro accordi che tornano, in {name}.", [
        txt("s1", f"Il giro in {name}", chords_text),
        ex("s2", f"Gli accordi in {name}", "Un accordo per battuta, otto battute: sentilo girare.", left, L_STEPS, bpm, fifths=fifths),
        ex("s3", f"Melodia sul giro in {name}", "La destra canta sopra gli accordi, la sinistra li tiene.", left + right, B_STEPS, bpm, fifths=fifths),
    ])

blues_left, blues_right = [], []
BL = [("C", [48, 55]), ("C", [48, 55]), ("C", [48, 55]), ("C", [48, 55]), ("F", [41, 48]), ("F", [41, 48]), ("C", [48, 55]), ("C", [48, 55]), ("G", [43, 50]), ("F", [41, 48]), ("C", [48, 55]), ("C", [48, 55])]
for i, (_, ks) in enumerate(BL):
    blues_left += [N(i * 4.0, 2.0, ks[0], "L"), N(i * 4.0 + 2.0, 2.0, ks[1], "L")]
BR = [[60, 63, 65, 63], [60, 63, 65, 66], [67, 65, 63, 60], [60, 60, 63, 60], [65, 68, 70, 68], [65, 63, 60, 63], [67, 66, 65, 63], [60, 63, 60, 60], [67, 70, 67, 65], [65, 68, 65, 63], [60, 63, 65, 63], [60, 4]]
for i, bar in enumerate(BR[:11]):
    blues_right += [N(i * 4.0 + j, 1.0, k, "R") for j, k in enumerate(bar)]
blues_right.append(N(44.0, 4.0, 60, "R"))

AND_CH = [("Am", [45, 48, 52]), ("G", [43, 47, 50]), ("F", [41, 45, 48]), ("E", [40, 44, 47])]
AND_MEL = [[64, 69, 72, 69], [62, 67, 71, 67], [60, 65, 69, 65], [59, 64, 68, 64]]
and_left, and_right = [], []
for rep in range(2):
    for i, (_, ks) in enumerate(AND_CH):
        b = (rep * 4 + i) * 4.0
        and_left += chord("L", ks, b, 4.0)
        and_right += [N(b + j, 1.0, k, "R") for j, k in enumerate(AND_MEL[i])] if rep == 0 else [N(b, 2.0, AND_MEL[i][0], "R"), N(b + 2.0, 2.0, AND_MEL[i][2], "R")]

course_songs = {"id": "canzoni-con-accordi", "title": "Canzoni con gli accordi", "lessons": [
    giro_lesson("l1", "Do maggiore", 0, 0, "Il giro più usato nel pop è Do, Sol, La minore, Fa: primo, quinto, sesto e quarto grado. Con questi quattro accordi si possono suonare centinaia di canzoni. Ascolta come l'ultimo accordo chiede di tornare all'inizio.", 80),
    giro_lesson("l2", "Sol maggiore", 7, 1, "Lo stesso giro si può suonare in ogni tonalità: in Sol maggiore diventa Sol, Re, Mi minore, Do. Le dita fanno gli stessi movimenti, ma partono da un altro punto. Attento al fa diesis, che tornerà spesso.", 80),
    giro_lesson("l3", "Fa maggiore", 5, -1, "In Fa maggiore il giro diventa Fa, Do, Re minore, Si bemolle. Il si bemolle è un tasto nero: la sinistra ci arriva con il pollice un po' ruotato. Dopo qualche ripetizione la mano troverà la posizione da sola.", 80),
    lesson("l4", "Il blues in 12 battute", "Dodici battute con tre accordi: la forma più famosa del blues.", [
        txt("s1", "Dodici battute", "Il blues dura dodici battute e usa tre accordi: Do, Fa e Sol. Si comincia con quattro battute su Do, poi Fa, di nuovo Do e un giro finale su Sol e Fa. Suonalo lento e pensa al ritmo, più che alle note."),
        ex("s2", "Il blues a sinistra", "Dodici battute: la sinistra segue gli accordi con fondamentale e quinta.", blues_left, L_STEPS, 72),
        ex("s3", "Il blues con la destra", "Una melodia con le note blues, sopra la sinistra.", blues_left + blues_right, B_STEPS, 72),
    ]),
    lesson("l5", "La cadenza andalusa", "La minore, Sol, Fa, Mi: una discesa famosa.", [
        txt("s1", "Una discesa", "Gli accordi La minore, Sol, Fa e Mi scendono come i gradini di una scala. È la cadenza andalusa, usata nel flamenco e in tantissime canzoni. Il Mi è maggiore e contiene un sol diesis: ascoltalo, perché dà tensione."),
        ex("s2", "Gli accordi andalusi", "Quattro accordi, due volte: la sinistra scende.", and_left, L_STEPS, 72),
        ex("s3", "Una melodia sulla cadenza", "La destra canta con note ribattute; nella seconda volta, note lunghe.", and_left + and_right, B_STEPS, 72),
    ]),
]}

# ----------------------------------------------------------------------------------------------
# 5. Tecnica quotidiana (schemi tradizionali di pubblico dominio, nello stile degli esercizi di Hanon)
# ----------------------------------------------------------------------------------------------
def eighths(hand, keys, start=0.0):
    return [N(start + i * 0.5, 0.5, k, hand) for i, k in enumerate(keys)]

def hanon_cells(tonic, order, degrees, hand_shift=0):
    """Ogni cella è un giro di 8 crome costruito sulla scala (gradi relativi alla cella)."""
    sc = scale(tonic + hand_shift, MAJ, 20)
    out = []
    for k in order:
        out += [sc[k + d] for d in degrees]
    return out

H1 = [0, 2, 3, 4, 5, 4, 3, 2]   # primo schema: salita sulle prime cinque dita e ritorno
H2 = [0, 1, 2, 3, 4, 3, 2, 1]   # cinque dita in ordine
H3 = [0, 2, 1, 3, 2, 4, 3, 1]   # dita alternate

def hanon_ex(eid, title, text, degrees, order, bpm, with_left=True):
    r = eighths("R", hanon_cells(60, order, degrees))
    l = eighths("L", hanon_cells(60, order, degrees, hand_shift=-12))
    bars = len(order)
    end = bars * 4.0
    right = r + [N(end, 4.0, scale(60, MAJ, 20)[order[-1]], "R")]
    left = l + [N(end, 4.0, scale(48, MAJ, 20)[order[-1]], "L")]
    steps = B_STEPS if with_left else R_STEPS
    return ex(eid, title, text, right + (left if with_left else []), steps, bpm)

def thirds_ex(eid, title, text, bpm, tonic=60):
    sc = scale(tonic, MAJ, 16)
    seq = [(sc[i], sc[i + 2]) for i in range(0, 7)] + [(sc[i], sc[i + 2]) for i in range(6, -1, -1)]
    notes = []
    for j, (a, b) in enumerate(seq):
        notes += [N(j * 1.0, 1.0, a, "R"), N(j * 1.0, 1.0, b, "R")]
    notes += [N(len(seq) * 1.0, 2.0, sc[0], "R"), N(len(seq) * 1.0, 2.0, sc[2], "R")]
    left = [N(i * 2.0, 2.0, tonic - 12 + off, "L") for i, off in enumerate([0, 0, 5, 5, 7, 7, 0, 0])]
    return ex(eid, title, text, notes + left, B_STEPS, bpm, fifths=1 if tonic == 55 else 0)

def scale_oct_ex(eid, title, text, bpm, tonic=60, steps=MAJ, fifths=0):
    sc = scale(tonic, steps, 16)
    up = sc[:15]
    seq = up + list(reversed(up[:-1]))
    r = eighths("R", seq)
    l = eighths("L", [k - 12 for k in seq])
    end = len(seq) * 0.5
    return ex(eid, title, text, r + l + [N(end, 4.0, sc[0], "R"), N(end, 4.0, sc[0] - 12, "L")], B_STEPS, bpm, fifths=fifths)

def arpeggio_ex(eid, title, text, bpm, tonic, steps, fifths=0):
    sc = scale(tonic, steps, 16)
    idx = [0, 2, 4, 7, 9, 11, 14]
    up = [sc[i] for i in idx]
    seq = up + list(reversed(up[:-1]))
    seq = seq + seq  # due giri
    r = eighths("R", seq)
    l = eighths("L", [k - 12 for k in seq])
    end = len(seq) * 0.5
    return ex(eid, title, text, r + l + [N(end, 4.0, up[0], "R"), N(end, 4.0, up[0] - 12, "L")], B_STEPS, bpm, fifths=fifths)

course_tech = {"id": "tecnica-quotidiana", "title": "Tecnica quotidiana", "lessons": [
    lesson("l1", "Cinque dita regolari", "Il classico esercizio che sale di grado: dita uguali, suono uguale.", [
        txt("s1", "Dita uguali", "Questi schemi sono tradizionali e di pubblico dominio, nello stile degli esercizi di Hanon. Ogni giro di otto crome si ripete partendo da un grado più alto. Cerca un suono uguale in tutte le dita: il quarto e il quinto dito sono i più deboli, quindi dagli attenzione."),
        hanon_ex("s2", "Salita in cinque dita", "Quattro giri che salgono di grado, le due mani all'ottava.", H1, [0, 1, 2, 3], 66),
        hanon_ex("s3", "Salita e discesa", "Sali per quattro giri e poi scendi: otto battute.", H1, [0, 1, 2, 3, 2, 1, 0], 66),
    ]),
    lesson("l2", "Cinque dita in ordine", "Un altro schema sulle stesse cinque note.", [
        txt("s1", "Un'altra forma", "Lo stesso gesto può cambiare forma: dita in ordine, dita alternate. Il polso resta morbido e il braccio accompagna senza irrigidirsi. Se senti tensione, fermati e ricomincia più piano."),
        hanon_ex("s2", "Dita in ordine", "Uno, due, tre, quattro, cinque e ritorno: sempre uguale.", H2, [0, 1, 2, 3], 66),
        hanon_ex("s3", "Dita alternate", "Un ordine meno ovvio: ogni dito deve sapere dove andare.", H3, [0, 1, 2, 3, 2, 1, 0], 60),
    ]),
    lesson("l3", "Terze", "Due note insieme, in movimento di scala.", [
        txt("s1", "Terze in scala", "Le terze sono due note che si suonano insieme, a distanza di tre note nella scala. La mano deve restare ferma e compatta, senza aprirsi e chiudersi. Premi le due dita nello stesso momento: se non lo fanno, abbassa la velocità."),
        thirds_ex("s2", "Terze di Do", "Terze che salgono e scendono, sulla scala di Do.", 60),
        thirds_ex("s3", "Terze di Sol", "Le stesse terze in Sol maggiore: attenzione al fa diesis.", 60, tonic=55),
    ]),
    lesson("l4", "Scale in due ottave", "Passare il pollice senza che si senta.", [
        txt("s1", "Il pollice scivola", "Per suonare una scala lunga il pollice passa sotto la mano. Il movimento va preparato con un po' di anticipo, senza spostare il polso. La scala ben fatta suona come un unico filo."),
        scale_oct_ex("s2", "Scala di Do in due ottave", "Le due mani all'ottava: sali e scendi per due ottave.", 60),
        scale_oct_ex("s3", "Scala di Sol in due ottave", "Occhio al fa diesis: le dita lo ritrovano da sole.", 60, tonic=55),
    ]),
    lesson("l5", "Arpeggi in due ottave", "Accordi spezzati che girano su tutta la tastiera.", [
        txt("s1", "L'accordo che gira", "L'arpeggio suona le note di un accordo una alla volta. Il pollice passa sotto la mano dopo la terza o la quinta nota. Il gomito accompagna il movimento verso l'esterno e poi torna dentro."),
        arpeggio_ex("s2", "Arpeggio di Do", "Do, mi, sol e di nuovo do: due ottave, su e giù.", 60, 60, MAJ),
        arpeggio_ex("s3", "Arpeggio di La minore", "Lo stesso gesto con un suono più scuro.", 60, 57, MIN),
    ]),
]}

# ----------------------------------------------------------------------------------------------
# 6. Scale e arpeggi in ogni tonalità
# ----------------------------------------------------------------------------------------------
MAJ_KEYS = {"Do": (0, 0), "Sol": (7, 1), "Re": (2, 2), "La": (9, 3), "Mi": (4, 4), "Si": (11, 5), "Fa diesis": (6, 6),
            "Fa": (5, -1), "Si bemolle": (10, -2), "Mi bemolle": (3, -3), "La bemolle": (8, -4), "Re bemolle": (1, -5)}
MIN_KEYS = {"La": (9, 0), "Mi": (4, 1), "Si": (11, 2), "Fa diesis": (6, 3), "Do diesis": (1, 4), "Sol diesis": (8, 5),
            "Re": (2, -1), "Sol": (7, -2), "Do": (0, -3), "Fa": (5, -4), "Si bemolle": (10, -5)}

def base_of(pc):
    """La tonica nella fascia 49..60: due ottave verso l'alto restano sulla tastiera e la sinistra sotto di un'ottava."""
    return 49 + ((pc - 49) % 12)

def scale_in(eid, name, table, steps, label, bpm=60):
    pc, fifths = table[name]
    return scale_oct_ex(eid, f"Scala di {name} {label}", "Due mani all'ottava, due ottave su e giù: il pollice passa sotto con calma.", bpm, tonic=base_of(pc), steps=steps, fifths=fifths)

def arp_in(eid, name, table, steps, label, bpm=60):
    pc, fifths = table[name]
    return arpeggio_ex(eid, f"Arpeggio di {name} {label}", "Le note dell'accordo una alla volta, due ottave su e giù.", bpm, base_of(pc), steps, fifths=fifths)

course_scales = {"id": "scale-arpeggi-tonalita", "title": "Scale e arpeggi in ogni tonalità", "lessons": [
    lesson("l1", "Maggiori con i diesis", "Re, La, Mi, Si: ogni tonalità aggiunge un diesis.", [
        txt("s1", "Un diesis in più", "Ogni scala maggiore con i diesis ne ha uno in più della precedente: Re ne ha due, La tre, Mi quattro, Si cinque. I diesis cadono sui tasti neri: dopo un po' la mano li trova senza guardare. Suona piano e ascolta che tutte le note siano uguali."),
        *[scale_in(f"s{i + 2}", n, MAJ_KEYS, MAJ, "maggiore") for i, n in enumerate(["Re", "La", "Mi", "Si"])],
    ]),
    lesson("l2", "Maggiori con i bemolli", "Fa, Si bemolle, Mi bemolle, La bemolle.", [
        txt("s1", "I bemolli", "Nelle tonalità con i bemolli ogni scala ne ha uno in più: Fa ne ha uno, Si bemolle due, Mi bemolle tre, La bemolle quattro. Anche i bemolli sono tasti neri, ma a sinistra delle note. Il pollice passa sotto la mano senza forzare, con il gomito morbido."),
        *[scale_in(f"s{i + 2}", n, MAJ_KEYS, MAJ, "maggiore") for i, n in enumerate(["Fa", "Si bemolle", "Mi bemolle", "La bemolle"])],
    ]),
    lesson("l3", "Minori naturali", "Mi, Si, Re, Sol: suono più scuro.", [
        txt("s1", "La scala minore", "La scala minore naturale ha un suono più scuro, perché la terza, la sesta e la settima sono più basse. Usa le stesse alterazioni della maggiore relativa: Mi minore è come Sol maggiore, ma parte dal mi. Cambia il punto di partenza, non le note."),
        *[scale_in(f"s{i + 2}", n, MIN_KEYS, MIN, "minore naturale") for i, n in enumerate(["Mi", "Si", "Re", "Sol"])],
    ]),
    lesson("l4", "Minori armoniche", "La settima si alza di un semitono.", [
        txt("s1", "La settima alzata", "Nella scala minore armonica la settima nota sale di un semitono. Si crea così un salto di tre semitoni fra la sesta e la settima, che dà il caratteristico suono orientale. Fai attenzione alla settima alterata: è facile dimenticarla."),
        *[scale_in(f"s{i + 2}", n, MIN_KEYS, HARM, "minore armonica") for i, n in enumerate(["La", "Mi", "Re", "Do"])],
    ]),
    lesson("l5", "Arpeggi maggiori", "Sol, Re, Fa, Si bemolle.", [
        txt("s1", "Gli arpeggi", "L'arpeggio suona le note dell'accordo una alla volta, su due ottave. Il pollice passa sotto la mano dopo la terza o la quinta nota, e il polso accompagna con un movimento circolare. Vai piano, finché i passaggi non diventano silenziosi."),
        *[arp_in(f"s{i + 2}", n, MAJ_KEYS, MAJ, "maggiore") for i, n in enumerate(["Sol", "Re", "Fa", "Si bemolle"])],
    ]),
    lesson("l6", "Arpeggi minori", "Mi, Re, Sol, Do.", [
        txt("s1", "Arpeggi con la terza minore", "Gli arpeggi minori hanno la terza abbassata di un semitono rispetto a quelli maggiori. La mano resta quasi uguale, ma le dita cadono su tasti diversi. Suona con la stessa regolarità e ascolta come cambia il carattere."),
        *[arp_in(f"s{i + 2}", n, MIN_KEYS, MIN, "minore") for i, n in enumerate(["Mi", "Re", "Sol", "Do"])],
    ]),
]}

# più schemi nello stile Hanon: trilli, cromatica, sinistra sola
def trill_ex(eid, title, text, bpm, hi=62, lo=60):
    notes = []
    for i in range(64):
        notes.append(N(i * 0.25, 0.25, lo if i % 2 == 0 else hi, "R"))
    notes += [N(b * 4.0, 4.0, 48, "L") for b in range(4)]
    return ex(eid, title, text, notes, B_STEPS, bpm)

def chromatic_ex(eid, title, text, bpm):
    seq = list(range(60, 73)) + list(range(71, 59, -1))
    r = eighths("R", seq)
    l = eighths("L", [k - 12 for k in seq])
    end = len(seq) * 0.5
    return ex(eid, title, text, r + l + [N(end, 4.0, 60, "R"), N(end, 4.0, 48, "L")], B_STEPS, bpm)

course_tech["lessons"].append(lesson("l6", "Trilli, cromatica e sinistra sola", "Tre schemi in più per le dita.", [
    txt("s1", "Altri schemi", "Questi schemi allenano la velocità e l'indipendenza delle dita. Il trillo alterna due note vicine in sedicesimi: il polso resta rilassato e il movimento viene dalle dita. La scala cromatica usa tutti i tasti, bianchi e neri. L'ultimo schema allena la sinistra da sola."),
    trill_ex("s2", "Trillo su due note", "Due note vicine, in sedicesimi regolari, con la sinistra che tiene il Do.", 56),
    chromatic_ex("s3", "Scala cromatica", "Tutti i tasti dal Do al Do, bianchi e neri, su e giù.", 60),
    hanon_ex("s4", "Cinque dita, solo sinistra", "Lo schema di salita, solo con la sinistra: la mano debole lavora di più.", H1, [0, 1, 2, 3], 66, with_left=False),
]))
# l'ultimo esercizio usa la mano destra: lo si rifà con la sinistra sola
_last = course_tech["lessons"][-1]["slides"][-1]
for _n in _last["song"]["notes"]:
    _n["hand"] = "L"; _n["key"] -= 12
_last["steps"] = L_STEPS

# ----------------------------------------------------------------------------------------------
# 7. Accordi in tutte le tonalità
# ----------------------------------------------------------------------------------------------
QUAL = {"maj": (0, 4, 7), "min": (0, 3, 7), "dim": (0, 3, 6), "aug": (0, 4, 8), "7": (0, 4, 7, 10), "maj7": (0, 4, 7, 11), "m7": (0, 3, 7, 10)}
PC = {"Do": 0, "Do diesis": 1, "Re": 2, "Mi bemolle": 3, "Mi": 4, "Fa": 5, "Fa diesis": 6, "Sol": 7, "La bemolle": 8, "La": 9, "Si bemolle": 10, "Si": 11}

def chord_keys(root_pc, q, inv=0):
    r = 55 + ((root_pc - 55) % 12)
    ks = [r + i for i in QUAL[q]]
    for _ in range(inv):
        ks = ks[1:] + [ks[0] + 12]
    return ks, r

def chord_ex(eid, title, text, prog, bpm=60, fifths=0):
    """prog: lista di (nome_radice, qualità, rivolto). Un accordo per battuta, a destra; la fondamentale a sinistra."""
    prog = prog if len(prog) >= 4 else prog * 2
    right, left = [], []
    for i, (name, q, inv) in enumerate(prog):
        ks, r = chord_keys(PC[name], q, inv)
        right += [N(i * 4.0, 4.0, k, "R") for k in ks]
        left.append(N(i * 4.0, 4.0, r - 12, "L"))
    return ex(eid, title, text, right + left, B_STEPS, bpm, fifths=fifths)

def ch(*items):
    return [(n, q, 0) for n, q in items]

course_chords = {"id": "accordi-tonalita", "title": "Accordi in ogni tonalità", "lessons": [
    lesson("l1", "Triadi maggiori", "Tre note a distanza di terza: il suono luminoso.", [
        txt("s1", "La triade maggiore", "Una triade maggiore ha tre note: la fondamentale, la terza maggiore e la quinta. Si suona con i diti uno, tre e cinque, tenendo la mano arrotondata. Premi le tre note nello stesso istante, senza far sentire un arpeggio."),
        chord_ex("s2", "Do, Fa, Sol", "Tre accordi maggiori e il ritorno al Do.", ch(("Do", "maj"), ("Fa", "maj"), ("Sol", "maj"), ("Do", "maj")), fifths=0),
        chord_ex("s3", "Re, Sol, La", "Gli stessi gradi in Re maggiore.", ch(("Re", "maj"), ("Sol", "maj"), ("La", "maj"), ("Re", "maj")), fifths=2),
        chord_ex("s4", "Mi bemolle, La bemolle, Si bemolle", "Gli stessi gradi in Mi bemolle maggiore: tasti neri sotto le dita.", ch(("Mi bemolle", "maj"), ("La bemolle", "maj"), ("Si bemolle", "maj"), ("Mi bemolle", "maj")), fifths=-3),
    ]),
    lesson("l2", "Triadi minori", "La terza si abbassa: il suono si fa più scuro.", [
        txt("s1", "Il colore minore", "Una triade minore ha la terza abbassata di un semitono. La mano resta quasi uguale, ma un dito cade su un tasto diverso. Ascolta con attenzione la differenza di colore rispetto agli accordi maggiori."),
        chord_ex("s2", "La, Re, Mi minori", "I tre accordi minori principali di La minore.", ch(("La", "min"), ("Re", "min"), ("Mi", "min"), ("La", "min")), fifths=0),
        chord_ex("s3", "Mi, La, Si minori", "Gli stessi gradi in Mi minore.", ch(("Mi", "min"), ("La", "min"), ("Si", "min"), ("Mi", "min")), fifths=1),
        chord_ex("s4", "Re, Sol, La minori", "Gli stessi gradi in Re minore.", ch(("Re", "min"), ("Sol", "min"), ("La", "min"), ("Re", "min")), fifths=-1),
    ]),
    lesson("l3", "Diminuite e aumentate", "Quattro colori su una stessa nota.", [
        txt("s1", "Due colori nuovi", "La triade diminuita ha due terze minori: suona tesa e instabile. La triade aumentata ha due terze maggiori: suona sospesa e un po' misteriosa. Insieme agli accordi maggiore e minore formano i quattro tipi di triade."),
        chord_ex("s2", "Quattro colori su Do", "Do maggiore, minore, diminuito e aumentato.", [("Do", "maj", 0), ("Do", "min", 0), ("Do", "dim", 0), ("Do", "aug", 0)]),
        chord_ex("s3", "Quattro colori su Sol", "La stessa sequenza partendo da Sol.", [("Sol", "maj", 0), ("Sol", "min", 0), ("Sol", "dim", 0), ("Sol", "aug", 0)]),
        chord_ex("s4", "Il Si diminuito", "L'accordo che precede il Do maggiore: tensione e risoluzione.", [("Si", "dim", 0), ("Do", "maj", 0), ("Si", "dim", 0), ("Do", "maj", 0)]),
    ]),
    lesson("l4", "Rivolti", "Le stesse note, in un altro ordine.", [
        txt("s1", "Cambiare la nota più bassa", "Un rivolto usa le stesse note della triade, ma cambia quale sta in basso. Il primo rivolto parte dalla terza, il secondo dalla quinta. I rivolti permettono di spostarsi poco fra un accordo e l'altro, con la mano più comoda."),
        chord_ex("s2", "Sol in tre posizioni", "Fondamentale, primo rivolto, secondo rivolto e ritorno.", [("Sol", "maj", 0), ("Sol", "maj", 1), ("Sol", "maj", 2), ("Sol", "maj", 0)], fifths=1),
        chord_ex("s3", "Fa in tre posizioni", "Lo stesso gesto su Fa maggiore.", [("Fa", "maj", 0), ("Fa", "maj", 1), ("Fa", "maj", 2), ("Fa", "maj", 0)], fifths=-1),
        chord_ex("s4", "Re minore in tre posizioni", "I rivolti di una triade minore.", [("Re", "min", 0), ("Re", "min", 1), ("Re", "min", 2), ("Re", "min", 0)], fifths=-1),
    ]),
    lesson("l5", "Accordi di settima", "Una quarta nota per più colore.", [
        txt("s1", "La settima", "Aggiungendo alla triade una quarta nota, a distanza di settima, si ottiene un accordo di settima. La settima di dominante suona instabile e chiede di risolvere. La settima maggiore suona morbida e jazz. Tieni sempre le quattro note insieme."),
        chord_ex("s2", "Sol settima verso Do", "L'accordo di dominante che risolve sulla tonica.", [("Sol", "7", 0), ("Do", "maj", 0), ("Sol", "7", 0), ("Do", "maj", 0)]),
        chord_ex("s3", "Do settima verso Fa", "La stessa idea, un passo più in là.", [("Do", "7", 0), ("Fa", "maj", 0), ("Do", "7", 0), ("Fa", "maj", 0)], fifths=-1),
        chord_ex("s4", "Do maggiore settima e La minore settima", "Due accordi di settima morbidi, alternati.", [("Do", "maj7", 0), ("La", "m7", 0), ("Do", "maj7", 0), ("La", "m7", 0)]),
    ]),
    lesson("l6", "Cadenze", "Le sequenze che concludono una frase.", [
        txt("s1", "Concludere", "La cadenza è la sequenza di accordi che chiude una frase musicale. La più comune parte dalla tonica, va alla sottodominante e alla dominante, e torna alla tonica. In jazz si usa spesso il giro due-cinque-uno, con accordi di settima."),
        chord_ex("s2", "Cadenza in Re", "Re, Sol, La, Re.", ch(("Re", "maj"), ("Sol", "maj"), ("La", "maj"), ("Re", "maj")), fifths=2),
        chord_ex("s3", "Cadenza in La", "La, Re, Mi, La.", ch(("La", "maj"), ("Re", "maj"), ("Mi", "maj"), ("La", "maj")), fifths=3),
        chord_ex("s4", "Cadenza in Si bemolle", "Si bemolle, Mi bemolle, Fa, Si bemolle.", ch(("Si bemolle", "maj"), ("Mi bemolle", "maj"), ("Fa", "maj"), ("Si bemolle", "maj")), fifths=-2),
        chord_ex("s5", "Il giro due-cinque-uno in Fa", "Sol minore settima, Do settima, Fa maggiore settima.", [("Sol", "m7", 0), ("Do", "7", 0), ("Fa", "maj7", 0), ("Fa", "maj7", 0)], fifths=-1),
    ]),
]}

# ----------------------------------------------------------------------------------------------
# 8. Principianti: due mani insieme e accordi (livello 1)
# ----------------------------------------------------------------------------------------------
BEG_B = [step("B", "wait", 1.0, 1, "Le due mani insieme, senza fretta: premi quando compaiono le note."),
         step("B", "tempo", 0.5, 2, "Ora a tempo, molto piano."),
         step("B", "tempo", 0.75, 2, "Un po' più veloce."),
         step("B", "tempo", 1.0, 1, "Velocità normale.")]
BEG_SPLIT = [step("L", "wait", 1.0, 1, "Prima la sinistra da sola."),
             step("R", "wait", 1.0, 1, "Ora la destra da sola."),
             step("B", "wait", 1.0, 1, "Le due mani insieme, senza fretta."),
             step("B", "tempo", 0.5, 2, "Ora a tempo, molto piano.")]
BEG_R = [step("R", "wait", 1.0, 1, "Premi tutte le note dell'accordo nello stesso momento."),
         step("R", "tempo", 0.5, 2, "Ora a tempo, molto piano."),
         step("R", "tempo", 0.75, 2, "Un po' più veloce.")]

def q4(hand, keys, start=0.0, dur=1.0):
    return [N(start + i * dur, dur, k, hand) for i, k in enumerate(keys)]

def stack(hand, keys_per_bar, bar_len=4.0, start=0.0):
    out = []
    for i, ks in enumerate(keys_per_bar):
        out += [N(start + i * bar_len, bar_len, k, hand) for k in ks]
    return out

C_T, F_T, G_T, A_M = [60, 64, 67], [60, 65, 69], [59, 62, 67], [57, 60, 64]

course_begin2 = {"id": "prime-due-mani", "title": "Prime due mani", "lessons": [
    lesson("l1", "Le due mani insieme", "Le prime note suonate con tutte e due le mani.", [
        txt("s1", "Mani distanti, stesso suono", "Per cominciare suoniamo la stessa nota con tutte e due le mani, a distanza di un'ottava. Il pollice di ogni mano sta su un Do, e le dita non devono tendersi. Premi le due note nello stesso momento e ascolta se suonano come una sola."),
        ex("s2", "Do con tutte e due", "Una nota intera per battuta, le due mani insieme.", stack("R", [[60], [64], [67], [60]]) + stack("L", [[48], [52], [55], [48]]), BEG_B, 54),
        ex("s3", "Su e giù, insieme", "Quattro note, le due mani nello stesso ordine.", q4("R", [60, 62, 64, 65, 67, 65, 64, 62, 64, 62, 60, 62]) + q4("L", [48, 50, 52, 53, 55, 53, 52, 50, 52, 50, 48, 50]) + [N(12.0, 4.0, 60, "R"), N(12.0, 4.0, 48, "L")], BEG_B, 60),
    ]),
    lesson("l2", "Una mano tiene, l'altra suona", "La sinistra ferma sulla nota, la destra cammina.", [
        txt("s1", "Un'attenzione per volta", "Quando una mano tiene una nota lunga e l'altra suona, è facile far partire entrambe le mani insieme per sbaglio. Concentrati sul tenere premuto il dito della mano ferma. Conta i quattro battiti: la mano che tiene non si muove fino alla fine della battuta."),
        ex("s2", "La sinistra tiene il Do", "La destra suona quattro note sopra una nota lunga.", stack("L", [[48], [48], [48], [48]]) + q4("R", [60, 62, 64, 65, 67, 65, 64, 62, 64, 62, 60, 62, 60, 60, 60, 60]), BEG_SPLIT, 54),
        ex("s3", "La destra tiene il Sol", "Ora la nota lunga è nella destra e la sinistra cammina.", stack("R", [[67], [67], [67], [67]]) + q4("L", [48, 50, 52, 53, 55, 53, 52, 50, 48, 50, 52, 50, 48, 48, 48, 48]), BEG_SPLIT, 54),
    ]),
    lesson("l3", "A turno", "Prima una mano, poi l'altra.", [
        txt("s1", "Domanda e risposta", "Suonando a turno le mani non sono mai insieme, ma devono passare il testimone senza esitare. La mano che ha finito resta pronta sulla tastiera, perché l'altra possa partire subito. Cerca un ritmo regolare, come se suonasse una sola persona."),
        ex("s2", "Passo a passo, a turno", "Una battuta alla destra, una alla sinistra.", q4("R", [60, 62, 64, 62]) + q4("L", [48, 50, 52, 50], 4.0) + q4("R", [64, 65, 67, 65], 8.0) + q4("L", [53, 52, 50, 48], 12.0), BEG_SPLIT, 60),
        ex("s3", "Il rimbalzo", "Una nota alla destra, una alla sinistra, sempre alternate.", [N(i * 1.0, 1.0, 60 + (i % 4) if False else (64 if i % 2 == 0 else 48), "R" if i % 2 == 0 else "L") for i in range(16)], BEG_SPLIT, 60),
    ]),
    lesson("l4", "Le dita che salgono e scendono", "Cinque note, due mani, due direzioni.", [
        txt("s1", "Insieme e in contrario", "Nel moto parallelo le due mani vanno nella stessa direzione. Nel moto contrario una sale e l'altra scende, e si incontrano o si allontanano. Il secondo è più difficile, perché il cervello vorrebbe far fare lo stesso movimento a tutte e due."),
        ex("s2", "Cinque note, due mani", "Salita e discesa in parallelo.", q4("R", [60, 62, 64, 65, 67, 65, 64, 62, 64, 62, 60, 62]) + q4("L", [48, 50, 52, 53, 55, 53, 52, 50, 52, 50, 48, 50]) + [N(12.0, 4.0, 60, "R"), N(12.0, 4.0, 48, "L")], BEG_B, 56),
        ex("s3", "Opposti", "La destra sale, la sinistra scende: moto contrario.", q4("R", [60, 62, 64, 65, 67, 65, 64, 62, 64, 62, 60, 62]) + q4("L", [48, 47, 45, 43, 41, 43, 45, 47, 45, 47, 48, 47]) + [N(12.0, 4.0, 60, "R"), N(12.0, 4.0, 48, "L")], BEG_B, 56),
    ]),
    lesson("l5", "I primi accordi a sinistra", "La sinistra suona più note insieme.", [
        txt("s1", "Più note, un solo gesto", "Con la sinistra si suonano spesso due o tre note insieme. Qui cominciamo con le note Do e Sol insieme, poi con l'accordo di Do. Tieni le dita curve e premi tutte le note nello stesso istante, come se fosse un tasto solo."),
        ex("s2", "Do e Sol sotto la melodia", "La sinistra suona due note insieme, la destra una melodia lenta.", stack("L", [[48, 55]] * 4) + q4("R", [64, 67, 64, 60, 62, 64, 62, 60, 64, 67, 69, 67, 64, 62, 60, 60]) , BEG_SPLIT, 54),
        ex("s3", "L'accordo di Do e quello di Sol", "Due accordi che si alternano sotto una melodia.", stack("L", [[48, 52, 55], [43, 47, 50], [48, 52, 55], [48, 52, 55]]) + q4("R", [67, 67, 64, 64, 62, 62, 59, 62, 64, 64, 60, 60, 60, 62, 64, 60]), BEG_SPLIT, 54),
    ]),
    lesson("l6", "Tre piccoli brani", "Mettiamo insieme quello che sai.", [
        txt("s1", "Suonare un brano", "Qui si fa musica vera con tutte e due le mani. Se un passaggio è difficile, fermati e rifallo da solo prima di ripartire. Tieni lo sguardo sullo spartito, non sulle mani."),
        ex("s2", "Passeggiata", "Una melodia tranquilla con un basso regolare.", q4("R", [64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62, 62]) + stack("L", [[48, 55], [48, 55], [48, 55], [43, 50]], 4.0), BEG_SPLIT, 60),
        ex("s3", "Ninna nanna a due mani", "Tre quarti, dolce e lenta.", [N(i * 3.0, 3.0, k, "R") for i, k in enumerate([64, 62, 60, 62, 64, 64, 64, 62])] + [N(i * 3.0, 3.0, k, "L") for i, k in enumerate([48, 55, 48, 55, 48, 55, 48, 55])], BEG_SPLIT, 54, ts=(3, 4)),
        ex("s4", "Piccola marcia", "Quattro quarti decisi, mani insieme.", q4("R", [60, 60, 64, 64, 67, 67, 64, 64, 65, 65, 62, 62, 60, 60, 60, 60]) + [N(i * 2.0, 2.0, k, "L") for i, k in enumerate([48, 55, 48, 55, 53, 55, 48, 48])], BEG_SPLIT, 66),
    ]),
]}
# il «rimbalzo» alterna le mani: destra su Mi, sinistra su Do
for _n in course_begin2["lessons"][2]["slides"][2]["song"]["notes"]:
    _n["key"] = 64 if _n["hand"] == "R" else 48

course_begin_chords = {"id": "accordi-per-cominciare", "title": "Accordi per cominciare", "lessons": [
    lesson("l1", "Due note insieme", "Terze e quinte con la mano destra.", [
        txt("s1", "Premere insieme", "Prima di suonare un accordo intero, si impara a premere due note insieme. La terza è l'intervallo fra due note a distanza di una nota intermedia, la quinta con tre note intermedie. Tieni la mano rilassata e premi le due dita nello stesso istante."),
        ex("s2", "Terze vicine", "Due note a distanza di terza, salendo.", [N(i * 2.0, 2.0, k, "R") for i, (a, b) in enumerate([(60, 64), (62, 65), (64, 67), (65, 69), (67, 71), (65, 69), (64, 67), (62, 65)]) for k in (a, b)], BEG_R, 54),
        ex("s3", "Quinte vuote", "Do e Sol insieme, poi Re e La, poi Mi e Si.", [N(i * 4.0, 4.0, k, "R") for i, (a, b) in enumerate([(60, 67), (62, 69), (64, 71), (60, 67)]) for k in (a, b)], BEG_R, 54),
    ]),
    lesson("l2", "La triade di Do", "Tre note insieme: Do, Mi, Sol.", [
        txt("s1", "Il primo accordo", "L'accordo di Do maggiore si suona con Do, Mi e Sol insieme, con i diti uno, tre e cinque della mano destra. Tieni le tre note premute per tutta la battuta. Se una nota parte prima delle altre, ripeti più piano."),
        ex("s2", "Do maggiore a destra", "Accordo di Do: premi, tieni, rilascia.", stack("R", [C_T, C_T, C_T, C_T], 4.0), BEG_R, 54),
        ex("s3", "Do con il basso", "L'accordo a destra e una nota bassa a sinistra.", stack("R", [C_T] * 4) + stack("L", [[48]] * 4), BEG_B, 54),
    ]),
    lesson("l3", "Do e Sol", "Due accordi che si alternano.", [
        txt("s1", "Cambiare accordo", "Il secondo accordo da imparare è Sol maggiore, con Sol, Si e Re. Fra Do e Sol la mano si muove poco: due note restano vicine. Prepara le dita in anticipo, senza guardare la tastiera."),
        ex("s2", "Do, Sol, Do, Sol", "Un accordo ogni battuta, a destra.", stack("R", [C_T, G_T, C_T, G_T]), BEG_R, 54),
        ex("s3", "Do e Sol con il basso", "Aggiungi la nota bassa a sinistra.", stack("R", [C_T, G_T, C_T, G_T]) + stack("L", [[48], [43], [48], [43]]), BEG_B, 54),
    ]),
    lesson("l4", "Do, Fa, Sol", "I tre accordi principali.", [
        txt("s1", "Tre accordi", "Con Do, Fa e Sol si suonano moltissime canzoni. Il Fa maggiore è composto da Fa, La e Do. Ascolta come l'ordine Do, Fa, Sol, Do dia un senso di partenza e di arrivo."),
        ex("s2", "Il giro di tre accordi", "Do, Fa, Sol, Do, uno per battuta.", stack("R", [C_T, F_T, G_T, C_T]), BEG_R, 54),
        ex("s3", "Il giro con il basso", "Anche la sinistra suona la sua nota.", stack("R", [C_T, F_T, G_T, C_T]) + stack("L", [[48], [41], [43], [48]]), BEG_B, 54),
        ex("s4", "Due accordi per battuta", "Ogni battuta suona due volte lo stesso accordo.", [N(i * 2.0, 2.0, k, "R") for i, ks in enumerate([C_T, C_T, F_T, F_T, G_T, G_T, C_T, C_T]) for k in ks] + [N(i * 2.0, 2.0, k, "L") for i, k in enumerate([48, 48, 41, 41, 43, 43, 48, 48])], BEG_B, 54),
    ]),
    lesson("l5", "La minore", "Un accordo più scuro.", [
        txt("s1", "Il colore minore", "L'accordo di La minore usa La, Do e Mi. Si distingue da quelli maggiori perché è più scuro e triste. Alternarlo a Do, Fa e Sol dà il giro più famoso del pop."),
        ex("s2", "La minore da solo", "Premi e senti il colore dell'accordo.", stack("R", [A_M, A_M, A_M, A_M]), BEG_R, 54),
        ex("s3", "Do, La minore, Fa, Sol", "Il giro dei quattro accordi.", stack("R", [C_T, A_M, F_T, G_T]) + stack("L", [[48], [45], [41], [43]]), BEG_B, 54),
    ]),
    lesson("l6", "Accompagnare una melodia", "Gli accordi sotto una melodia semplice.", [
        txt("s1", "Cantare e accompagnare", "Ora la sinistra suona gli accordi e la destra la melodia. Suona piano gli accordi e un po' più forte la melodia, così si sente cosa canta. Se ti perdi, fermati e riparti dall'inizio della battuta."),
        ex("s2", "Giro con la melodia", "Gli accordi di sinistra sostengono una melodia lunga.", stack("L", [[48, 52, 55], [43, 47, 50], [45, 48, 52], [41, 45, 48]] * 2) + [N(i * 4.0, 4.0, k, "R") for i, k in enumerate([64, 62, 60, 62, 64, 62, 60, 60])], BEG_SPLIT, 54),
        ex("s3", "La melodia più mossa", "Gli stessi accordi, con una melodia in minime.", stack("L", [[48, 52, 55], [43, 47, 50], [45, 48, 52], [41, 45, 48]] * 2) + [N(i * 2.0, 2.0, k, "R") for i, k in enumerate([64, 67, 62, 59, 60, 64, 62, 60, 64, 67, 69, 67, 65, 62, 60, 60])], BEG_SPLIT, 54),
    ]),
]}

# ----------------------------------------------------------------------------------------------
NEW = [
    (course_keys, "Più tonalità", "Re, La e Si bemolle maggiore, Mi e Re minore: alterazioni, scale e brevi melodie in tonalità nuove.", 5, "piu-tonalita.json"),
    (course_left, "Pattern per la mano sinistra", "Basso di Alberti, ottave, boogie-woogie, basso che cammina e stride: gli accompagnamenti più usati.", 4, "sinistra-pattern.json"),
    (course_rhythm, "Ritmi di oggi", "Levare, sedicesimi, swing, funk e due contro tre: il ritmo della musica di oggi.", 4, "ritmi-di-oggi.json"),
    (course_tech, "Tecnica quotidiana", "Schemi di cinque dita, terze, scale e arpeggi in due ottave: esercizi di tecnica nello stile di Hanon.", 3, "tecnica-quotidiana.json"),
    (course_begin2, "Prime due mani", "Le prime note con tutte e due le mani: insieme, a turno, in parallelo e in contrario, fino a tre piccoli brani.", 1, "prime-due-mani.json"),
    (course_begin_chords, "Accordi per cominciare", "Due note insieme, la triade di Do, Do-Sol, Do-Fa-Sol e La minore: i primi accordi, con la melodia sopra.", 1, "accordi-per-cominciare.json"),
    (course_scales, "Scale e arpeggi in ogni tonalità", "Scale maggiori e minori e arpeggi in tutte le tonalità, due ottave a due mani: il lavoro di base di ogni pianista.", 4, "scale-arpeggi-tonalita.json"),
    (course_chords, "Accordi in ogni tonalità", "Triadi maggiori, minori, diminuite e aumentate, rivolti, accordi di settima e cadenze in più tonalità.", 4, "accordi-tonalita.json"),
    (course_songs, "Canzoni con gli accordi", "Il giro I-V-vi-IV in tre tonalità, il blues in 12 battute e la cadenza andalusa: accordi per suonare canzoni.", 3, "canzoni-con-accordi.json"),
]

if __name__ == "__main__":
    idx_path = os.path.join(OUT, "index.json")
    index = json.load(open(idx_path, encoding="utf-8"))
    known = {c["id"] for c in index["courses"]}
    for course, title, desc, level, fname in NEW:
        json.dump(course, open(os.path.join(OUT, fname), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        if course["id"] not in known:
            index["courses"].append({"id": course["id"], "title": title, "description": desc, "level": level, "file": fname})
        print(fname, sum(len([s for s in l["slides"] if s["type"] == "exercise"]) for l in course["lessons"]), "esercizi")
    json.dump(index, open(idx_path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
