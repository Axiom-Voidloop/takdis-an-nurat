package io.github.axiomvoidloop.takdisannurat.engine

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import java.time.LocalDate
import java.time.YearMonth

data class CalculationResult(
    /** Days from the missed-from date up to, but not including, the start date. */
    val periodDays: Int,
    /** Days set aside for monthly haid. */
    val haidDays: Int,
    /** Days set aside for nifas. */
    val nifasDays: Int,
    /** Days of prayers owed for each wakt. */
    val owedDays: Int,
    val includeWitr: Boolean,
    /** The haid days per month actually used, after fitting the school's allowed range. Null for men. */
    val haidDaysPerMonthUsed: Int?,
) {
    val excludedDays: Int get() = haidDays + nifasDays

    /** The prayers that are being tracked. */
    val wakts: List<Wakt> get() = if (includeWitr) Wakt.entries else Wakt.entries - Wakt.WITR

    fun prayersOwed(wakt: Wakt): Int = if (wakt == Wakt.WITR && !includeWitr) 0 else owedDays

    fun rakatsOwed(wakt: Wakt): Int = prayersOwed(wakt) * wakt.rakats
}

object QadaCalculator {

    /**
     * Works out how many days of each prayer are owed. The rules are in SPEC.md, section 1.
     * Dates are counted as whole days (no time zones). The period is [missedFrom, startDate).
     */
    fun calculate(setup: Setup): CalculationResult {
        val periodStart = setup.missedFrom.toDate()
        val periodEnd = setup.startDate
        val periodStartDay = periodStart.toEpochDay()
        val periodEndDay = periodEnd.toEpochDay()
        val periodDays = (periodEndDay - periodStartDay).coerceAtLeast(0L).toInt()

        if (setup.gender == Gender.MALE || periodDays == 0) {
            return CalculationResult(
                periodDays = periodDays,
                haidDays = 0,
                nifasDays = 0,
                owedDays = periodDays,
                includeWitr = setup.includeWitr,
                haidDaysPerMonthUsed = null,
            )
        }

        val rules = setup.schoolChoice.rules()
        val haidPerMonth = setup.haidDaysPerMonth?.coerceIn(rules.haidMinDays, rules.haidMaxDays) ?: 0

        // Days (as epoch days) with no monthly haid, and days of nifas.
        val pauseDays = HashSet<Long>()
        val nifasDays = HashSet<Long>()
        for (event in setup.lifeEvents) {
            when (event) {
                is Birth -> addBirth(event, rules, periodEnd, pauseDays, nifasDays)
                is EarlyLoss -> addEarlyLoss(event, rules, periodEnd, pauseDays, nifasDays)
                is NoPeriods -> addNoPeriods(event, periodEnd, pauseDays)
            }
        }

        val periodRange = periodStartDay until periodEndDay
        val nifasInPeriod = nifasDays.count { it in periodRange }

        // Monthly haid, month by month. A full month sets aside exactly haidPerMonth days.
        // Partial months are scaled and rounded down, which leans toward owing slightly more.
        var haidTotal = 0
        var month = YearMonth.from(periodStart)
        val lastMonth = YearMonth.from(periodEnd.minusDays(1))
        while (!month.isAfter(lastMonth)) {
            val monthStartDay = month.atDay(1).toEpochDay()
            val monthEndDay = month.plusMonths(1).atDay(1).toEpochDay()
            val from = maxOf(monthStartDay, periodStartDay)
            val to = minOf(monthEndDay, periodEndDay)
            var eligible = 0
            for (day in from until to) {
                if (day !in pauseDays && day !in nifasDays) eligible++
            }
            haidTotal += haidPerMonth * eligible / month.lengthOfMonth()
            month = month.plusMonths(1)
        }

        val excluded = haidTotal + nifasInPeriod
        return CalculationResult(
            periodDays = periodDays,
            haidDays = haidTotal,
            nifasDays = nifasInPeriod,
            owedDays = (periodDays - excluded).coerceAtLeast(0),
            includeWitr = setup.includeWitr,
            haidDaysPerMonthUsed = haidPerMonth,
        )
    }

    /** How many are left after some have been made up. Never below zero. */
    fun remaining(owed: Int, completed: Int): Int = maxOf(0, owed - completed)

    /**
     * A starting point for people who do not remember: 15 lunar years is about
     * 14 years and 6 months after birth. The user can edit the date.
     */
    fun estimateMissedFromByAge(birthDate: LocalDate): LocalDate =
        birthDate.plusYears(14).plusMonths(6)

    // ---- life events ----

    private fun addBirth(
        birth: Birth,
        rules: SchoolRules,
        periodEnd: LocalDate,
        pause: MutableSet<Long>,
        nifas: MutableSet<Long>,
    ) {
        if (rules.pauseHaidInPregnancy) {
            pause.addRange(birth.date.minusDays(Defaults.PREGNANCY_DAYS), birth.date)
        }
        val defaultReturnDays = if (birth.breastfed == true) {
            Defaults.PERIOD_RETURN_DAYS_BREASTFED
        } else {
            Defaults.PERIOD_RETURN_DAYS_NOT_BREASTFED
        }
        val returnEnd = returnEndDate(birth.periodReturned, birth.date, defaultReturnDays, periodEnd)
        pause.addRange(birth.date, returnEnd)

        val nifasLength = minOf(birth.nifas.resolvedDays(), rules.nifasMaxDays).coerceAtLeast(0)
        nifas.addRange(birth.date, birth.date.plusDays(nifasLength.toLong()))
    }

    private fun addEarlyLoss(
        loss: EarlyLoss,
        rules: SchoolRules,
        periodEnd: LocalDate,
        pause: MutableSet<Long>,
        nifas: MutableSet<Long>,
    ) {
        val ended = loss.endedOn.lastDay()
        if (rules.pauseHaidInPregnancy) {
            pause.addRange(ended.minusWeeks(loss.weeksAlong.upperBoundWeeks.toLong()), ended)
        }
        val returnEnd = returnEndDate(
            loss.periodReturned, ended, Defaults.PERIOD_RETURN_DAYS_AFTER_LOSS, periodEnd,
        )
        pause.addRange(ended, returnEnd)

        if (loss.bleedingTreatedAs == BleedingTreatedAs.NIFAS) {
            val bleeding = minOf(loss.bleedingDays ?: 0, rules.nifasMaxDays).coerceAtLeast(0)
            nifas.addRange(ended, ended.plusDays(bleeding.toLong()))
        }
        // "Haid" or "I haven't asked": nothing extra is set aside, so she owes slightly more.
    }

    private fun addNoPeriods(event: NoPeriods, periodEnd: LocalDate, pause: MutableSet<Long>) {
        val endExclusive = event.to?.plusDays(1) ?: periodEnd
        pause.addRange(event.from, endExclusive)
    }

    private fun returnEndDate(
        returned: PeriodReturn,
        from: LocalDate,
        defaultDays: Long,
        periodEnd: LocalDate,
    ): LocalDate = when (returned) {
        // The whole month counts as still having no period.
        is PeriodReturn.ReturnedIn -> returned.month.firstDayOfNextMonth()
        PeriodReturn.NotYet -> periodEnd
        PeriodReturn.NotSure -> from.plusDays(defaultDays)
    }

    private fun MutableSet<Long>.addRange(from: LocalDate, toExclusive: LocalDate) {
        for (day in from.toEpochDay() until toExclusive.toEpochDay()) add(day)
    }
}
