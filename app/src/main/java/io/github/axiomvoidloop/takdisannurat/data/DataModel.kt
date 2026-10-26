package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import io.github.axiomvoidloop.takdisannurat.engine.Gender
import io.github.axiomvoidloop.takdisannurat.engine.QadaCalculator
import io.github.axiomvoidloop.takdisannurat.engine.Setup
import io.github.axiomvoidloop.takdisannurat.engine.Wakt
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class EventKind {
    /** A prayer that was made up. The count is always positive. */
    PRAYED,

    /** A correction. The count may be negative. */
    ADJUSTMENT,
}

/** One tap, bulk add or correction. Undo simply removes the event. */
data class PrayerEvent(
    val id: String,
    val wakt: Wakt,
    val count: Int,
    val date: LocalDate,
    val kind: EventKind = EventKind.PRAYED,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(count != 0) { "count must not be zero" }
        require(kind == EventKind.ADJUSTMENT || count > 0) { "a prayed event needs a positive count" }
    }
}

enum class Theme { BLACK, WHITE }

enum class DisplayUnit { DAYS, RAKATS }

data class ReminderSettings(
    val enabled: Boolean = true,
    val everyMinutes: Int = 300,
) {
    init {
        require(everyMinutes in MIN_MINUTES..MAX_MINUTES) { "everyMinutes out of range" }
    }

    companion object {
        /** Android does not reliably run background work more often than this. */
        const val MIN_MINUTES = 15
        const val MAX_MINUTES = 7 * 24 * 60
    }
}

data class QuietHours(
    val from: LocalTime = LocalTime.of(23, 0),
    val to: LocalTime = LocalTime.of(6, 0),
)

data class Settings(
    val displayUnit: DisplayUnit = DisplayUnit.DAYS,
    val theme: Theme = Theme.BLACK,
    val reminder: ReminderSettings = ReminderSettings(),
    val quietHours: QuietHours = QuietHours(),
)

/** Everything the app saves, and everything in a backup file (the app lock PIN is never part of it). */
data class AppData(
    val setup: Setup,
    val settings: Settings = Settings(),
    val events: List<PrayerEvent> = emptyList(),
) {
    init {
        require(setup.gender == Gender.FEMALE || setup.lifeEvents.isEmpty()) {
            "life events are only for a female profile"
        }
        require(setup.gender == Gender.MALE || setup.haidDaysPerMonth != null) {
            "a female profile needs the days of haid per month"
        }
    }

    fun withEvent(event: PrayerEvent): AppData = copy(events = events + event)

    /** Undo: removes the event with this id (nothing happens if it is not there). */
    fun withoutEvent(id: String): AppData = copy(events = events.filterNot { it.id == id })
}

fun newEventId(): String = UUID.randomUUID().toString()

/** Counting what has been made up and what is left. */
object Progress {

    fun completed(events: List<PrayerEvent>, wakt: Wakt): Int =
        events.filter { it.wakt == wakt }.sumOf { it.count }

    fun remaining(owed: Int, events: List<PrayerEvent>, wakt: Wakt): Int =
        QadaCalculator.remaining(owed, completed(events, wakt))

    /** Whole percent done, from 0 to 100. Nothing owed counts as 100. */
    fun percentDone(owed: Int, events: List<PrayerEvent>, wakt: Wakt): Int {
        if (owed <= 0) return 100
        val done = completed(events, wakt).coerceIn(0, owed)
        return done * 100 / owed
    }

    /**
     * The count of the adjustment event that makes "remaining" equal [targetRemaining]
     * (for "I already made some up before"). Returns null when nothing needs to change.
     */
    fun adjustmentToReach(owed: Int, events: List<PrayerEvent>, wakt: Wakt, targetRemaining: Int): Int? {
        val target = targetRemaining.coerceIn(0, maxOf(owed, 0))
        val neededCompleted = owed - target
        val delta = neededCompleted - completed(events, wakt)
        return if (delta == 0) null else delta
    }
}
