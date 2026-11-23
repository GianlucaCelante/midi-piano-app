"""Genera app/src/main/assets/songs/cornfield-chase.musicxml dalla trascrizione manuale delle prime battute
dello spartito (immagine fornita dall'utente). Trascrizione a occhio: da verificare."""
import pathlib
D = 12  # divisioni per quarto
def pc(n):  # 'A4' -> (step, alter, octave)
    s = n[0]; o = int(n[-1]); alt = 1 if '#' in n else 0
    return s, alt, o
def note(n, dur, staff, voice, chord=False, typ=None):
    s, a, o = pc(n)
    return ('<note>' + ('<chord/>' if chord else '') + f'<pitch><step>{s}</step>' + (f'<alter>{a}</alter>' if a else '') +
            f'<octave>{o}</octave></pitch><duration>{dur}</duration><voice>{voice}</voice><staff>{staff}</staff></note>')
def rest(dur, staff, voice):
    return f'<note><rest/><duration>{dur}</duration><voice>{voice}</voice><staff>{staff}</staff></note>'
def chord(ns, dur, staff, voice):
    return ''.join(note(n, dur, staff, voice, chord=i > 0) for i, n in enumerate(ns))
def backup(d): return f'<backup><duration>{d}</duration></backup>'
Q, E, T = D, D // 2, D // 3  # quarto, ottavo, ottavo di terzina
bar34 = 3 * Q
measures = []
def m34(mel, lower, lh, first=False, extra=''):
    return extra + mel + backup(bar34) + chord(lower, bar34, 1, 2) + backup(bar34) + chord(lh, bar34, 2, 3)
def attrs34(first=False):
    return ('<attributes><divisions>12</divisions><key><fifths>0</fifths></key><time><beats>3</beats><beat-type>4</beat-type></time>'
            '<staves>2</staves><clef number="1"><sign>G</sign><line>2</line></clef><clef number="2"><sign>F</sign><line>4</line></clef></attributes>'
            '<direction placement="above"><direction-type><words>Allegretto</words></direction-type><sound tempo="144"/></direction>')
Am_lo, G_lo = ['C4', 'E4'], ['B3', 'D4']
Am_lh, G_lh = ['A2', 'E3'], ['G2', 'D3']
def qbar(first, lo, lh):  # first, E5, rest
    return m34(note(first, Q, 1, 1) + note('E5', Q, 1, 1) + rest(Q, 1, 1), lo, lh)
def ebar(first, lo, lh):  # 4 ottavi, pausa, ottavo
    mel = note(first, E, 1, 1) + note('E5', E, 1, 1) * 3 + rest(E, 1, 1) + note('E5', E, 1, 1)
    return m34(mel, lo, lh)
ms = []
ms.append(attrs34() + m34(note('C4', bar34, 1, 1), ['C4'], Am_lh)[:0] +
          note('C4', bar34, 1, 1) + backup(bar34) + chord(['A2', 'E3'], bar34, 2, 2))
ms.append(qbar('A4', Am_lo, Am_lh)); ms.append(qbar('A4', Am_lo, Am_lh))
ms.append(qbar('B4', G_lo, G_lh)); ms.append(qbar('B4', G_lo, G_lh))
ms.append(qbar('A4', Am_lo, Am_lh)); ms.append(qbar('A4', Am_lo, Am_lh))
ms.append(qbar('B4', G_lo, G_lh))
ms.append(m34(note('D5', Q, 1, 1) + note('C5', Q, 1, 1) + note('B4', Q, 1, 1), G_lo, G_lh))
ms.append(ebar('A4', Am_lo, Am_lh)); ms.append(ebar('A4', Am_lo, Am_lh)); ms.append(ebar('B4', G_lo, G_lh))
ms.append(ebar('B4', G_lo, G_lh)); ms.append(ebar('B4', G_lo, G_lh)); ms.append(ebar('A4', Am_lo, Am_lh)); ms.append(ebar('B4', G_lo, G_lh))
# battuta 5/8
b58 = 5 * E
mel = note('D5', E, 1, 1) + note('E5', E, 1, 1) * 3 + rest(E, 1, 1)
ms.append('<attributes><time><beats>5</beats><beat-type>8</beat-type></time></attributes>' + mel + backup(b58) +
          chord(G_lo, b58, 1, 2) + backup(b58) + chord(G_lh, 4 * E, 2, 3) + chord(['B2', 'D3'], E, 2, 3))
# 3/4 legato: arpeggi in terzine di ottavi (destra) e sedicesimi di terzina (sinistra)
def arp(rh, lh):
    r = ''.join(note(n, T, 1, 1) for _ in range(3) for n in rh)
    l = ''.join(note(n, T // 2, 2, 2) for _ in range(3) for n in lh * 3)
    return r + backup(bar34) + l
ms.append('<attributes><time><beats>3</beats><beat-type>4</beat-type></time></attributes>' +
          arp(['A4', 'C5', 'E5'], ['A2', 'E3', 'A3']))
ms.append(arp(['B4', 'D5', 'G5'], ['G2', 'D3', 'G3']))
body = ''.join(f'<measure number="{i + 1}">{m}</measure>' for i, m in enumerate(ms))
xml = ('<?xml version="1.0" encoding="UTF-8"?><score-partwise version="4.0"><work><work-title>Cornfield Chase</work-title></work>'
       '<identification><creator type="composer">Hans Zimmer</creator></identification>'
       '<part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>'
       f'<part id="P1">{body}</part></score-partwise>')
pathlib.Path('app/src/main/assets/songs/cornfield-chase.musicxml').write_text(xml, encoding='utf-8')
