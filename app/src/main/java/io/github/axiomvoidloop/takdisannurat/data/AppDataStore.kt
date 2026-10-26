package io.github.axiomvoidloop.takdisannurat.data

/*
 * Takdis an-Nurat
 * Copyright (C) 2026 Axiom Voidloop
 * Licensed under the GNU General Public License v3.0 or later. See the LICENSE file.
 */

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

sealed interface LoadResult {
    /** Nothing has been saved yet (first launch). */
    data object Empty : LoadResult

    /** [recoveredFromBackup] is true when the main file was damaged and the previous copy was used. */
    data class Loaded(val data: AppData, val recoveredFromBackup: Boolean = false) : LoadResult

    /** Both the main file and its backup copy are damaged. The files are left untouched. */
    data class Corrupt(val reason: String) : LoadResult
}

/**
 * Keeps [AppData] in one private JSON file, in the same format as the backup.
 *
 * Saving is safe against crashes and power loss: the new data is written to a temporary file first,
 * the previous file is kept as a ".bak" copy, and only then does the new file take its place.
 */
class AppDataStore(
    private val file: File,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val backupFile = File(file.parentFile, file.name + ".bak")
    private val tempFile = File(file.parentFile, file.name + ".tmp")

    private sealed interface Read {
        data object Missing : Read
        data class Ok(val data: AppData) : Read
        data class Bad(val reason: String) : Read
    }

    @Synchronized
    fun load(): LoadResult {
        val main = read(file)
        if (main is Read.Ok) return LoadResult.Loaded(main.data)
        val backup = read(backupFile)
        if (backup is Read.Ok) return LoadResult.Loaded(backup.data, recoveredFromBackup = true)
        if (main is Read.Missing && backup is Read.Missing) return LoadResult.Empty
        val reason = (main as? Read.Bad)?.reason ?: (backup as? Read.Bad)?.reason ?: "unknown problem"
        return LoadResult.Corrupt(reason)
    }

    @Synchronized
    fun save(data: AppData) {
        val text = BackupCodec.encode(data, clock())
        file.parentFile?.mkdirs()
        FileOutputStream(tempFile).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (file.exists()) {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(
                tempFile.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Erases everything the app has saved (used for "reset"). */
    @Synchronized
    fun deleteAll() {
        file.delete()
        backupFile.delete()
        tempFile.delete()
    }

    private fun read(f: File): Read {
        if (!f.exists()) return Read.Missing
        return try {
            when (val result = BackupCodec.decode(f.readText(Charsets.UTF_8))) {
                is ImportResult.Success -> Read.Ok(result.data)
                is ImportResult.Failure -> Read.Bad(result.message)
            }
        } catch (e: java.io.IOException) {
            Read.Bad("could not read ${f.name}: ${e.message}")
        }
    }
}
