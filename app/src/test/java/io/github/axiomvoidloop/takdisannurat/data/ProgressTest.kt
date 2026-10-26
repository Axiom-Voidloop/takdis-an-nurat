package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import io.github.axiomvoidloop.takdisannurat.engine.Gender
import io.github.axiomvoidloop.takdisannurat.engine.MissedFrom
import io.github.axiomvoidloop.takdisannurat.engine.SchoolChoice
import io.github.axiomvoidloop.takdisannurat.engine.Setup
import io.github.axiomvoidloop.takdisannurat.engine.Wakt
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {

    private val day = LocalDate.of(2026, 12, 21)

    private fun prayed(wakt: Wakt, count: Int, id: String) = PrayerEvent(id, wakt, count, day)

    private val events = listOf(
        prayed(Wakt.FAJR, 1, "a"),
        prayed(Wakt.FAJR, 3, "b"),
        prayed(Wakt.ZUHR, 2, "c"),
    )

    @Test
    fun countsWhatWasMadeUp() {
        assertEquals(4, Progress.completed(events, Wakt.FAJR))
        assertEquals(2, Progress.completed(events, Wakt.ZUHR))
        assertEquals(0, Progress.completed(events, Wakt.ISHA))
    }

    @Test
    fun remainingNeverGoesBelowZero() {
        assertEquals(96, Progress.remaining(100, events, Wakt.FAJR))
        assertEquals(0, Progress.remaining(3, events, Wakt.FAJR))
    }

    @Test
    fun percentDoneIsWholeAndCapped() {
        assertEquals(4, Progress.percentDone(100, events, Wakt.FAJR))
        assertEquals(66, Progress.percentDone(3, listOf(prayed(Wakt.FAJR, 2, "x")), Wakt.FAJR))
        assertEquals(100, Progress.percentDone(3, events, Wakt.FAJR))
        assertEquals(100, Progress.percentDone(0, events, Wakt.FAJR))
        assertEquals(0, Progress.percentDone(100, events, Wakt.ASR))
    }

    @Test
    fun anAdjustmentCorrectsTheCount() {
        val adjust = PrayerEvent("fix", Wakt.FAJR, -2, day, EventKind.ADJUSTMENT)
        assertEquals(2, Progress.completed(events + adjust, Wakt.FAJR))
    }

    @Test
    fun settingTheRemainingCountMakesAnAdjustment() {
        // Owed 100, 4 done so far (96 left). The user says "I have 80 left".
        val delta = Progress.adjustmentToReach(100, events, Wakt.FAJR, 80)
        assertEquals(16, delta)
        val adjust = PrayerEvent("fix", Wakt.FAJR, delta!!, day, EventKind.ADJUSTMENT)
        assertEquals(80, Progress.remaining(100, events + adjust, Wakt.FAJR))
    }

    @Test
    fun settingTheRemainingCountCanGoBackUp() {
        val delta = Progress.adjustmentToReach(100, events, Wakt.FAJR, 99)
        assertEquals(-3, delta)
    }

    @Test
    fun settingTheSameRemainingCountChangesNothing() {
        assertEquals(null, Progress.adjustmentToReach(100, events, Wakt.FAJR, 96))
    }

    @Test
    fun theTargetIsKeptInsideWhatIsOwed() {
        assertEquals(null, Progress.adjustmentToReach(100, emptyList(), Wakt.FAJR, 500))
        assertEquals(100, Progress.adjustmentToReach(100, emptyList(), Wakt.FAJR, -5))
    }

    @Test
    fun undoRemovesOnlyThatEvent() {
        val data = AppData(
            Setup(Gender.MALE, SchoolChoice.NOT_SURE, MissedFrom(2020), LocalDate.of(2026, 1, 1), true),
            events = events,
        )
        val undone = data.withoutEvent("b")
        assertEquals(listOf("a", "c"), undone.events.map { it.id })
        assertEquals(data.events, data.withoutEvent("does-not-exist").events)
    }

    @Test
    fun eventIdsAreUnique() {
        val ids = (1..1000).map { newEventId() }.toSet()
        assertEquals(1000, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
    }
}
