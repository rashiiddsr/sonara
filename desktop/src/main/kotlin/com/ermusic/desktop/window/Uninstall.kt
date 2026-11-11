/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.Os
import com.ermusic.desktop.auth.LinuxKeyring
import com.ermusic.desktop.auth.MacKeychain
import com.ermusic.desktop.data.Backup
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDate
import javax.swing.JOptionPane
import javax.swing.UIManager

/** What happens to the user's Sonara data when Sonara goes. */
enum class DataChoice { Keep, BackUpThenDelete, Delete }

/**
 * Removing Sonara, with the user's say over their data: kept for a later install, saved to a
 * backup first, or deleted. On Windows the installer asks through [fromInstaller]; a Mac and
 * Linux have no uninstaller of their own, so Settings offers it ([uninstallHere]).
 */
object Uninstall {
    const val ARGUMENT = "--uninstall"

    /** Everything Sonara keeps for this user: its data folder and the key its sign-in is sealed with. */
    fun deleteData(root: File = AppFiles.root) {
        if (Os.isMac) runCatching { MacKeychain.forget() }
        if (Os.isLinux) runCatching { LinuxKeyring.forget() }
        root.deleteRecursively()
    }

    /** A backup's suggested name. */
    fun backupName(): String = "Sonara-backup-${LocalDate.now()}.zip"

    /** Asks where to save a backup and writes it; false when the user stepped back or it failed. */
    fun backUpWithDialog(): Boolean {
        val dialog = FileDialog(null as Frame?, "Save a backup of your Sonara data", FileDialog.SAVE)
        dialog.directory = File(System.getProperty("user.home"), "Documents").takeIf(File::isDirectory)?.path ?: System.getProperty("user.home")
        dialog.file = backupName()
        dialog.isVisible = true
        val name = dialog.file ?: return false
        val target = File(dialog.directory, if (name.endsWith(".zip")) name else "$name.zip")
        return runCatching { Backup.write(target) }.isSuccess
    }

    /** Carries out [choice] for the data; false when a backup was asked for and didn't happen. */
    fun applyChoice(choice: DataChoice): Boolean =
        when (choice) {
            DataChoice.Keep -> true
            DataChoice.Delete -> {
                deleteData()
                true
            }
            DataChoice.BackUpThenDelete ->
                if (backUpWithDialog()) {
                    deleteData()
                    true
                } else {
                    false
                }
        }

    /**
     * Run by the Windows installer while it removes Sonara (never while it updates it). [answer]
     * ("keep" or "delete") answers without asking, for silent uninstalls; otherwise the user
     * chooses. Closing the question keeps the data: nothing is deleted without a clear yes.
     */
    fun fromInstaller(answer: String?) {
        // The program is going away: an entry opening it at login would only fail. With Windows'
        // own reg.exe: under the installer, JNA (which StartAtLogin uses) can't load.
        removeWindowsRunEntry()
        when (answer?.lowercase()) {
            "keep" -> return
            "delete" -> {
                deleteData()
                return
            }
        }
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val choice = ask()
        if (!applyChoice(choice)) {
            JOptionPane.showMessageDialog(null, "No backup was made, so your Sonara data was kept.", "Uninstall Sonara", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private fun removeWindowsRunEntry() {
        if (!Os.isWindows) return
        val name = System.getProperty("jpackage.app-path")?.let { File(it).nameWithoutExtension } ?: return
        runCatching {
            ProcessBuilder("reg.exe", "delete", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", name, "/f")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor()
        }
    }

    private fun ask(): DataChoice {
        val options = arrayOf("Keep my data", "Back up, then delete", "Delete everything")
        val picked =
            JOptionPane.showOptionDialog(
                null,
                "Keep your Sonara data?\n\n" +
                    "Your sign-in, settings, history, downloads and song cache can stay on this computer,\n" +
                    "so Sonara picks up where you left off if you install it again.",
                "Uninstall Sonara",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0],
            )
        return when (picked) {
            1 -> DataChoice.BackUpThenDelete
            2 -> DataChoice.Delete
            else -> DataChoice.Keep
        }
    }

    /** What uninstalling from inside the app did, to tell the user. */
    sealed interface Result {
        data object Removed : Result

        data class RemoveYourself(val how: String) : Result

        data object BackupSkipped : Result
    }

    /**
     * Settings' "Uninstall Sonara" on a Mac or Linux: the data as chosen, the login entry, then the
     * app itself: the .app to the Trash, the AppImage file, or the .deb through the system's
     * password prompt. Where the app can't remove itself, [Result.RemoveYourself] says how.
     */
    fun uninstallHere(choice: DataChoice): Result {
        if (!applyChoice(choice)) return Result.BackupSkipped
        StartAtLogin.set(false)
        val program = System.getProperty("jpackage.app-path")?.let(::File)
        return when {
            Os.isMac -> {
                val bundle = program?.parentFile?.parentFile?.parentFile?.takeIf { it.name.endsWith(".app") }
                val trashed = bundle != null && runCatching { Desktop.getDesktop().moveToTrash(bundle) }.getOrDefault(false)
                if (trashed) Result.Removed else Result.RemoveYourself("Drag Sonara from Applications to the Trash.")
            }
            Os.isLinux -> {
                val appImage = System.getenv("APPIMAGE")?.let(::File)
                when {
                    appImage != null -> if (appImage.delete()) Result.Removed else Result.RemoveYourself("Delete ${appImage.path}.")
                    removeDebPackage() -> Result.Removed
                    else -> Result.RemoveYourself("Run: sudo apt remove sonara")
                }
            }
            else -> Result.RemoveYourself("Open Settings → Apps, find Sonara and choose Uninstall.")
        }
    }

    /** The .deb, removed with the system's own password prompt (pkexec); false when that isn't there or was declined. */
    private fun removeDebPackage(): Boolean {
        val pkexec = listOf("/usr/bin/pkexec", "/bin/pkexec").firstOrNull { File(it).canExecute() } ?: return false
        val aptGet = listOf("/usr/bin/apt-get", "/bin/apt-get").firstOrNull { File(it).canExecute() } ?: return false
        return runCatching { ProcessBuilder(pkexec, aptGet, "remove", "-y", "sonara").inheritIO().start().waitFor() == 0 }.getOrDefault(false)
    }
}
