package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import io.github.axiomvoidloop.takdisannurat.engine.Birth
import io.github.axiomvoidloop.takdisannurat.engine.BleedingTreatedAs
import io.github.axiomvoidloop.takdisannurat.engine.Confirmation
import io.github.axiomvoidloop.takdisannurat.engine.EarlyLoss
import io.github.axiomvoidloop.takdisannurat.engine.Gender
import io.github.axiomvoidloop.takdisannurat.engine.MissedFrom
import io.github.axiomvoidloop.takdisannurat.engine.MonthYear
import io.github.axiomvoidloop.takdisannurat.engine.NifasLength
import io.github.axiomvoidloop.takdisannurat.engine.NoPeriodReason
import io.github.axiomvoidloop.takdisannurat.engine.NoPeriods
import io.github.axiomvoidloop.takdisannurat.engine.PeriodReturn
import io.github.axiomvoidloop.takdisannurat.engine.QadaCalculator
import io.github.axiomvoidloop.takdisannurat.engine.SchoolChoice
import io.github.axiomvoidloop.takdisannurat.engine.Setup
import io.github.axiomvoidloop.takdisannurat.engine.Wakt
import io.github.axiomvoidloop.takdisannurat.engine.WeeksAlong
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {

    /** The example from SPEC.md, section 2. */
    private val validJson = """
        {
          "schemaVersion": 1,
          "app": "takdis-an-nurat",
          "exportedAt": "2026-12-20T09:30:00Z",
          "profile": {
            "gender": "female"
          },
          "setup": {
            "schoolChoice": "memory_first",
            "missedFrom": { "year": 2018, "month": null },
            "startDate": "2026-12-20",
            "includeWitr": true,
            "haid": { "daysPerMonth": 7 },
            "lifeEvents": [
              {
                "type": "birth",
                "date": "2022-05-10",
                "nifasDays": 40,
                "periodReturned": { "year": 2022, "month": 11 },
                "breastfed": null
              },
              {
                "type": "early_loss",
                "endedOn": { "year": 2024, "month": 3 },
                "weeksAlong": "4-12",
                "confirmedBy": "test_and_ultrasound",
                "bleedingDays": 5,
                "bleedingTreatedAs": "unasked",
                "periodReturned": null
              },
              {
                "type": "no_periods",
                "from": "2020-01-01",
                "to": "2020-03-31",
                "reason": "medical"
              }
            ]
          },
          "settings": {
            "displayUnit": "days",
            "theme": "black",
            "reminder": { "enabled": true, "everyMinutes": 300 },
            "quietHours": { "from": "23:00", "to": "06:00" }
          },
          "events": [
            { "id": "3f2a9c1e-0b7d-4c55-9a1e-6d2f8b4e7a10", "wakt": "fajr", "count": 1, "date": "2026-12-21" },
            { "id": "8b1d44f2-5c3a-4e07-b2a9-1f6e0c9d3a55", "wakt": "zuhr", "count": 3, "date": "2026-12-21" },
            { "id": "c47e2a90-1d6b-49f3-8e5c-72a0b3d1f6e8", "wakt": "isha", "count": -1, "date": "2026-12-22", "kind": "adjustment" }
          ],
          "summary": {
            "note": "Informational only. Ignored on import.",
            "daysOwed": 2645
          }
        }
    """.trimIndent()

    private val fatimaSetup = Setup(
        gender = Gender.FEMALE,
        schoolChoice = SchoolChoice.MEMORY_FIRST,
        missedFrom = MissedFrom(2018),
        startDate = LocalDate.of(2026, 12, 20),
        includeWitr = true,
        haidDaysPerMonth = 7,
        lifeEvents = listOf(
            Birth(
                date = LocalDate.of(2022, 5, 10),
                nifas = NifasLength.Days(40),
                periodReturned = PeriodReturn.ReturnedIn(MonthYear(2022, 11)),
                breastfed = null,
            ),
            EarlyLoss(
                endedOn = MonthYear(2024, 3),
                weeksAlong = WeeksAlong.FROM_4_TO_12,
                confirmedBy = Confirmation.TEST_AND_ULTRASOUND,
                bleedingDays = 5,
                bleedingTreatedAs = BleedingTreatedAs.UNASKED,
                periodReturned = PeriodReturn.NotSure,
            ),
            NoPeriods(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 31), NoPeriodReason.MEDICAL),
        ),
    )

    private val fatimaEvents = listOf(
        PrayerEvent("3f2a9c1e-0b7d-4c55-9a1e-6d2f8b4e7a10", Wakt.FAJR, 1, LocalDate.of(2026, 12, 21)),
        PrayerEvent("8b1d44f2-5c3a-4e07-b2a9-1f6e0c9d3a55", Wakt.ZUHR, 3, LocalDate.of(2026, 12, 21)),
        PrayerEvent("c47e2a90-1d6b-49f3-8e5c-72a0b3d1f6e8", Wakt.ISHA, -1, LocalDate.of(2026, 12, 22), EventKind.ADJUSTMENT),
    )

    private val fatima = AppData(fatimaSetup, Settings(), fatimaEvents)

    private fun decoded(text: String): AppData {
        return when (val result = BackupCodec.decode(text)) {
            is ImportResult.Success -> result.data
            is ImportResult.Failure -> throw AssertionError("expected success, but: ${result.message}")
        }
    }

    private fun assertRejected(text: String, mention: String) {
        when (val result = BackupCodec.decode(text)) {
            is ImportResult.Success -> fail("should have been rejected ($mention)")
            is ImportResult.Failure -> assertTrue(
                "message \"${result.message}\" should mention \"$mention\"",
                result.message.contains(mention),
            )
        }
    }

    private fun rejectedAfter(old: String, new: String, mention: String) {
        assertTrue("test setup: \"$old\" not found", validJson.contains(old))
        assertRejected(validJson.replace(old, new), mention)
    }

    // ---- reading the spec example ----

    @Test
    fun readsTheExampleFromTheSpec() {
        assertEquals(fatima, decoded(validJson))
    }

    @Test
    fun theExampleGivesTheNumbersInTheSpec() {
        // Fatima's example with a birth, an early loss and a medical stretch (checked against the Python prototype).
        val result = QadaCalculator.calculate(decoded(validJson).setup)
        assertEquals(3275, result.periodDays)
        assertEquals(630, result.excludedDays)
        assertEquals(2645, result.owedDays)
    }

    // ---- round trips ----

    @Test
    fun whatIsExportedCanBeImported() {
        val text = BackupCodec.encode(fatima, Instant.parse("2026-12-20T09:30:00Z"))
        assertEquals(fatima, decoded(text))
    }

    @Test
    fun aManRoundTrips() {
        val man = AppData(
            Setup(Gender.MALE, SchoolChoice.HANAFI_HANBALI, MissedFrom(2015, 6), LocalDate.of(2026, 12, 20), false),
            Settings(DisplayUnit.RAKATS, Theme.WHITE, ReminderSettings(false, 45), QuietHours(LocalTime.of(22, 30), LocalTime.of(5, 15))),
            listOf(PrayerEvent("one", Wakt.MAGHRIB, 2, LocalDate.of(2027, 1, 2))),
        )
        assertEquals(man, decoded(BackupCodec.encode(man)))
    }

    @Test
    fun everyKindOfLifeEventAndReturnRoundTrips() {
        val setup = fatimaSetup.copy(
            lifeEvents = listOf(
                Birth(LocalDate.of(2019, 1, 5), NifasLength.Days(33), PeriodReturn.NotYet, true),
                Birth(LocalDate.of(2021, 2, 6), NifasLength.Days(0), PeriodReturn.NotSure, false),
                EarlyLoss(MonthYear(2023, 7), WeeksAlong.UNDER_4, Confirmation.MISSED_PERIOD, null, BleedingTreatedAs.HAID, PeriodReturn.NotYet),
                EarlyLoss(MonthYear(2024, 1), WeeksAlong.FROM_12_TO_20, Confirmation.TEST, 8, BleedingTreatedAs.NIFAS, PeriodReturn.ReturnedIn(MonthYear(2024, 3))),
                NoPeriods(LocalDate.of(2025, 6, 1), null, NoPeriodReason.BREASTFEEDING),
            ),
        )
        val data = AppData(setup)
        assertEquals(data, decoded(BackupCodec.encode(data)))
    }

    @Test
    fun anApproximateNifasLengthIsSavedAsDays() {
        val setup = fatimaSetup.copy(
            lifeEvents = listOf(Birth(LocalDate.of(2022, 5, 10), NifasLength.ApproxWeeks(4))),
        )
        val back = decoded(BackupCodec.encode(AppData(setup)))
        val birth = back.setup.lifeEvents.single() as Birth
        assertEquals(NifasLength.Days(28), birth.nifas)
    }

    @Test
    fun theSummaryHoldsTheDaysOwedButIsNotUsedOnImport() {
        val text = BackupCodec.encode(fatima)
        val root = MiniJson.parse(text) as Map<*, *>
        val summary = root["summary"] as Map<*, *>
        assertEquals(QadaCalculator.calculate(fatimaSetup).owedDays.toLong(), summary["daysOwed"])
        val tampered = text.replace("\"daysOwed\": ${summary["daysOwed"]}", "\"daysOwed\": 1")
        assertEquals(fatima, decoded(tampered))
    }

    @Test
    fun theExportedTextIsStableAndReadable() {
        val text = BackupCodec.encode(fatima, Instant.parse("2026-12-20T09:30:45.999Z"))
        assertTrue(text.startsWith("{\n  \"schemaVersion\": 1,\n  \"app\": \"takdis-an-nurat\",\n  \"exportedAt\": \"2026-12-20T09:30:45Z\""))
        assertTrue(text.endsWith("}\n"))
    }

    @Test
    fun thePinOrAnyLockDataIsNeverInTheFile() {
        val text = BackupCodec.encode(fatima).lowercase()
        assertTrue(!text.contains("pin"))
        assertTrue(!text.contains("lock"))
    }

    // ---- leniency ----

    @Test
    fun unknownExtraFieldsAreIgnored() {
        val text = validJson.replace("\"app\": \"takdis-an-nurat\",", "\"app\": \"takdis-an-nurat\",\n  \"futureField\": [1, 2, 3],")
        assertEquals(fatima, decoded(text))
    }

    @Test
    fun settingsAndEventsMayBeLeftOut() {
        val minimal = """
            {"schemaVersion": 1, "app": "takdis-an-nurat",
             "profile": {"gender": "male"},
             "setup": {"schoolChoice": "not_sure", "missedFrom": {"year": 2015},
                       "startDate": "2026-12-20", "includeWitr": true}}
        """.trimIndent()
        val data = decoded(minimal)
        assertEquals(Settings(), data.settings)
        assertEquals(emptyList<PrayerEvent>(), data.events)
        assertEquals(4371, QadaCalculator.calculate(data.setup).owedDays)
    }

    // ---- refusing bad files ----

    @Test
    fun refusesTextThatIsNotJson() {
        assertRejected("this is not json", "not valid JSON")
        assertRejected("", "not valid JSON")
        assertRejected("[1, 2, 3]", "must be an object")
    }

    @Test
    fun refusesANewerVersion() {
        rejectedAfter("\"schemaVersion\": 1,", "\"schemaVersion\": 2,", "update the app")
        rejectedAfter("\"schemaVersion\": 1,", "\"schemaVersion\": 0,", "schemaVersion")
        rejectedAfter("\"schemaVersion\": 1,", "", "schemaVersion")
        rejectedAfter("\"schemaVersion\": 1,", "\"schemaVersion\": \"1\",", "schemaVersion")
    }

    @Test
    fun refusesAFileFromAnotherApp() {
        rejectedAfter("\"app\": \"takdis-an-nurat\",", "\"app\": \"other-app\",", "app")
        rejectedAfter("\"app\": \"takdis-an-nurat\",", "", "app")
    }

    @Test
    fun refusesInvalidSetupValues() {
        rejectedAfter("\"startDate\": \"2026-12-20\"", "\"startDate\": \"20-12-2026\"", "setup.startDate")
        rejectedAfter("\"startDate\": \"2026-12-20\"", "\"startDate\": \"2026-02-30\"", "setup.startDate")
        rejectedAfter("\"startDate\": \"2026-12-20\"", "\"startDate\": 20261220", "setup.startDate")
        rejectedAfter("\"schoolChoice\": \"memory_first\"", "\"schoolChoice\": \"other\"", "setup.schoolChoice")
        rejectedAfter("\"includeWitr\": true", "\"includeWitr\": \"yes\"", "setup.includeWitr")
        rejectedAfter("\"year\": 2018", "\"year\": 1800", "setup.missedFrom.year")
        rejectedAfter("\"year\": 2018, \"month\": null", "\"year\": 2018, \"month\": 13", "setup.missedFrom.month")
        rejectedAfter("\"year\": 2018, \"month\": null", "\"year\": 2018.5, \"month\": null", "setup.missedFrom.year")
    }

    @Test
    fun refusesAProfileThatDoesNotFit() {
        rejectedAfter("\"daysPerMonth\": 7", "\"daysPerMonth\": 20", "setup.haid.daysPerMonth")
        rejectedAfter("\"daysPerMonth\": 7", "\"daysPerMonth\": 0", "setup.haid.daysPerMonth")
        rejectedAfter("\"haid\": { \"daysPerMonth\": 7 },", "", "setup.haid")
        rejectedAfter("\"gender\": \"female\"", "\"gender\": \"male\"", "male profile")
        rejectedAfter("\"gender\": \"female\"", "\"gender\": \"other\"", "profile.gender")
    }

    @Test
    fun refusesInvalidLifeEvents() {
        rejectedAfter("\"type\": \"birth\"", "\"type\": \"wedding\"", "setup.lifeEvents[0].type")
        rejectedAfter("\"nifasDays\": 40", "\"nifasDays\": 90", "setup.lifeEvents[0].nifasDays")
        rejectedAfter("\"date\": \"2022-05-10\"", "\"date\": \"May 10\"", "setup.lifeEvents[0].date")
        rejectedAfter("\"periodReturned\": { \"year\": 2022, \"month\": 11 }", "\"periodReturned\": \"soon\"", "periodReturned")
        rejectedAfter("\"weeksAlong\": \"4-12\"", "\"weeksAlong\": \"5\"", "weeksAlong")
        rejectedAfter("\"confirmedBy\": \"test_and_ultrasound\"", "\"confirmedBy\": \"guess\"", "confirmedBy")
        rejectedAfter("\"to\": \"2020-03-31\"", "\"to\": \"2019-12-31\"", "must not be before")
        rejectedAfter("\"reason\": \"medical\"", "\"reason\": \"fun\"", "reason")
    }

    @Test
    fun refusesInvalidPrayerEvents() {
        rejectedAfter("\"wakt\": \"fajr\"", "\"wakt\": \"dawn\"", "events[0].wakt")
        rejectedAfter("\"count\": 1, \"date\"", "\"count\": 0, \"date\"", "events[0].count")
        rejectedAfter("\"count\": 1, \"date\"", "\"count\": -1, \"date\"", "must be positive")
        rejectedAfter("\"count\": 1, \"date\"", "\"count\": 1.5, \"date\"", "events[0].count")
        rejectedAfter("\"count\": 1, \"date\"", "\"count\": 99999999, \"date\"", "events[0].count")
        rejectedAfter("\"kind\": \"adjustment\"", "\"kind\": \"magic\"", "events[2].kind")
        rejectedAfter("\"id\": \"8b1d44f2-5c3a-4e07-b2a9-1f6e0c9d3a55\"", "\"id\": \"3f2a9c1e-0b7d-4c55-9a1e-6d2f8b4e7a10\"", "appears more than once")
        rejectedAfter("\"id\": \"8b1d44f2-5c3a-4e07-b2a9-1f6e0c9d3a55\"", "\"id\": \"\"", "events[1].id")
        rejectedAfter("\"date\": \"2026-12-22\"", "\"date\": \"tomorrow\"", "events[2].date")
    }

    @Test
    fun refusesInvalidSettings() {
        rejectedAfter("\"everyMinutes\": 300", "\"everyMinutes\": 5", "settings.reminder.everyMinutes")
        rejectedAfter("\"everyMinutes\": 300", "\"everyMinutes\": 999999", "settings.reminder.everyMinutes")
        rejectedAfter("\"from\": \"23:00\"", "\"from\": \"25:00\"", "settings.quietHours.from")
        rejectedAfter("\"theme\": \"black\"", "\"theme\": \"red\"", "settings.theme")
        rejectedAfter("\"displayUnit\": \"days\"", "\"displayUnit\": \"miles\"", "settings.displayUnit")
        rejectedAfter("\"enabled\": true", "\"enabled\": 1", "settings.reminder.enabled")
    }

    @Test
    fun aRefusedFileChangesNothing() {
        // decode() only returns a result; it never touches the data the app already has.
        val result = BackupCodec.decode(validJson.replace("\"wakt\": \"fajr\"", "\"wakt\": \"dawn\""))
        assertTrue(result is ImportResult.Failure)
    }

    // ---- merging ----

    @Test
    fun mergeAddsOnlyNewEvents() {
        val current = AppData(
            fatimaSetup.copy(haidDaysPerMonth = 9),
            Settings(theme = Theme.WHITE),
            listOf(fatimaEvents[0]),
        )
        val merged = BackupCodec.merge(current, fatima)
        assertEquals(2, merged.added)
        assertEquals(1, merged.skipped)
        assertEquals(listOf(fatimaEvents[0], fatimaEvents[1], fatimaEvents[2]), merged.data.events)
        // The current setup and settings are kept.
        assertEquals(9, merged.data.setup.haidDaysPerMonth)
        assertEquals(Theme.WHITE, merged.data.settings.theme)
    }

    @Test
    fun mergingTheSameBackupTwiceChangesNothing() {
        val once = BackupCodec.merge(fatima.copy(events = emptyList()), fatima)
        val twice = BackupCodec.merge(once.data, fatima)
        assertEquals(0, twice.added)
        assertEquals(3, twice.skipped)
        assertEquals(once.data, twice.data)
    }
}
