package io.github.axiomvoidloop.takdisannurat.engine

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import java.time.LocalDate

/** The date the user started missing prayers. Month is optional (January is used if missing). */
data class MissedFrom(val year: Int, val month: Int? = null) {
    init {
        require(month == null || month in 1..12) { "month must be between 1 and 12" }
    }

    fun toDate(): LocalDate = LocalDate.of(year, month ?: 1, 1)
}

data class MonthYear(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "month must be between 1 and 12" }
    }

    fun firstDay(): LocalDate = LocalDate.of(year, month, 1)

    fun lastDay(): LocalDate = firstDay().plusMonths(1).minusDays(1)

    fun firstDayOfNextMonth(): LocalDate = firstDay().plusMonths(1)
}

/** Whether, and when, the user's period came back after a birth or a loss. */
sealed interface PeriodReturn {
    data class ReturnedIn(val month: MonthYear) : PeriodReturn
    data object NotYet : PeriodReturn
    data object NotSure : PeriodReturn
}

/** How long the bleeding after birth lasted: a number of days, or an approximate number of weeks (2 to 6). */
sealed interface NifasLength {
    data class Days(val days: Int) : NifasLength
    data class ApproxWeeks(val weeks: Int) : NifasLength

    fun resolvedDays(): Int = when (this) {
        is Days -> days
        is ApproxWeeks -> weeks.coerceIn(2, 6) * 7
    }
}

enum class WeeksAlong(val upperBoundWeeks: Int) {
    UNDER_4(4),
    FROM_4_TO_12(12),
    FROM_12_TO_20(20),
}

/** Recorded with an early loss as the user's own note. It does not change the numbers. */
enum class Confirmation { MISSED_PERIOD, TEST, ULTRASOUND, TEST_AND_ULTRASOUND }

enum class BleedingTreatedAs { NIFAS, HAID, UNASKED }

enum class NoPeriodReason { BREASTFEEDING, MEDICAL, ILLNESS_STRESS, OTHER, NOT_SURE }

/** Things that happened in a woman's life that change how many days of haid and nifas are set aside. */
sealed interface LifeEvent

data class Birth(
    val date: LocalDate,
    val nifas: NifasLength,
    val periodReturned: PeriodReturn = PeriodReturn.NotSure,
    val breastfed: Boolean? = null,
) : LifeEvent

data class EarlyLoss(
    val endedOn: MonthYear,
    val weeksAlong: WeeksAlong,
    val confirmedBy: Confirmation,
    val bleedingDays: Int? = null,
    val bleedingTreatedAs: BleedingTreatedAs = BleedingTreatedAs.UNASKED,
    val periodReturned: PeriodReturn = PeriodReturn.NotSure,
) : LifeEvent

/** [to] is the last day of the stretch (inclusive). Null means it is still going on. */
data class NoPeriods(
    val from: LocalDate,
    val to: LocalDate?,
    val reason: NoPeriodReason,
) : LifeEvent

/** Everything the calculator needs. */
data class Setup(
    val gender: Gender,
    val schoolChoice: SchoolChoice,
    val missedFrom: MissedFrom,
    /** The day the user begins praying regularly (not counted as a missed day). */
    val startDate: LocalDate,
    val includeWitr: Boolean,
    val haidDaysPerMonth: Int? = null,
    val lifeEvents: List<LifeEvent> = emptyList(),
)
