# Analisi di riferimento: Flowkey Android v2.120.0

Fonte: analisi **statica** di `flowkey.apk` (package `com.flowkey.app`, SHA-256 `4369c7d4…3561c70`) con REA (jadx + `hermes-dec`)
e analisi dei simboli di `libAndroidPlayer.so`. L'app non è mai stata eseguita.

> Uso: **solo riferimento funzionale e architetturale**. Non copiare codice, grafica, video, spartiti, suoni né usare il loro
> backend/API. Tutto va riscritto da zero.
> Legenda: **[O]** = osservato nei file; **[I]** = inferito; **[?]** = non determinato.

## 1. Architettura a strati

| Strato | Tecnologia | Evidenza |
|---|---|---|
| UI di navigazione (home, corsi, brani, account, paywall) | React Native + Hermes, Reanimated, Gesture Handler, SVG, Apollo GraphQL | [O] `index.android.bundle` (bytecode Hermes v98, 30.613 funzioni) |
| Moduli ponte Android | Kotlin: `midi.*`, `audioplayer.*`, `videoplayer.*`, `googleplaybilling.*`, `push.*` | [O] classi jadx |
| **Motore "Player"** (partitura, sincronizzazione, apprendimento, riconoscimento note) | **Swift compilato per Android** (`libAndroidPlayer.so` + runtime `libswift*.so`), rendering GLES con `uikit` custom e ThorVG/Lottie | [O] simboli Swift, `com.flowkey.uikit`, `PlayerView` |
| Audio | Oboe (AAudio/OpenSL ES) per input/output a bassa latenza | [O] simboli `oboe::` |
| Suono del piano | Campioni SFZ scaricati a runtime, sintetizzati con sfizz | [O] `SFZDownloader`, `SfizzCacheDirectory`, query `MidiSongPlaybackSfzFileUrl` |
| Rete | GraphQL (Apollo) per tutto; `resourceBaseURL` per i file statici | [O] 126 documenti GraphQL |

Il motore Player espone a React Native un'interfaccia a eventi/azioni (`dispatchAction`, `setEventCallback`, `onData`) [O].
Dentro il motore c'è un'architettura **Redux** (`ReduxStore`, `ReduxAction`, `ReduxState`, store separati per `midiPlayback`,
`midiSounds`, `metronome`, `noteDetection`, `scoreFollowing`, `playerEvents`, `lifecycle`, `activeNotes`, `yamahaLights`) [O],
con UI a componenti dichiarativi (`*Component: connect()`) [O].

## 2. Modello dati di un brano (`SongData`) [O]
Campi: `_id, title, artist, level, songGroupId, requiredAccessLevel, hasMidiSounds, credits{arrangedBy,composedBy,performedBy,publishingRights},
songPartsV3[{name,startTick,endTick,phrases[]}], barLines[{tick,x}], loops[{name,startX,endX}], musicalFeatures.timeSignatures[{numerator,denominator,bar}],
sustainPedalData[{t,value}], lightningKeys, videoMp4Url, videoDuration, musicsheets, noteEvents`.

`noteEvents[]` (evento temporale):
- `t` (secondi), `sheetTiming{bar,beat,tick}`
- `notesL[]` e `notesR[]` (mano sinistra/destra), ciascuna: `key` (numero MIDI), `name`, `delay`, `duration`, `velocity`,
  `isInArpeggio`, `dots`, `bbox{x,y,width,height}` (posizione sulla partitura orizzontale), `fullSheetMediumBbox`
- `horizontalSheetX`, `fullSheetMedium{x,lineNumber}` (posizione per scorrimento verticale a pagina intera)

`lightningKeys`: per ogni brano, `xPositions`, `yOffset` e `keyIndexRanges{left,right,both}{lowest,highest}`: intervallo di tasti
da evidenziare sulla tastiera, per mano [O].

**Partitura:** NON è renderizzata sul dispositivo. Il server fornisce **immagini PNG a tessere** (`files{png2x,displayList}`) in due layout:
- `horizontalSheet`: striscia orizzontale scorrevole, con `stickyPanel` (chiave e armatura fisse a sinistra, `fadeOutWidth`),
- `fullSheetMedium`: pagina intera con `lineYOffsets`, `lineHeights`, `sliceHeight`.
`SheetRenderer`/`TiledSheetView` rasterizzano le tessere necessarie (`neededTileIndices`), con "glow" per evidenziare note
corrette/errate (`highlightNotes`, `highlightErrorNotes`). [O]

## 3. Apprendimento: modalità, passi, esercizi
- `LearnMode`: velocità di riproduzione lenta/veloce/originale (`slowPlaybackRate`, `fastPlaybackRate`, `percentSpeed`,
  "speed dial" 50/75/100%) [O].
- `StandardLearnStep`: `expectation`, `title`, `text`, `requiredLoopCount`, `pauseWhenGoalIsReached`, `alwaysAllowSkip`, `hasCountInEnabled`,
  `icon`, `settings` [O].
- `LearnStepSettings`: `hand` (sinistra/destra/entrambe), **`waitMode`**, `allowLoopSelection`, `showLightningKeys`,
  `loopWholeSong`, `loopStart`, `loopEnd` [O].
- `ModalLearnStep`: passo informativo con testo e pulsante [O].
- `Exercise` → `ExercisePart[]` → `learnSteps[]`; ogni parte ha `awardLevel`, `isCompleted`, `lastCompletedTempo` [O].
  Soglie: `tempoThreshold`, `silverTempoThreshold`, `practiceLoopIsEnabled`, `awardingIsEnabled` [O].
- Funzioni di contorno: count-in con anelli e suddivisioni (`CountInAnimationFrame`), metronomo, selettore mano, loop con
  nome/collegamento (`LoopItem`, `LoopSeamItem`, `FullSheetLoopLines`), menu parti del brano (`songPartsV3`), nomi note (`NoteNames`),
  "Just Play" (suona senza guida), tasti "lightning" [O].
- Messaggi di ricompensa livello 1/2 (`rewardMessagesLevel1/2`), feedback "played correctly" [O].

## 4. Ingresso MIDI e riconoscimento note
- **MIDI hardware** [O]: `MIDIEngine` (USB via AMidi, Bluetooth LE MIDI con scansione/connessione), eventi `MIDIMessageReceived{device,message,time}`,
  sysex, `MIDIOutConnection.send/sendScheduled`.
- **Microfono** [O]: `AudioInput`, `ProcessedAudio{audioData, featureVector, filterbankMagnitudes, onsetDetected, onsetFeatureValue, onsetThreshold}`,
  `CnnPitch` (rete neurale per altezza), `BenchmarkResult{pitchCnnVersion, pitchPredictionStride}`. Nessun file `.tflite` nell'APK
  → il modello è incorporato nella libreria o scaricato [?].
- **Score following** [O]: `ScoreFollowerCore{noteEventIndices, observationTimestampDeltas, process(), reset()}` e `DecisionLogic`
  (`activateIfOnsetDetected`, `notesWerePlayedCorrectly`, `Parameters`). Il brano avanza in `waitMode` quando le note attese sono state suonate.
- **Luci Yamaha** [O]: `YamahaLights` (handshake sysex): accende i tasti della tastiera collegata. Idea interessante da replicare con MIDI out.
- Volume dinamico dei suoni MIDI in base all'ultimo input (`calculateVolumeModifier`) [O].

## 5. Analisi della performance
Modulo `PerformanceAnalysis` [O]:
- Esiti per nota: `TruePositive{eventIndex, expectedDetectionTime, instantaneousLogTempo, note}`, `FalsePositive`, `FalseNegative`, `MatchedNote`.
- Metriche: `TempoDeviation{deviation,eventIndex}`, `DynamicsMetrics{meanError,meanNormalizedError}`, `NoteDurationMetrics{meanError}`,
  `DetailedConfidences{dynamics, noteDuration, notes, rhythmicConsistency, tempo}`, `ScoreTolerance`, `ScoreConfidence`.
- **Decisione di ripetizione loop**: `PracticeLoopDecisionEngine` con ingresso `PracticeLoopInput{currentStepId, inputType (midi/mic),
  loopsCompleted, loopsRequired, lowestTempoCeiling, metronomeEnabled, noteEventsPerSecond, performances, tempoExpectation, tempoThreshold}`
  e costanti `PRACTICE_LOOP_LOWEST_TEMPO`, `PRACTICE_LOOP_MIN_RECALL`. **I valori numerici non sono stati estratti** [?].
- Le sequenze MIDI suonate vengono inviate al server (`trackSongMIDISequence`, codifica compatta `BYTES_PER_ENCODED_MIDI_EVENT`) [O].

## 6. Struttura dei contenuti e dell'app (React Native)
- **Corsi V2**: Corso → Lezioni → Slide. Tipi di slide: video, esercizio, lezione, "ended", blocco premium (`PremiumLockSlide`).
  Copertine, progresso e livello di accesso richiesto per lezione.
- **Brani**: scoperta (`SongDiscovery`, `TopPicks`, `RecommendedSongGroups`, `RecentlyLearned`), categorie, raccolte, playlist,
  preferiti, livelli di difficoltà (cerchi di livello), **Guided Songs** (brani dentro i corsi, con `learnProgress`).
- **Pratica giornaliera** (`dailyPractice`, v2): elenco di `practiceItems` per lezione, con premi e spiegazione del progresso.
- **Profilo di apprendimento** (`LearnProfile`), obiettivi iniziali ("leggere spartiti", "suonare con due mani", "brani semplici/preferiti"),
  esperienza ("una mano"), onboarding con selezione livello e anteprima audio.
- **Account/commerce**: login (password, token, Apple, OAuth, link email), famiglia (account figli, voucher), abbonamenti e offerte
  (Google Play Billing), push, review request, esperimenti A/B, i18n in ~12 lingue.
- **Tracciamento attività**: `TrackUserActivity` (secondi attivi + timezone), sessioni giornaliere → streak.

## 7. Cosa NON è stato determinato
- Soglie e formule esatte (tolleranza di tempo/nota, decisione di loop, `PRACTICE_LOOP_*`): servirebbe decompilare `libAndroidPlayer.so`
  (richiede Ghidra 12.x + JDK 21, non configurati).
- Modello e pesi della rete `CnnPitch`.
- Formato `.flw` (sfondi/primo piano animati), formato `displayList` delle tessere.
- Comportamento a runtime (nessuna esecuzione).

## 8. Implicazioni per la nostra scelta tecnologica (da discutere)
1. **Partitura**: Flowkey usa PNG pre-renderizzate + coordinate per nota (costoso: serve una pipeline di produzione lato server).
   Per noi conviene renderizzare dal dato (MIDI/MusicXML → notazione) con una libreria come VexFlow/OSMD, o iniziare con
   visualizzazione a "note che cadono"/piano roll, che non richiede notazione.
2. **Modello dati**: ricalcare l'idea `noteEvents{t, notesL, notesR}` con mano separata, durata, velocity: è semplice e sufficiente per waitMode,
   mani separate, loop, tempo.
3. **MIDI**: Web MIDI API (Chrome/Edge/Android) copre USB e BLE MIDI senza app nativa; Safari/iOS no. Se serve iOS, serve un wrapper nativo.
4. **Score following**: con tastiera MIDI è triviale (confronto note attese/ricevute); il microfono (CNN) è un'estensione futura costosa.
5. **Architettura**: store a eventi/Redux-like con componenti separati è una buona base; motore di gioco separato dalla UI.
6. **Suoni**: SFZ + sfizz o sampler web (soundfont/WebAudio) con campioni con licenza libera.
7. **Backend**: GraphQL non è obbligatorio; per un MVP bastano file JSON locali + storage del progresso.
