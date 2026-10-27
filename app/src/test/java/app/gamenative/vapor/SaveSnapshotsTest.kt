package app.gamenative.vapor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSnapshotsTest {
    private fun container(): File {
        val root = Files.createTempDirectory("vapor").toFile()
        val user = SaveSnapshots.userDir(root)
        File(user, "Documents/My Games/Fallout3/Saves").mkdirs()
        File(user, "Documents/My Games/Fallout3/Saves/Save 1.fos").writeText("v1")
        File(user, "AppData/Local/Game/cache").mkdirs()
        File(user, "AppData/Local/Game/cache/big.bin").writeText("cache")
        File(user, "AppData/Local/Game/settings.ini").writeText("a=1")
        return root
    }

    @Test
    fun snapshotCopiesSavesAndSkipsCaches() {
        val root = container()
        val snap = SaveSnapshots.create(root, "Quick save", 1000L)
        assertNotNull(snap)
        assertEquals(2, snap!!.files)
        assertTrue(File(snap.dir, "Documents/My Games/Fallout3/Saves/Save 1.fos").isFile)
        assertFalse(File(snap.dir, "AppData/Local/Game/cache/big.bin").exists())
    }

    @Test
    fun restorePutsOldSaveBackAndKeepsNewerFiles() {
        val root = container()
        val save = File(SaveSnapshots.userDir(root), "Documents/My Games/Fallout3/Saves/Save 1.fos")
        val snap = SaveSnapshots.create(root, "Quick save", 1000L)!!
        save.writeText("v2")
        val later = File(save.parentFile, "Save 2.fos").apply { writeText("new") }
        assertTrue(SaveSnapshots.restore(root, snap))
        assertEquals("v1", save.readText())
        assertTrue(later.isFile)
        // the state before loading was backed up automatically
        assertTrue(SaveSnapshots.list(root).any { it.label == "Before loading" })
    }

    @Test
    fun pendingRestoreAppliesOnce() {
        val root = container()
        val save = File(SaveSnapshots.userDir(root), "Documents/My Games/Fallout3/Saves/Save 1.fos")
        val snap = SaveSnapshots.create(root, "Quick save", 1000L)!!
        save.writeText("v2")
        SaveSnapshots.requestRestoreOnNextLaunch(root, snap)
        assertNotNull(SaveSnapshots.applyPendingRestore(root))
        assertEquals("v1", save.readText())
        assertNull(SaveSnapshots.applyPendingRestore(root))
    }

    @Test
    fun listIsNewestFirstAndPruned() {
        val root = container()
        for (i in 1..(SaveSnapshots.MAX_SNAPSHOTS + 3)) SaveSnapshots.create(root, "Save $i", i * 1000L)
        val all = SaveSnapshots.list(root)
        assertEquals(SaveSnapshots.MAX_SNAPSHOTS, all.size)
        assertEquals("Save ${SaveSnapshots.MAX_SNAPSHOTS + 3}", all.first().label)
    }

    @Test
    fun emptyProfileMakesNoSnapshot() {
        val root = Files.createTempDirectory("vapor-empty").toFile()
        SaveSnapshots.userDir(root).mkdirs()
        assertNull(SaveSnapshots.create(root, "x"))
    }

    @Test
    fun profileFolderLinkedToContainerIsNotCopied() {
        val root = container()
        val docs = File(SaveSnapshots.userDir(root), "Saved Games")
        Files.createSymbolicLink(docs.toPath(), root.toPath())
        val snap = SaveSnapshots.create(root, "x", 5L)!!
        assertFalse(File(snap.dir, "Saved Games").exists())
    }

    @Test
    fun autoBackupSkipsUnchangedSavesAndKeepsThree() {
        val root = container()
        val save = File(SaveSnapshots.userDir(root), "Documents/My Games/Fallout3/Saves/Save 1.fos")
        save.setLastModified(1_000L)
        File(SaveSnapshots.userDir(root), "AppData/Local/Game/settings.ini").setLastModified(1_000L)
        assertNotNull(SaveSnapshots.autoBackup(root, 2_000L))
        assertNull(SaveSnapshots.autoBackup(root, 3_000L))
        for (t in 4..8) {
            save.setLastModified(t * 1_000L + 500L)
            assertNotNull(SaveSnapshots.autoBackup(root, t * 1_000L + 900L))
        }
        assertEquals(SaveSnapshots.MAX_AUTO, SaveSnapshots.list(root).count { it.label == SaveSnapshots.AUTO_LABEL })
    }
}
