package com.gianlucacelante.midipiano

import android.content.Context

/** Impostazioni dell'utente, salvate sul dispositivo. */
data class Settings(
    val sound: Boolean = true,
    val metronome: Boolean = false,
    val countIn: Boolean = true,
    val midiSounds: Boolean = true,
    val noteNames: Boolean = true,
    /** In "A tempo" con una mano sola, l'app suona l'altra mano. */
    val backing: Boolean = true,
    /** Puntini dei movimenti sul pannello comandi (in "A tempo" e "Ascolta"). */
    val visualBeat: Boolean = true,
    /** Obiettivo di pratica al giorno, in minuti (0 = nessun obiettivo). */
    val dailyGoalMinutes: Int = 10,
    /** Sul loop in "A tempo": un giro pulito accelera di un gradino, uno sbagliato rallenta. */
    val speedTrainer: Boolean = false,
    val fallingNotes: Boolean = false,
    val onboarded: Boolean = false,
    /** La guida del player (modalità, mani, loop, velocità, mappa errori) è stata vista. */
    val playerGuideSeen: Boolean = false,
    val mic: Boolean = false,
    val fingers: Boolean = true,
    /** Ora del promemoria giornaliero (0-23), -1 = spento. */
    val reminderHour: Int = -1,
    /** Routine quotidiana scelta (id del file in assets/routines) e quanti esercizi farne ogni giorno (0 = tutti). */
    val routineId: String = "routine-base",
    val routineSize: Int = 0,
)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Migrazione: la v2 accende i suoni (prima "suoni dalla tastiera" era spento di default e la tastiera restava muta). */
    private fun migrate() {
        if (prefs.getInt("version", 1) < 2) prefs.edit().putBoolean("sound", true).putBoolean("midiSounds", true).putInt("version", 2).apply()
        // v3: i nomi delle note sullo spartito sono accesi di default (chi comincia non li sa leggere); si spengono dalle impostazioni
        if (prefs.getInt("version", 1) < 3) prefs.edit().putBoolean("noteNames", true).putInt("version", 3).apply()
    }

    fun load(): Settings { migrate(); return read() }

    private fun read() = Settings(
        sound = prefs.getBoolean("sound", true),
        metronome = prefs.getBoolean("metronome", false),
        countIn = prefs.getBoolean("countIn", true),
        midiSounds = prefs.getBoolean("midiSounds", true),
        noteNames = prefs.getBoolean("noteNames", true),
        backing = prefs.getBoolean("backing", true),
        visualBeat = prefs.getBoolean("visualBeat", true),
        dailyGoalMinutes = prefs.getInt("dailyGoal", 10),
        speedTrainer = prefs.getBoolean("speedTrainer", false),
        fallingNotes = prefs.getBoolean("fallingNotes", false),
        onboarded = prefs.getBoolean("onboarded", false),
        playerGuideSeen = prefs.getBoolean("playerGuideSeen", false),
        mic = prefs.getBoolean("mic", false),
        fingers = prefs.getBoolean("fingers", true),
        reminderHour = prefs.getInt("reminderHour", -1),
        routineId = prefs.getString("routineId", "routine-base") ?: "routine-base",
        routineSize = prefs.getInt("routineSize", 0),
    )

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean("sound", s.sound).putBoolean("metronome", s.metronome).putBoolean("countIn", s.countIn)
            .putBoolean("midiSounds", s.midiSounds).putBoolean("noteNames", s.noteNames).putBoolean("backing", s.backing).putBoolean("visualBeat", s.visualBeat).putInt("dailyGoal", s.dailyGoalMinutes).putBoolean("speedTrainer", s.speedTrainer).putBoolean("fallingNotes", s.fallingNotes).putBoolean("onboarded", s.onboarded).putBoolean("playerGuideSeen", s.playerGuideSeen).putBoolean("mic", s.mic).putBoolean("fingers", s.fingers).putInt("reminderHour", s.reminderHour).putString("routineId", s.routineId).putInt("routineSize", s.routineSize)
            .apply()
    }
}
