package io.github.axiomvoidloop.takdisannurat.engine

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

enum class Gender { MALE, FEMALE }

/** What the user picked on the school screen. See SPEC.md section 1.2. */
enum class SchoolChoice { HANAFI_HANBALI, SHAFII_MALIKI, NOT_SURE, MEMORY_FIRST }

/** The prayers that can be owed, with their number of rakats (fard, and wajib for Witr). */
enum class Wakt(val rakats: Int) {
    FAJR(2),
    ZUHR(4),
    ASR(4),
    MAGHRIB(3),
    ISHA(4),
    WITR(3),
}

/** The rules that depend on the chosen school. */
data class SchoolRules(
    val haidMinDays: Int,
    val haidMaxDays: Int,
    /** True: no monthly haid deduction during pregnancy. */
    val pauseHaidInPregnancy: Boolean,
    val nifasMaxDays: Int,
)

/**
 * "Not sure" and "heart and memories first" apply no school's rules:
 * periods stop in pregnancy (biology), and the nifas input limit is the
 * longest limit found in any school.
 */
fun SchoolChoice.rules(): SchoolRules = when (this) {
    SchoolChoice.HANAFI_HANBALI -> SchoolRules(
        haidMinDays = 3, haidMaxDays = 10, pauseHaidInPregnancy = true, nifasMaxDays = 40,
    )
    SchoolChoice.SHAFII_MALIKI -> SchoolRules(
        haidMinDays = 1, haidMaxDays = 15, pauseHaidInPregnancy = false, nifasMaxDays = 60,
    )
    SchoolChoice.NOT_SURE, SchoolChoice.MEMORY_FIRST -> SchoolRules(
        haidMinDays = 1, haidMaxDays = 15, pauseHaidInPregnancy = true, nifasMaxDays = 60,
    )
}

/** Estimates used when the user cannot say exactly. Every one leans toward owing slightly more. */
object Defaults {
    const val PREGNANCY_DAYS = 280L
    const val PERIOD_RETURN_DAYS_NOT_BREASTFED = 84L
    const val PERIOD_RETURN_DAYS_BREASTFED = 180L
    const val PERIOD_RETURN_DAYS_AFTER_LOSS = 42L
}
