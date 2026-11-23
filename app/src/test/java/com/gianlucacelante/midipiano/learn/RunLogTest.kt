package com.gianlucacelante.midipiano.learn

import org.junit.Assert.assertEquals
import org.junit.Test

/** Una riga di registro per ogni giro di esercizio (finito o abbandonato). */
class RunLogTest {
    @Test fun finishedRunLineHasEverythingNeededToAnalyseASession() {
        val l = RunLog.finished(exerciseId = "mano-sinistra-accordi/l6/s3", step = 2, mode = "TEMPO", speed = 0.75, hits = 20, misses = 3, wrong = 5, accuracy = 0.87, stars = 2, passed = false, outcome = "Retry")
        assertEquals("fine giro esercizio=mano-sinistra-accordi/l6/s3 passo=3 modo=TEMPO vel=0,75 colpite=20 mancate=3 sbagliate=5 precisione=87% stelle=2 riuscito=no esito=Retry", l)
    }

    @Test fun abandonedRunLineSaysWhereAndWhy() {
        val l = RunLog.abandoned(exerciseId = "x/l1/s1", step = 0, mode = "WAIT", atSec = 8.25, hits = 4, wrong = 1, reason = "indietro")
        assertEquals("giro abbandonato esercizio=x/l1/s1 passo=1 modo=WAIT a=8,3s colpite=4 sbagliate=1 motivo=indietro", l)
    }
}
