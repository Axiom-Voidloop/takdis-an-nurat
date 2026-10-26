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
import io.github.axiomvoidloop.takdisannurat.engine.LifeEvent
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
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

class BackupFormatException(message: String) : Exception(message)

sealed interface ImportResult {
    data class Success(val data: AppData) : ImportResult
    data class Failure(val message: String) : ImportResult
}

data class MergeResult(val data: AppData, val added: Int, val skipped: Int)

/**
 * Turns [AppData] into the backup JSON and back. The format is described in SPEC.md, section 2.
 * Reading is strict: if anything in the file is invalid, the whole file is refused.
 */
object BackupCodec {
    const val APP_ID = "takdis-an-nurat"
    const val SCHEMA_VERSION = 1

    private const val MAX_TEXT_LENGTH = 20_000_000
    private const val MAX_EVENTS = 200_000
    private const val MAX_LIFE_EVENTS = 500
    private const val MAX_ID_LENGTH = 100
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // ---------------------------------------------------------------- writing

    fun encode(data: AppData, now: Instant = Instant.now()): String {
        val setup = data.setup
        val root = linkedMapOf<String, Any?>(
            "schemaVersion" to SCHEMA_VERSION,
            "app" to APP_ID,
            "exportedAt" to now.truncatedTo(ChronoUnit.SECONDS).toString(),
            "profile" to linkedMapOf<String, Any?>("gender" to token(setup.gender)),
            "setup" to encodeSetup(setup),
            "settings" to encodeSettings(data.settings),
            "events" to data.events.map { encodeEvent(it) },
            "summary" to linkedMapOf<String, Any?>(
                "note" to "Informational only. Ignored on import.",
                "daysOwed" to QadaCalculator.calculate(setup).owedDays,
            ),
        )
        return MiniJson.stringify(root) + "\n"
    }

    private fun token(e: Enum<*>): String = e.name.lowercase()

    private fun encodeSetup(s: Setup): Map<String, Any?> = linkedMapOf(
        "schoolChoice" to token(s.schoolChoice),
        "missedFrom" to linkedMapOf<String, Any?>("year" to s.missedFrom.year, "month" to s.missedFrom.month),
        "startDate" to s.startDate.toString(),
        "includeWitr" to s.includeWitr,
        "haid" to if (s.gender == Gender.FEMALE) {
            linkedMapOf<String, Any?>("daysPerMonth" to s.haidDaysPerMonth)
        } else {
            null
        },
        "lifeEvents" to s.lifeEvents.map { encodeLifeEvent(it) },
    )

    private fun encodeMonthYear(m: MonthYear): Map<String, Any?> =
        linkedMapOf("year" to m.year, "month" to m.month)

    private fun encodePeriodReturned(r: PeriodReturn): Any? = when (r) {
        is PeriodReturn.ReturnedIn -> encodeMonthYear(r.month)
        PeriodReturn.NotYet -> "not_yet"
        PeriodReturn.NotSure -> null
    }

    private fun encodeWeeksAlong(w: WeeksAlong): String = when (w) {
        WeeksAlong.UNDER_4 -> "under-4"
        WeeksAlong.FROM_4_TO_12 -> "4-12"
        WeeksAlong.FROM_12_TO_20 -> "12-20"
    }

    private fun encodeLifeEvent(e: LifeEvent): Map<String, Any?> = when (e) {
        is Birth -> linkedMapOf(
            "type" to "birth",
            "date" to e.date.toString(),
            "nifasDays" to e.nifas.resolvedDays(),
            "periodReturned" to encodePeriodReturned(e.periodReturned),
            "breastfed" to e.breastfed,
        )
        is EarlyLoss -> linkedMapOf(
            "type" to "early_loss",
            "endedOn" to encodeMonthYear(e.endedOn),
            "weeksAlong" to encodeWeeksAlong(e.weeksAlong),
            "confirmedBy" to token(e.confirmedBy),
            "bleedingDays" to e.bleedingDays,
            "bleedingTreatedAs" to token(e.bleedingTreatedAs),
            "periodReturned" to encodePeriodReturned(e.periodReturned),
        )
        is NoPeriods -> linkedMapOf(
            "type" to "no_periods",
            "from" to e.from.toString(),
            "to" to e.to?.toString(),
            "reason" to token(e.reason),
        )
    }

    private fun encodeSettings(s: Settings): Map<String, Any?> = linkedMapOf(
        "displayUnit" to token(s.displayUnit),
        "theme" to token(s.theme),
        "reminder" to linkedMapOf<String, Any?>(
            "enabled" to s.reminder.enabled,
            "everyMinutes" to s.reminder.everyMinutes,
        ),
        "quietHours" to linkedMapOf<String, Any?>(
            "from" to s.quietHours.from.format(TIME_FORMAT),
            "to" to s.quietHours.to.format(TIME_FORMAT),
        ),
    )

    private fun encodeEvent(e: PrayerEvent): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "id" to e.id,
            "wakt" to token(e.wakt),
            "count" to e.count,
            "date" to e.date.toString(),
        )
        if (e.kind == EventKind.ADJUSTMENT) map["kind"] = "adjustment"
        return map
    }

    // ---------------------------------------------------------------- reading

    /** Reads a backup. Never throws for a bad file: the result says what is wrong. */
    fun decode(text: String): ImportResult = try {
        ImportResult.Success(decodeOrThrow(text))
    } catch (e: BackupFormatException) {
        ImportResult.Failure(e.message ?: "The file is not a valid backup.")
    } catch (e: MiniJson.ParseException) {
        ImportResult.Failure("The file is not valid JSON: ${e.message}")
    }

    fun decodeOrThrow(text: String): AppData {
        if (text.length > MAX_TEXT_LENGTH) bad("file", "is too large")
        val root = MiniJson.parse(text).asObject("file")

        val version = root.reqInt("", "schemaVersion", Int.MIN_VALUE..Int.MAX_VALUE)
        if (version > SCHEMA_VERSION) {
            bad(
                "schemaVersion",
                "is $version, but this version of the app only understands version $SCHEMA_VERSION. Please update the app.",
            )
        }
        if (version < 1) bad("schemaVersion", "must be 1 or higher")
        if (root.reqString("", "app") != APP_ID) bad("app", "must be \"$APP_ID\"")

        val gender = root.reqObject("", "profile").reqEnum<Gender>("profile", "gender")
        val setup = decodeSetup(root.reqObject("", "setup"), gender)
        val settings = root.optObject("", "settings")?.let { decodeSettings(it) } ?: Settings()
        val events = decodeEvents(root["events"])
        return AppData(setup, settings, events)
    }

    private fun decodeSetup(obj: Map<String, Any?>, gender: Gender): Setup {
        val school = obj.reqEnum<SchoolChoice>("setup", "schoolChoice")
        val missed = obj.reqObject("setup", "missedFrom")
        val missedFrom = MissedFrom(
            year = missed.reqInt("setup.missedFrom", "year", 1900..2200),
            month = missed.optInt("setup.missedFrom", "month", 1..12),
        )
        val startDate = obj.reqDate("setup", "startDate")
        val includeWitr = obj.reqBoolean("setup", "includeWitr")

        val haid = obj.optObject("setup", "haid")
        val lifeRaw = obj["lifeEvents"]

        if (gender == Gender.MALE) {
            if (haid != null) bad("setup.haid", "must be empty for a male profile")
            if (lifeRaw != null && (lifeRaw !is List<*> || lifeRaw.isNotEmpty())) {
                bad("setup.lifeEvents", "must be empty for a male profile")
            }
            return Setup(gender, school, missedFrom, startDate, includeWitr)
        }

        if (haid == null) bad("setup.haid", "is required for a female profile")
        val haidDays = haid.reqInt("setup.haid", "daysPerMonth", 1..15)
        val lifeEvents = decodeLifeEvents(lifeRaw)
        return Setup(gender, school, missedFrom, startDate, includeWitr, haidDays, lifeEvents)
    }

    private fun decodeLifeEvents(raw: Any?): List<LifeEvent> {
        if (raw == null) return emptyList()
        if (raw !is List<*>) bad("setup.lifeEvents", "must be a list")
        if (raw.size > MAX_LIFE_EVENTS) bad("setup.lifeEvents", "has too many entries")
        return raw.mapIndexed { index, item ->
            val path = "setup.lifeEvents[$index]"
            val obj = item.asObject(path)
            when (val type = obj.reqString(path, "type")) {
                "birth" -> Birth(
                    date = obj.reqDate(path, "date"),
                    nifas = NifasLength.Days(obj.reqInt(path, "nifasDays", 0..60)),
                    periodReturned = decodePeriodReturned(obj["periodReturned"], "$path.periodReturned"),
                    breastfed = obj.optBoolean(path, "breastfed"),
                )
                "early_loss" -> EarlyLoss(
                    endedOn = decodeMonthYear(obj.reqObject(path, "endedOn"), "$path.endedOn"),
                    weeksAlong = decodeWeeksAlong(obj.reqString(path, "weeksAlong"), "$path.weeksAlong"),
                    confirmedBy = obj.reqEnum<Confirmation>(path, "confirmedBy"),
                    bleedingDays = obj.optInt(path, "bleedingDays", 1..60),
                    bleedingTreatedAs = obj.optEnum<BleedingTreatedAs>(path, "bleedingTreatedAs")
                        ?: BleedingTreatedAs.UNASKED,
                    periodReturned = decodePeriodReturned(obj["periodReturned"], "$path.periodReturned"),
                )
                "no_periods" -> {
                    val from = obj.reqDate(path, "from")
                    val to = obj.optDate(path, "to")
                    if (to != null && to.isBefore(from)) bad("$path.to", "must not be before \"from\"")
                    NoPeriods(from, to, obj.reqEnum<NoPeriodReason>(path, "reason"))
                }
                else -> bad("$path.type", "must be \"birth\", \"early_loss\" or \"no_periods\" (found \"$type\")")
            }
        }
    }

    private fun decodeMonthYear(obj: Map<String, Any?>, path: String): MonthYear =
        MonthYear(obj.reqInt(path, "year", 1900..2200), obj.reqInt(path, "month", 1..12))

    private fun decodePeriodReturned(raw: Any?, path: String): PeriodReturn = when {
        raw == null -> PeriodReturn.NotSure
        raw == "not_yet" -> PeriodReturn.NotYet
        raw is Map<*, *> -> PeriodReturn.ReturnedIn(decodeMonthYear(raw.asObject(path), path))
        else -> bad(path, "must be empty, \"not_yet\", or an object with a year and a month")
    }

    private fun decodeWeeksAlong(text: String, path: String): WeeksAlong = when (text) {
        "under-4" -> WeeksAlong.UNDER_4
        "4-12" -> WeeksAlong.FROM_4_TO_12
        "12-20" -> WeeksAlong.FROM_12_TO_20
        else -> bad(path, "must be \"under-4\", \"4-12\" or \"12-20\"")
    }

    private fun decodeSettings(obj: Map<String, Any?>): Settings {
        val defaults = Settings()
        val reminderObj = obj.optObject("settings", "reminder")
        val reminder = if (reminderObj == null) {
            defaults.reminder
        } else {
            ReminderSettings(
                enabled = reminderObj.optBoolean("settings.reminder", "enabled") ?: defaults.reminder.enabled,
                everyMinutes = reminderObj.optInt(
                    "settings.reminder", "everyMinutes",
                    ReminderSettings.MIN_MINUTES..ReminderSettings.MAX_MINUTES,
                ) ?: defaults.reminder.everyMinutes,
            )
        }
        val quietObj = obj.optObject("settings", "quietHours")
        val quiet = if (quietObj == null) {
            defaults.quietHours
        } else {
            QuietHours(
                from = quietObj.optTime("settings.quietHours", "from") ?: defaults.quietHours.from,
                to = quietObj.optTime("settings.quietHours", "to") ?: defaults.quietHours.to,
            )
        }
        return Settings(
            displayUnit = obj.optEnum<DisplayUnit>("settings", "displayUnit") ?: defaults.displayUnit,
            theme = obj.optEnum<Theme>("settings", "theme") ?: defaults.theme,
            reminder = reminder,
            quietHours = quiet,
        )
    }

    private fun decodeEvents(raw: Any?): List<PrayerEvent> {
        if (raw == null) return emptyList()
        if (raw !is List<*>) bad("events", "must be a list")
        if (raw.size > MAX_EVENTS) bad("events", "has too many entries")
        val seen = HashSet<String>()
        return raw.mapIndexed { index, item ->
            val path = "events[$index]"
            val obj = item.asObject(path)
            val id = obj.reqString(path, "id")
            if (id.isBlank() || id.length > MAX_ID_LENGTH) bad("$path.id", "must be 1 to $MAX_ID_LENGTH characters")
            if (!seen.add(id)) bad("$path.id", "\"$id\" appears more than once")
            val count = obj.reqInt(path, "count", -1_000_000..1_000_000)
            if (count == 0) bad("$path.count", "must not be zero")
            val kind = obj.optEnum<EventKind>(path, "kind") ?: EventKind.PRAYED
            if (kind == EventKind.PRAYED && count < 0) bad("$path.count", "must be positive")
            PrayerEvent(
                id = id,
                wakt = obj.reqEnum<Wakt>(path, "wakt"),
                count = count,
                date = obj.reqDate(path, "date"),
                kind = kind,
            )
        }
    }

    // ---------------------------------------------------------------- merging

    /**
     * "Merge": keeps the current setup and settings, and adds the incoming prayer events
     * whose id is not already there.
     */
    fun merge(current: AppData, incoming: AppData): MergeResult {
        val known = current.events.map { it.id }.toHashSet()
        val toAdd = incoming.events.filter { it.id !in known }
        return MergeResult(
            data = current.copy(events = current.events + toAdd),
            added = toAdd.size,
            skipped = incoming.events.size - toAdd.size,
        )
    }

    // ---------------------------------------------------------------- reading helpers

    private fun bad(path: String, why: String): Nothing = throw BackupFormatException("$path $why")

    private fun join(parent: String, key: String): String = if (parent.isEmpty()) key else "$parent.$key"

    private fun Any?.asObject(path: String): Map<String, Any?> {
        if (this !is Map<*, *>) bad(path, "must be an object")
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }

    private fun Map<String, Any?>.reqObject(parent: String, key: String): Map<String, Any?> =
        (this[key] ?: bad(join(parent, key), "is required")).asObject(join(parent, key))

    private fun Map<String, Any?>.optObject(parent: String, key: String): Map<String, Any?>? =
        this[key]?.asObject(join(parent, key))

    private fun Map<String, Any?>.reqString(parent: String, key: String): String {
        val v = this[key] ?: bad(join(parent, key), "is required")
        return v as? String ?: bad(join(parent, key), "must be text")
    }

    private fun Map<String, Any?>.reqBoolean(parent: String, key: String): Boolean {
        val v = this[key] ?: bad(join(parent, key), "is required")
        return v as? Boolean ?: bad(join(parent, key), "must be true or false")
    }

    private fun Map<String, Any?>.optBoolean(parent: String, key: String): Boolean? {
        val v = this[key] ?: return null
        return v as? Boolean ?: bad(join(parent, key), "must be true or false")
    }

    private fun Map<String, Any?>.reqInt(parent: String, key: String, range: IntRange): Int =
        optInt(parent, key, range) ?: bad(join(parent, key), "is required")

    private fun Map<String, Any?>.optInt(parent: String, key: String, range: IntRange): Int? {
        val v = this[key] ?: return null
        if (v !is Long) bad(join(parent, key), "must be a whole number")
        if (v < range.first.toLong() || v > range.last.toLong()) {
            bad(join(parent, key), "must be between ${range.first} and ${range.last}")
        }
        return v.toInt()
    }

    private fun parseDate(text: String, path: String): LocalDate {
        val date = try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            bad(path, "must be a date written as YYYY-MM-DD")
        }
        if (date.year !in 1900..2200) bad(path, "must be a year between 1900 and 2200")
        return date
    }

    private fun Map<String, Any?>.reqDate(parent: String, key: String): LocalDate =
        optDate(parent, key) ?: bad(join(parent, key), "is required")

    private fun Map<String, Any?>.optDate(parent: String, key: String): LocalDate? {
        val v = this[key] ?: return null
        val text = v as? String ?: bad(join(parent, key), "must be a date written as YYYY-MM-DD")
        return parseDate(text, join(parent, key))
    }

    private fun Map<String, Any?>.optTime(parent: String, key: String): LocalTime? {
        val v = this[key] ?: return null
        val text = v as? String ?: bad(join(parent, key), "must be a time written as HH:mm")
        return try {
            LocalTime.parse(text, TIME_FORMAT)
        } catch (e: DateTimeParseException) {
            bad(join(parent, key), "must be a time written as HH:mm")
        }
    }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.optEnum(parent: String, key: String): T? {
        val v = this[key] ?: return null
        val text = v as? String ?: bad(join(parent, key), "must be text")
        return enumValues<T>().firstOrNull { it.name.lowercase() == text }
            ?: bad(join(parent, key), "must be one of: ${enumValues<T>().joinToString(", ") { it.name.lowercase() }}")
    }

    private inline fun <reified T : Enum<T>> Map<String, Any?>.reqEnum(parent: String, key: String): T =
        optEnum<T>(parent, key) ?: bad(join(parent, key), "is required")
}
