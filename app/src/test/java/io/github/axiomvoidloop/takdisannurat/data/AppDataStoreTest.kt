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
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataStoreTest {

    private val man = AppData(
        Setup(Gender.MALE, SchoolChoice.MEMORY_FIRST, MissedFrom(2015), LocalDate.of(2026, 12, 20), true),
    )

    private fun withStore(test: (AppDataStore, File) -> Unit) {
        val dir = Files.createTempDirectory("takdis-test").toFile()
        try {
            val file = File(dir, "data.json")
            test(AppDataStore(file), file)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun startsEmpty() = withStore { store, _ ->
        assertEquals(LoadResult.Empty, store.load())
    }

    @Test
    fun savesAndLoadsAgain() = withStore { store, _ ->
        store.save(man)
        assertEquals(LoadResult.Loaded(man), store.load())
    }

    @Test
    fun theLatestSaveWins() = withStore { store, _ ->
        store.save(man)
        val more = man.withEvent(PrayerEvent("e1", Wakt.FAJR, 1, LocalDate.of(2027, 1, 1)))
        store.save(more)
        assertEquals(LoadResult.Loaded(more), store.load())
    }

    @Test
    fun keepsThePreviousVersionAsABackup() = withStore { store, file ->
        store.save(man)
        val more = man.withEvent(PrayerEvent("e1", Wakt.FAJR, 1, LocalDate.of(2027, 1, 1)))
        store.save(more)
        val backup = File(file.parentFile, "data.json.bak")
        assertTrue(backup.exists())
        assertEquals(man, (BackupCodec.decode(backup.readText()) as ImportResult.Success).data)
    }

    @Test
    fun leavesNoTemporaryFileBehind() = withStore { store, file ->
        store.save(man)
        store.save(man)
        assertFalse(File(file.parentFile, "data.json.tmp").exists())
    }

    @Test
    fun recoversFromADamagedMainFile() = withStore { store, file ->
        store.save(man)
        val more = man.withEvent(PrayerEvent("e1", Wakt.FAJR, 1, LocalDate.of(2027, 1, 1)))
        store.save(more)
        file.writeText("{ this is damaged")
        assertEquals(LoadResult.Loaded(man, recoveredFromBackup = true), store.load())
    }

    @Test
    fun reportsWhenEverythingIsDamaged() = withStore { store, file ->
        store.save(man)
        store.save(man)
        file.writeText("garbage")
        File(file.parentFile, "data.json.bak").writeText("more garbage")
        assertTrue(store.load() is LoadResult.Corrupt)
        // The damaged files are left as they are, so nothing is lost by looking.
        assertEquals("garbage", file.readText())
    }

    @Test
    fun deleteAllErasesEverything() = withStore { store, file ->
        store.save(man)
        store.save(man)
        store.deleteAll()
        assertFalse(file.exists())
        assertEquals(LoadResult.Empty, store.load())
    }

    @Test
    fun theSavedFileIsAValidBackup() = withStore { store, file ->
        store.save(man)
        val result = BackupCodec.decode(file.readText())
        assertTrue(result is ImportResult.Success)
    }
}
