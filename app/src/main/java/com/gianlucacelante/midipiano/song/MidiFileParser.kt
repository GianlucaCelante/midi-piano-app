package com.gianlucacelante.midipiano.song

import com.gianlucacelante.midipiano.notation.NoteValues

class MidiFileException(message: String) : Exception(message)

/**
 * Converte un file MIDI standard (SMF formato 0/1) in un [Song].
 *
 * Mani: con più tracce con note, la traccia con altezza media più alta è la mano destra e quella
 * più bassa la sinistra (le altre seguono la più vicina per altezza media). Con una sola traccia
 * si divide al Do centrale (60). Il canale 10 (percussioni) è ignorato.
 */
object MidiFileParser {
    internal class RawNote(val tick: Long, var endTick: Long, val key: Int, val velocity: Int, val track: Int)
    private class MeterEvent(val tick: Long, val num: Int, val den: Int)
    private class KeyEvent(val tick: Long, val fifths: Int)

    fun parse(bytes: ByteArray, id: String, title: String, composer: String?, source: SongSource): Song {
        val r = Reader(bytes)
        if (r.readTag() != "MThd") throw MidiFileException("Non è un file MIDI (manca MThd)")
        val headerLen = r.readInt32()
        if (headerLen < 6) throw MidiFileException("Intestazione MIDI non valida")
        r.readInt16() // formato
        val trackCount = r.readInt16()
        val division = r.readInt16()
        r.skip(headerLen - 6)
        if (division and 0x8000 != 0) throw MidiFileException("Temporizzazione SMPTE non supportata")
        val ppq = division
        if (ppq == 0) throw MidiFileException("Divisione temporale non valida (0)")

        val tempos = ArrayList<Pair<Long, Int>>() // (tick, microsecondi per quarto)
        val notes = ArrayList<RawNote>()
        val meta = Meta()

        var track = 0
        while (track < trackCount && r.remaining() >= 8) {
            val tag = r.readTag()
            val len = r.readInt32()
            if (tag != "MTrk") {
                r.skip(len) // chunk sconosciuto: si salta
                continue
            }
            if (len < 0 || len > r.remaining()) throw MidiFileException("Traccia MIDI troncata o con lunghezza non valida")
            readTrack(Reader(bytes, r.pos, r.pos + len), track, tempos, notes, meta)
            r.skip(len)
            track++
        }
        if (notes.isEmpty()) throw MidiFileException("Il file non contiene note")

        tempos.sortBy { it.first }
        val tempoMap = TempoMap(ppq, tempos)
        val handOf = assignHands(notes)

        // Attacchi distinti per mano (ordinati): servono a dedurre il valore scritto dalle note staccate
        val onsetsByHand: Map<Hand, LongArray> = notes
            .groupBy { handOf(it) }
            .mapValues { (_, list) -> list.map { it.tick }.distinct().sorted().toLongArray() }

        val songNotes = notes
            .sortedWith(compareBy({ it.tick }, { it.key }))
            .map { n ->
                val start = tempoMap.toSeconds(n.tick)
                val hand = handOf(n)
                val endTick = maxOf(n.endTick, n.tick + 1)
                val onsets = onsetsByHand.getValue(hand)
                var idx = onsets.binarySearch(n.tick)
                idx = if (idx >= 0) idx + 1 else -idx - 1 // primo attacco strettamente successivo
                val nextBeats = if (idx < onsets.size) (onsets[idx] - n.tick).toDouble() / ppq else null
                SongNote(
                    startSec = start,
                    durationSec = tempoMap.toSeconds(endTick) - start,
                    key = n.key, velocity = n.velocity, hand = hand,
                    beats = NoteValues.notated((endTick - n.tick).toDouble() / ppq, nextBeats, n.tick.toDouble() / ppq),
                )
            }

        // Stanghette: una battuta dura numeratore * (4 / denominatore) quarti; la prima è a 0 e non si disegna.
        // Cambi di metro: la battuta nuova comincia dove cambia. Levare: se la musica comincia "in anacrusi" la prima battuta è corta.
        val meters = meta.meters.sortedBy { it.tick }.fold(ArrayList<MeterEvent>()) { acc, m ->
            val last = acc.lastOrNull() ?: MeterEvent(0, meta.timeNumerator, meta.timeDenominator)
            if (m.tick == 0L) { if (acc.isEmpty()) acc.add(m) else acc[acc.lastIndex] = m } else if (m.num != last.num || m.den != last.den) acc.add(m)
            acc
        }
        val firstMeter = meters.firstOrNull() ?: MeterEvent(0, meta.timeNumerator, meta.timeDenominator)
        val barTicks0 = (firstMeter.num * (4.0 / firstMeter.den) * ppq).toLong()
        val lastTick = notes.maxOf { it.endTick }
        val pickup = if (meters.size <= 1) detectPickupTicks(notes, ppq, barTicks0) else 0L
        val barTickList = barTickList(meters, pickup, lastTick, ppq, firstMeter)
        val barLines = barTickList.map { tempoMap.toSeconds(it) }
        val timeChanges = meters.drop(1).filter { it.tick > 0 }.map { TimeChange(tempoMap.toSeconds(it.tick), it.num, it.den) }
        val keyChanges = meta.keys.sortedBy { it.tick }.let { ks ->
            var prev = ks.firstOrNull()?.fifths ?: 0
            ks.drop(1).filter { it.fifths != prev }.map { prev = it.fifths; KeyChange(tempoMap.toSeconds(it.tick), it.fifths) }
        }
        return Song(
            id, title, composer, source, SongCleanup.tidy(songNotes), barLines, meta.keyFifths, firstMeter.num, firstMeter.den,
            secPerBeat = tempoMap.toSeconds(ppq.toLong()), // durata del primo quarto (ppq tick)
            tempoChanges = tempoMap.changes().let { if (it.size > 1) it else emptyList() },
            timeChanges = timeChanges, keyChanges = keyChanges,
        )
    }

    /** Tick delle stanghette dopo la prima, seguendo i cambi di metro. [pickup] > 0: la prima battuta è un levare lungo quei tick. */
    private fun barTickList(meters: List<MeterEvent>, pickup: Long, lastTick: Long, ppq: Int, first: MeterEvent): List<Long> {
        val out = ArrayList<Long>()
        var cur = 0L
        if (pickup > 0) { out.add(pickup); cur = pickup }
        var guard = 0
        while (cur < lastTick && guard++ < 50_000) {
            val m = meters.lastOrNull { it.tick <= cur } ?: first
            var len = (m.num * (4.0 / m.den) * ppq).toLong().coerceAtLeast(1L)
            val change = meters.firstOrNull { it.tick > cur && it.tick < cur + len }
            if (change != null) len = change.tick - cur // il cambio di metro cade dentro la battuta: la accorcia
            cur += len
            if (cur < lastTick) out.add(cur)
        }
        return out
    }

    /**
     * Un file MIDI non dice dov'è l'inizio delle battute: se la musica comincia con poche note brevi e il resto ricade
     * molto meglio con la griglia spostata (note lunghe e basse sul primo movimento), la prima battuta è un levare.
     * Prudente: sposta solo con un vantaggio netto.
     */
    internal fun detectPickupTicks(notes: List<RawNote>, ppq: Int, barTicks: Long): Long {
        if (barTicks < ppq * 2L) return 0L
        val first = notes.minOf { it.tick }
        if (first > ppq / 4) return 0L // la musica non comincia a zero: la griglia è già coerente col file
        val step = (ppq / 2).coerceAtLeast(1)
        fun weight(n: RawNote): Int = 1 + (if (n.key < 55) 1 else 0) + (if (n.endTick - n.tick >= ppq * 2L) 1 else 0)
        fun score(p: Long): Int = notes.sumOf { n -> if (n.tick >= p && (n.tick - p) % barTicks == 0L) weight(n) else 0 }
        val base = score(0L)
        var best = 0L
        var bestScore = base
        var p = step.toLong()
        while (p <= barTicks - step) {
            if (p <= barTicks * 0.6) {
                val before = notes.count { it.tick < p }
                val sc = score(p)
                if (before in 1..4 && sc > bestScore) { best = p; bestScore = sc }
            }
            p += step
        }
        return if (best > 0 && bestScore > base * 1.6 + 6) best else 0L
    }

    /** Informazioni musicali globali lette dai meta-eventi (vale la prima occorrenza). */
    private class Meta {
        var keyFifths = 0
        var keySet = false
        var timeNumerator = 4
        var timeDenominator = 4
        var timeSet = false
        val meters = ArrayList<MeterEvent>()
        val keys = ArrayList<KeyEvent>()
    }

    private fun readTrack(r: Reader, trackIndex: Int, tempos: MutableList<Pair<Long, Int>>, notes: MutableList<RawNote>, meta: Meta) {
        var tick = 0L
        var status = 0
        val open = HashMap<Int, ArrayDeque<RawNote>>() // (canale*128+nota) -> note aperte
        while (r.remaining() > 0) {
            tick += r.readVarLen()
            var b = r.peek()
            if (b and 0x80 != 0) {
                status = b
                r.skip(1)
            } else if (status == 0) {
                throw MidiFileException("Running status senza stato precedente")
            }
            b = status
            when {
                b == 0xFF -> {
                    val type = r.readByte()
                    val len = r.readVarLen().toInt()
                    if (type == 0x51 && len == 3) {
                        val us = (r.readByte() shl 16) or (r.readByte() shl 8) or r.readByte()
                        if (us > 0) tempos.add(tick to us)
                    } else if (type == 0x59 && len == 2) { // armatura: numero di alterazioni (con segno) e modo
                        val sf = r.readByte().toByte().toInt()
                        r.readByte()
                        if (sf in -7..7) meta.keys.add(KeyEvent(tick, sf))
                        if (!meta.keySet && sf in -7..7) {
                            meta.keyFifths = sf
                            meta.keySet = true
                        }
                    } else if (type == 0x58 && len >= 2) { // tempo musicale: numeratore e potenza di 2 del denominatore
                        val nn = r.readByte()
                        val dd = r.readByte()
                        r.skip(len - 2)
                        if (nn > 0 && dd in 0..5) meta.meters.add(MeterEvent(tick, nn, 1 shl dd))
                        if (!meta.timeSet && nn > 0 && dd in 0..5) {
                            meta.timeNumerator = nn
                            meta.timeDenominator = 1 shl dd
                            meta.timeSet = true
                        }
                    } else {
                        r.skip(len)
                    }
                    if (type == 0x2F) break
                    status = 0
                }
                b == 0xF0 || b == 0xF7 -> {
                    r.skip(r.readVarLen().toInt())
                    status = 0
                }
                else -> {
                    val channel = b and 0x0F
                    when (b and 0xF0) {
                        0x90 -> {
                            val key = r.readByte()
                            val vel = r.readByte()
                            if (channel != 9) {
                                val k = channel * 128 + key
                                if (vel > 0) {
                                    val n = RawNote(tick, tick, key, vel, trackIndex)
                                    open.getOrPut(k) { ArrayDeque() }.addLast(n)
                                    notes.add(n)
                                } else open[k]?.removeFirstOrNull()?.endTick = tick
                            }
                        }
                        0x80 -> {
                            val key = r.readByte()
                            r.readByte()
                            if (channel != 9) open[channel * 128 + key]?.removeFirstOrNull()?.endTick = tick
                        }
                        0xC0, 0xD0 -> r.skip(1)
                        else -> r.skip(2)
                    }
                }
            }
        }
        // note rimaste aperte: finiscono con la traccia
        open.values.flatten().forEach { if (it.endTick <= it.tick) it.endTick = tick }
    }

    private fun assignHands(notes: List<RawNote>): (RawNote) -> Hand {
        val byTrack = notes.groupBy { it.track }
        if (byTrack.size <= 1) return { if (it.key >= 60) Hand.RIGHT else Hand.LEFT }
        val avg = byTrack.mapValues { (_, v) -> v.map { it.key }.average() }
        val high = avg.maxOf { it.value }
        val low = avg.minOf { it.value }
        val mid = (high + low) / 2
        return { n -> if (avg.getValue(n.track) >= mid) Hand.RIGHT else Hand.LEFT }
    }

    private class TempoMap(private val ppq: Int, tempos: List<Pair<Long, Int>>) {
        // segmenti: tick di inizio, secondi di inizio, microsecondi per quarto
        private val ticks = ArrayList<Long>()
        private val secs = ArrayList<Double>()
        private val mpq = ArrayList<Int>()

        init {
            val list = if (tempos.isEmpty() || tempos.first().first > 0) listOf(0L to 500_000) + tempos else tempos
            var t = 0.0
            var prevTick = 0L
            var prevMpq = list.first().second
            for ((tick, us) in list) {
                t += (tick - prevTick) * prevMpq / 1_000_000.0 / ppq
                ticks.add(tick); secs.add(t); mpq.add(us)
                prevTick = tick; prevMpq = us
            }
        }

        /** I cambi di tempo come (istante, secondi per quarto). */
        fun changes(): List<TempoChange> = ticks.indices.map { TempoChange(secs[it], mpq[it] / 1_000_000.0) }

        fun toSeconds(tick: Long): Double {
            var i = ticks.indexOfLast { it <= tick }
            if (i < 0) i = 0
            return secs[i] + (tick - ticks[i]) * mpq[i] / 1_000_000.0 / ppq
        }
    }

    private class Reader(private val b: ByteArray, var pos: Int = 0, private val end: Int = b.size) {
        fun remaining() = end - pos
        fun peek(): Int = if (pos < end) b[pos].toInt() and 0xFF else throw MidiFileException("File MIDI troncato")
        fun readByte(): Int = peek().also { pos++ }
        fun skip(n: Int) {
            if (n < 0 || pos.toLong() + n > end) throw MidiFileException("File MIDI troncato")
            pos += n
        }
        fun readInt16() = (readByte() shl 8) or readByte()
        fun readInt32() = (readByte() shl 24) or (readByte() shl 16) or (readByte() shl 8) or readByte()
        fun readTag(): String = String(CharArray(4) { readByte().toChar() })
        fun readVarLen(): Long {
            var v = 0L
            repeat(4) {
                val x = readByte()
                v = (v shl 7) or (x and 0x7F).toLong()
                if (x and 0x80 == 0) return v
            }
            throw MidiFileException("Lunghezza variabile non valida")
        }
    }
}
