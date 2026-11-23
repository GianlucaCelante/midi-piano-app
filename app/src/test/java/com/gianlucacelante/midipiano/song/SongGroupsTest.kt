package com.gianlucacelante.midipiano.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongGroupsTest {
    private fun user(title: String, ext: String = "mxl") =
        SongEntry("user-" + title.replace(' ', '-'), title, null, "user:${title.replace(' ', '-')}.$ext", "File importato", "Personale", false, null)

    private val titles = listOf(
        "alex warren ordinary easy piano", "ordinary alex warren intermediate piano", "ordinary alex warren",
        "i will wait 1", "i will wait mumford sons", "i will wait",
        "another love hardstyle", "another love", "tom odell another love",
        "sailor song gigi perez", "gigi perez sailor song", "sailor song piano",
        "runaway kanye west", "runaway kanye west ramin djawadi arr by alex patience",
        "free kpop demon hunters rumi jinu piano arr", "free rumi and jinu duet kpop demon hunters piano beginner",
        "little lion man mumford sons", "skinny love bon iver", "people help the people birdy", "una mattina x tori no uta",
    )

    @Test fun sameSongInDifferentArrangementsBecomesOneGroup() {
        val groups = SongGroups.build(titles.map { user(it) })
        assertEquals(10, groups.size) // Ordinary, I Will Wait, Another Love, Sailor Song, Runaway, Kpop + 4 brani singoli
        val ordinary = groups.first { it.rep.title.contains("Ordinary", true) }
        assertEquals(3, ordinary.variants.size)
        assertEquals(listOf("Facile", "Intermedio", "Originale"), ordinary.variants.map { it.label.substringBefore(" (") })
        assertTrue(groups.first { it.rep.title.startsWith("I Will Wait") }.variants.size == 3)
        assertEquals(3, groups.first { it.rep.title.startsWith("Another Love") }.variants.size)
        assertEquals(2, groups.first { it.rep.title.startsWith("Runaway") }.variants.size)
    }

    @Test fun difficultyComesFromTheTitleAndOrdersTheVariants() {
        val g = SongGroups.build(listOf(user("kpop demon hunters beginner"), user("kpop demon hunters")))
        assertEquals(1, g.size)
        assertEquals(listOf("Principiante", "Originale"), g[0].variants.map { it.label })
        assertEquals("Kpop Demon Hunters", g[0].rep.title)
    }

    @Test fun unrelatedSongsAndBundledOnesAreNotMerged() {
        val bundled = SongEntry("b1", "Another Love", null, "another.mid", "o", "Public Domain", false, null)
        val g = SongGroups.build(listOf(bundled, user("another love"), user("love me tender"), user("another")))
        assertEquals(4, g.size) // un brano incluso non si fonde; «another» da solo non è abbastanza per fondere
    }

    @Test fun sameContentInTwoFormatsKeepsTheMusicXml() {
        val mid = user("another love", "mid")
        val mxl = user("tom odell another love", "mxl")
        val g = SongGroups.build(listOf(mid, mxl), sameContent = { _, _ -> true })
        assertEquals(1, g.size)
        assertEquals(1, g[0].variants.size)
        assertEquals(SongFormat.MUSICXML, g[0].variants[0].entry.format)
    }

    @Test fun duplicateLabelsGetANumber() {
        val g = SongGroups.build(listOf(user("song x"), user("song x 2"), user("song x intermediate")))
        val labels = g[0].variants.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test fun unrankedArrangementsAreSpreadFromEasyToHard() {
        val a = user("i will wait"); val b = user("i will wait 1"); val c = user("i will wait mumford sons")
        val lv = mapOf(a.id to 4, b.id to 4, c.id to 5)
        val g = SongGroups.build(listOf(a, b, c), level = { lv[it] ?: 3 }, size = { if (it == a.id) 243 else if (it == b.id) 1074 else 3026 })
        assertEquals(listOf("Facile", "Intermedio", "Avanzato"), g[0].variants.map { it.label.substringBefore(" (") })
        assertEquals(a.id, g[0].variants[0].entry.id) // il più semplice (meno note a parità di livello) per primo
    }

    @Test fun cleanTitlesAndComposersAreKeptAndMergedByArtist() {
        val e1 = user("Ordinary (Easy Piano)").copy(composer = "Alex Warren")
        val e2 = user("Ordinary").copy(composer = "Alex Warren")
        val other = user("Ordinary").copy(id = "user-x", composer = "Someone Else")
        val g = SongGroups.build(listOf(e1, e2, other))
        assertEquals(2, g.size) // stesso titolo ma autore diverso: due brani
        val aw = g.first { it.rep.composer == "Alex Warren" }
        assertEquals("Ordinary", aw.rep.title)
        assertEquals(listOf("Facile", "Originale"), aw.variants.map { it.label.substringBefore(" (") })
    }
}
