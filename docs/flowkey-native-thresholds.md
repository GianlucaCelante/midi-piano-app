# Soglie e logica di pratica nel motore nativo di Flowkey (riferimento)

Fonte: decompilazione con Ghidra 12.2 di `libAndroidPlayer.so` (arm64, Swift, dall'APK v2.120.0) e lettura diretta dei dati dell'ELF.
Scopo: **solo riferimento** per tarare il nostro motore di pratica. Il codice resta nostro: qui ci sono valori e idee descritte a parole,
niente codice copiato. Il comportamento a runtime non è stato verificato.

Legenda: **[O]** osservato (valore letto dal binario) · **[I]** inferito (interpretazione mia) · **[?]** non determinato.
Gli indirizzi sono quelli di Ghidra (image base `0x100000`); per trovare un dato nel file ELF si sottrae `0x100000`.

## 1. Costanti numeriche [O]

| Nome (simbolo Swift) | Valore | Indirizzo dato | Note |
|---|---|---|---|
| `PerformanceAnalysis.PRACTICE_LOOP_LOWEST_TEMPO` | **0.35** (Double) | `0xafac68` | tempo minimo, come frazione del tempo originale (35 %) |
| `PerformanceAnalysis.PRACTICE_LOOP_MIN_RECALL` | **0.1** (Double) | `0xafac70` | il nome indica un richiamo minimo; **non ho trovato dove venga usato** [?] |
| `PerformanceMetrics.micF1ScoreTolerance` (valore iniziale) | **0.98** | `0x8c2bc0` | tolleranza F1 per l'ingresso da microfono [I: uso] |
| `ScoreFollowerCore.chordDurationThresholdInMs` | **50** ms | `0x3f17f0` | durata massima entro cui note vicine contano come un accordo [I: uso] |
| `DecisionLogic.octaveFilterThresholdFactor` | **0.5** (Float) | `0x3ec40c` | filtro ottave dell'analisi audio |

`DecisionLogic.Parameters` (microfono, struttura di 5 campi; ordine dal nome dell'inizializzatore) [O]:

| Campo | `default` | `lowLatency` |
|---|---|---|
| `bufferFrameCount` | 6 | 6 |
| `pitchConfidenceSumThreshold` | 0.1 | 0.1 |
| `onsetConfidenceSlopeThreshold` | 0.1 | 0.1 |
| `pitchClassThresholdFactor` | 2.0 | 2.0 |
| `pitchConfidenceSumThresholdScoreFollowing` | 2.31 | 1.53 |

Altre soglie incontrate nel codice (valori [O], significato [I]): 0.1, 0.2, 0.3, 0.4, 0.5, 0.9, 7/9 (≈0.7778), 12/13 (≈0.9231), 0.15, -0.1, -0.4, 0.75 e passo di arrotondamento 0.05.

## 2. Valutazione del tempo e della coerenza

- **Deviazione di tempo** [O struttura, I nomi]: si calcola `d = |ln(tempo_suonato / tempo_atteso)|` e la si classifica in quattro livelli:
  `d < 0.1` → 3 (ottimo); `0.1 ≤ d < 0.2` → 2; `0.2 ≤ d < 0.4` → 1; `d ≥ 0.4` → 0. (Il livello 3 è anche il risultato se manca il dato.)
- **Punteggio delle note** (`getScoreConfidence`) [O struttura, I significato]: confronta un valore di precisione/richiamo con due soglie
  **7/9 ≈ 0.778** e **12/13 ≈ 0.923**: sotto la prima livello 0, sotto la seconda livello 1, sotto il massimo livello 2, altrimenti 3.
  Per uno degli ingressi la soglia è scalata sul numero di note attese. Quale ingresso (MIDI o microfono) usi quale ramo non è certo.
- **Nuovo tempo di pratica** (`getTempo`) [O formula]: `tempo = arrotonda_per_difetto_a_0.05( tempo_utente + incoerenza_ritmica × (-0.4) )`,
  poi limitato fra il **tempo minimo** (parametro `lowestTempo`) e la soglia del passo (`tempoThreshold`). Cioè: più sei irregolare,
  più il tempo successivo scende, con passi di 5 %.
- **Variazione di tempo** (`getTempoDelta`) [O valori, I uso]: risultato **-0.1** se scatta la condizione di riduzione; altrimenti 0 senza prove;
  con prove, dipende dalla coerenza ritmica: livello 2 → **+0.1**, livello 3 → **+0.15**, altri → 0.
- **Ridurre il tempo?** (`shouldReduceTempo`) [I, confidenza bassa]: con una o due prove, se la coerenza ritmica è al livello più basso si consiglia
  di rallentare; altrimenti si rallenta solo se le ultime due prove hanno tutte un'incoerenza alta (≥ 0.5). Basta una prova decente (< 0.5) per non rallentare.
- **Sto suonando con sicurezza?** (`getIsPlayingConfidently`) [I, parziale]: serve coerenza ritmica massima (livello 3) e che il tempo dell'ultima prova,
  arrotondato a 0.05, raggiunga la soglia dell'esercizio. Nei passi con metronomo servono anche punteggio e deviazione di tempo almeno al livello 2.
  La parte per coerenza < 3 non è stata ricostruita [?].

## 3. Decisione sul loop (`PracticeLoopDecisionEngine.evaluate`) [I, confidenza bassa-media]

Ingresso (ordine dei campi dal simbolo): passo corrente, loop completati, loop richiesti, tempo atteso (opzionale), soglia di tempo,
elenco delle prove, metronomo, tipo di ingresso, note al secondo (opzionale), tetto del tempo minimo (opzionale).
Uscita: un tempo di pratica e una decisione fra tre esiti più "nessuna" (i nomi dei casi non sono recuperabili dai simboli) [?].

Quello che si vede chiaramente:
1. **Tempo minimo dinamico**: di base 0.35 del tempo originale. Se si conoscono le note al secondo `n` e `0.75/n > 0.35` (brani poco densi, n < circa 2.1),
   il minimo diventa `min(tetto, 0.75/n)`; altrimenti resta `min(tetto, 0.35)`. Il risultato è arrotondato a 0.05 [I: lettura del flusso dei confronti].
   Serve inoltre un tetto ≥ 0.35 (altrimenti il codice si ferma con un errore interno).
2. **Passi semplici** (primo tipo di passo): il loop si ripete finché i loop completati non raggiungono quelli richiesti.
3. **Senza prove valide** (nessuna prova, tempo < 0.1 o prova con errore): non decide, salvo il passo di tipo 3 con tempo atteso, che dopo più di 3 loop passa avanti.
4. **Con tempo atteso assente**: si può continuare solo se l'ultima prova ha tempo ≥ `max(0.75 × minimo, 0.35)` **e** un valore ≥ **0.9** (probabilmente il richiamo): in quel caso il nuovo tempo si calcola con la formula `getTempo`.
5. **Con tempo atteso presente**: il nuovo tempo segue la stessa formula, limitato fra minimo e tempo atteso; se si è già al tempo atteso e i loop sono < 4 si ripete,
   altrimenti per i passi con metronomo si usa una finestra di accettazione costruita con i rapporti 7/9 e 12/13 del punteggio (sezione 2), e oltre 2 loop aggiuntivi si passa avanti.

Le diramazioni fini (quale decisione corrisponda a "ripeti", "alza il tempo" o "avanza") non sono certe: il decompilatore ha prodotto molti rami di errore
Swift (controlli di overflow) che nascondono il flusso. **Per il nostro progetto conviene usare la struttura (tempo minimo, passi di 5 %, peso -0.4 sull'incoerenza,
richiamo 0.9, soglie 7/9 e 12/13) e riprogettare noi le regole di decisione, testandole.**

## 4. Riconoscimento dal microfono (`DecisionLogic`) [I, parziale]

- **Attivazione su onset** (`activateIfOnsetDetected`): si attiva solo se (a) non è già attivo, (b) è passato un intervallo minimo dall'ultima attivazione
  e (c) la salita della confidenza dell'onset rispetto al fotogramma precedente supera una soglia adattiva che decade nel tempo (con base `onsetConfidenceSlopeThreshold`, 0.1).
  All'attivazione parte una finestra di `bufferFrameCount` = 6 fotogrammi.
- **Verifica note suonate** (`notesWerePlayedCorrectly`): a ogni fotogramma accumula le confidenze di altezza; il confronto finale con le soglie
  (somma ≥ 0.1, fattore di classe di altezza 2.0, filtro ottave 0.5; in modalità score following la somma richiesta è 2.31, o 1.53 a bassa latenza)
  è nel ramo che il decompilatore non ha recuperato [?].
- **Score following**: `ScoreFollowerCore` è un modello a stati nascosti (HMM) sulle note attese (nomi `startIndexHMM`, `states`, `observations`, `lowProbabilityNotesCache`);
  la funzione `process` non è stata analizzata in dettaglio [?]. La rete di altezza (CNN) non è nell'APK come file e i suoi pesi non sono stati estratti [?].

## 5. Cosa NON è stato determinato
- Tolleranze **temporali** di abbinamento nota suonata/nota attesa (`PerformanceMatcher`, `removeHarmonicFalsePositives`, `removeHighFalsePositives`, `getTempoDeviations`):
  le costanti sono riferimenti a dati non numerici nel decompilato e non le ho risolte.
- Uso effettivo di `PRACTICE_LOOP_MIN_RECALL`.
- Nomi e mappatura esatta dei tre esiti di `PracticeLoopDecision` e semantica di ogni campo di `PerformanceMetrics`.
- Logica di `ScoreFollowerCore.process`, pesi della CNN, confronto finale di `notesWerePlayedCorrectly`.

## 6. Ripetibilità
Ghidra 12.2 (SHA-256 verificato) in `C:\Users\gianl\tools\ghidra`, con un JDK 25 portabile (Temurin, SHA-256 verificato) in `C:\Users\gianl\tools\jdk25`,
perché Ghidra 12.2 non gira con il JDK 21 di Android Studio. Analisi headless con uno script che decompila le funzioni di `PerformanceAnalysis`, `NoteDetection.DecisionLogic`,
`ScoreFollowerCore`; le costanti sono lette dall'ELF. I file temporanei di analisi stanno nello scratchpad e non sono nel repository.

## 7. Come usarlo nel nostro progetto
Punti di partenza proposti (da tarare con prove reali): tempo minimo 0.35, passi di tempo 0.05, riduzione del tempo proporzionale all'incoerenza ritmica con peso 0.4,
tolleranza di deviazione in scala logaritmica con soglie 0.1/0.2/0.4, soglie di precisione 7/9 e 12/13, accordo = note entro 50 ms. Con la tastiera MIDI nulla di
tutto ciò che riguarda microfono, onset e HMM serve.
