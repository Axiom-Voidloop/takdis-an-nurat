package io.github.axiomvoidloop.takdisannurat.engine

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The expected numbers come from the worked examples in SPEC.md (section 1.6) and from an
 * independent prototype of the same rules written in Python.
 */
class CalculatorTest {

    private val startDate = LocalDate.of(2026, 12, 20)

    private val birth = Birth(
        date = LocalDate.of(2022, 5, 10),
        nifas = NifasLength.Days(40),
        periodReturned = PeriodReturn.ReturnedIn(MonthYear(2022, 11)),
    )

    private fun woman(
        school: SchoolChoice,
        events: List<LifeEvent> = emptyList(),
        haid: Int? = 7,
        missedFrom: MissedFrom = MissedFrom(2018),
        start: LocalDate = startDate,
    ) = Setup(
        gender = Gender.FEMALE,
        schoolChoice = school,
        missedFrom = missedFrom,
        startDate = start,
        includeWitr = true,
        haidDaysPerMonth = haid,
        lifeEvents = events,
    )

    // ---- men ----

    @Test
    fun faridOwes4371Days() {
        val farid = Setup(Gender.MALE, SchoolChoice.MEMORY_FIRST, MissedFrom(2015), startDate, true)
        val result = QadaCalculator.calculate(farid)
        assertEquals(4371, result.periodDays)
        assertEquals(4371, result.owedDays)
        assertEquals(0, result.excludedDays)
    }

    @Test
    fun faridRakats() {
        val farid = Setup(Gender.MALE, SchoolChoice.HANAFI_HANBALI, MissedFrom(2015), startDate, true)
        val result = QadaCalculator.calculate(farid)
        assertEquals(8742, result.rakatsOwed(Wakt.FAJR))
        assertEquals(17484, result.rakatsOwed(Wakt.ZUHR))
        assertEquals(17484, result.rakatsOwed(Wakt.ASR))
        assertEquals(13113, result.rakatsOwed(Wakt.MAGHRIB))
        assertEquals(17484, result.rakatsOwed(Wakt.ISHA))
        assertEquals(13113, result.rakatsOwed(Wakt.WITR))
    }

    @Test
    fun schoolDoesNotChangeMensTotals() {
        val totals = SchoolChoice.entries.map {
            QadaCalculator.calculate(Setup(Gender.MALE, it, MissedFrom(2015), startDate, true)).owedDays
        }
        assertEquals(1, totals.toSet().size)
    }

    @Test
    fun witrCanBeLeftOut() {
        val man = Setup(Gender.MALE, SchoolChoice.NOT_SURE, MissedFrom(2015), startDate, false)
        val result = QadaCalculator.calculate(man)
        assertEquals(5, result.wakts.size)
        assertEquals(0, result.prayersOwed(Wakt.WITR))
        assertEquals(4371, result.prayersOwed(Wakt.FAJR))
    }

    @Test
    fun startBeforeMissedFromGivesZero() {
        val man = Setup(Gender.MALE, SchoolChoice.NOT_SURE, MissedFrom(2026, 12), LocalDate.of(2026, 6, 1), true)
        assertEquals(0, QadaCalculator.calculate(man).owedDays)
    }

    // ---- women, monthly haid only ----

    @Test
    fun fatimaWithoutLifeEvents() {
        val result = QadaCalculator.calculate(woman(SchoolChoice.MEMORY_FIRST))
        assertEquals(3275, result.periodDays)
        assertEquals(753, result.excludedDays)
        assertEquals(2522, result.owedDays)
    }

    @Test
    fun aFullMonthSetsAsideExactlyTheCycleDays() {
        val result = QadaCalculator.calculate(
            woman(SchoolChoice.MEMORY_FIRST, missedFrom = MissedFrom(2020, 1), start = LocalDate.of(2020, 2, 1)),
        )
        assertEquals(31, result.periodDays)
        assertEquals(7, result.excludedDays)
        assertEquals(24, result.owedDays)
    }

    @Test
    fun aPartialMonthIsScaledAndRoundedDown() {
        // 19 days of a 31-day month: floor(7 * 19 / 31) = 4
        val result = QadaCalculator.calculate(
            woman(SchoolChoice.MEMORY_FIRST, missedFrom = MissedFrom(2020, 1), start = LocalDate.of(2020, 1, 20)),
        )
        assertEquals(19, result.periodDays)
        assertEquals(4, result.excludedDays)
        assertEquals(15, result.owedDays)
    }

    @Test
    fun haidDaysAreFittedToTheSchoolsRange() {
        val tooLong = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, haid = 12))
        assertEquals(10, tooLong.haidDaysPerMonthUsed)
        val tooShort = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, haid = 1))
        assertEquals(3, tooShort.haidDaysPerMonthUsed)
        val wide = QadaCalculator.calculate(woman(SchoolChoice.SHAFII_MALIKI, haid = 12))
        assertEquals(12, wide.haidDaysPerMonthUsed)
    }

    // ---- women, births ----

    @Test
    fun fatimaWithABirthHanafiHanbali() {
        val result = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(birth)))
        assertEquals(681, result.excludedDays)
        assertEquals(2594, result.owedDays)
        assertEquals(5188, result.rakatsOwed(Wakt.FAJR))
    }

    @Test
    fun fatimaWithABirthShafiiMaliki() {
        val result = QadaCalculator.calculate(woman(SchoolChoice.SHAFII_MALIKI, listOf(birth)))
        assertEquals(746, result.excludedDays)
        assertEquals(2529, result.owedDays)
        assertEquals(5058, result.rakatsOwed(Wakt.FAJR))
    }

    @Test
    fun noSchoolBehavesLikeBiology() {
        val memory = QadaCalculator.calculate(woman(SchoolChoice.MEMORY_FIRST, listOf(birth)))
        val notSure = QadaCalculator.calculate(woman(SchoolChoice.NOT_SURE, listOf(birth)))
        assertEquals(2594, memory.owedDays)
        assertEquals(2594, notSure.owedDays)
    }

    @Test
    fun nifasIsCappedBySchool() {
        val fiftyFive = birth.copy(nifas = NifasLength.Days(55))
        assertEquals(2594, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(fiftyFive))).owedDays)
        assertEquals(2514, QadaCalculator.calculate(woman(SchoolChoice.SHAFII_MALIKI, listOf(fiftyFive))).owedDays)
    }

    @Test
    fun approximateWeeksBecomeDays() {
        assertEquals(42, NifasLength.ApproxWeeks(6).resolvedDays())
        assertEquals(14, NifasLength.ApproxWeeks(2).resolvedDays())
        val sixWeeks = birth.copy(nifas = NifasLength.ApproxWeeks(6))
        // Capped at 40 for Hanafi/Hanbali, allowed in full (42) for Shafi'i/Maliki.
        assertEquals(2594, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(sixWeeks))).owedDays)
        assertEquals(2527, QadaCalculator.calculate(woman(SchoolChoice.SHAFII_MALIKI, listOf(sixWeeks))).owedDays)
    }

    @Test
    fun whenUnsureOfTheReturnTheDefaultsLeanCautious() {
        val notBreastfed = birth.copy(periodReturned = PeriodReturn.NotSure, breastfed = false)
        val breastfed = birth.copy(periodReturned = PeriodReturn.NotSure, breastfed = true)
        assertEquals(2567, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(notBreastfed))).owedDays)
        assertEquals(2589, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(breastfed))).owedDays)
    }

    @Test
    fun periodNotBackYetRunsToTheStartDate() {
        val notYet = birth.copy(periodReturned = PeriodReturn.NotYet)
        assertEquals(2934, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(notYet))).owedDays)
    }

    // ---- women, early losses ----

    private val loss = EarlyLoss(
        endedOn = MonthYear(2024, 3),
        weeksAlong = WeeksAlong.FROM_4_TO_12,
        confirmedBy = Confirmation.TEST_AND_ULTRASOUND,
        bleedingDays = 5,
        bleedingTreatedAs = BleedingTreatedAs.NIFAS,
    )

    @Test
    fun earlyLossByTheThreeSchoolSettings() {
        assertEquals(2547, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(loss))).owedDays)
        assertEquals(2528, QadaCalculator.calculate(woman(SchoolChoice.SHAFII_MALIKI, listOf(loss))).owedDays)
        assertEquals(2547, QadaCalculator.calculate(woman(SchoolChoice.MEMORY_FIRST, listOf(loss))).owedDays)
    }

    @Test
    fun earlyLossBleedingNotTreatedAsNifasSetsNothingExtraAside() {
        val unasked = loss.copy(bleedingTreatedAs = BleedingTreatedAs.UNASKED)
        assertEquals(2552, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(unasked))).owedDays)
    }

    @Test
    fun earlyLossWithAKnownReturn() {
        val returned = loss.copy(periodReturned = PeriodReturn.ReturnedIn(MonthYear(2024, 5)))
        assertEquals(2551, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(returned))).owedDays)
    }

    @Test
    fun theConfirmationMethodNeverChangesTheNumbers() {
        val results = Confirmation.entries.map {
            QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(loss.copy(confirmedBy = it)))).owedDays
        }
        assertEquals(1, results.toSet().size)
    }

    // ---- women, other times with no periods ----

    @Test
    fun anotherTimeWithNoPeriods() {
        val medical = NoPeriods(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), NoPeriodReason.MEDICAL)
        val result = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(birth, medical)))
        assertEquals(660, result.excludedDays)
        assertEquals(2615, result.owedDays)
    }

    @Test
    fun aStretchStillGoingOnRunsToTheStartDate() {
        val ongoing = NoPeriods(LocalDate.of(2025, 6, 1), null, NoPeriodReason.BREASTFEEDING)
        assertEquals(2652, QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(ongoing))).owedDays)
    }

    @Test
    fun overlappingStretchesAreMergedNotCountedTwice() {
        val inside = NoPeriods(LocalDate.of(2022, 6, 1), LocalDate.of(2022, 8, 31), NoPeriodReason.OTHER)
        val withOverlap = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(birth, inside)))
        val without = QadaCalculator.calculate(woman(SchoolChoice.HANAFI_HANBALI, listOf(birth)))
        assertEquals(without.owedDays, withOverlap.owedDays)
    }

    // ---- helpers ----

    @Test
    fun remainingNeverGoesBelowZero() {
        assertEquals(94, QadaCalculator.remaining(owed = 100, completed = 6))
        assertEquals(0, QadaCalculator.remaining(owed = 100, completed = 130))
    }

    @Test
    fun theAgeEstimateIsFourteenYearsAndSixMonths() {
        val estimate = QadaCalculator.estimateMissedFromByAge(LocalDate.of(2000, 3, 15))
        assertEquals(LocalDate.of(2014, 9, 15), estimate)
    }

    @Test
    fun missedFromDefaultsToJanuary() {
        assertEquals(LocalDate.of(2015, 1, 1), MissedFrom(2015).toDate())
        assertEquals(LocalDate.of(2015, 6, 1), MissedFrom(2015, 6).toDate())
        assertTrue(runCatching { MissedFrom(2015, 13) }.isFailure)
    }
}
