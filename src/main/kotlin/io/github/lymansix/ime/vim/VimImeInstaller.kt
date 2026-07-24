@file:Suppress("DEPRECATION")

package io.github.lymansix.ime.vim

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.listener.VimInsertListener
import io.github.lymansix.ime.settings.ImeSettings
import io.github.lymansix.ime.status.ImeStatusWidget
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires Vim mode transitions to IME mode switches. Loaded only when IdeaVim is
 * installed (the parent `vim-integration.xml` is declared as an optional `<depends>`
 * in plugin.xml, so the platform skips this class entirely when IdeaVim is absent).
 *
 * Behavior (gated by [ImeSettings.enableSmartSwitch]):
 *   - Leaving insert mode (Esc etc.) → store current `isChineseMode`, switch to English.
 *   - Entering insert mode           → restore the stored `isChineseMode`.
 *
 * `storedChineseMode` is held globally (not per-editor) because [ImeSettings.isChineseMode]
 * is itself an app-level setting — there is no per-editor Chinese/English state to preserve.
 *
 * Implements [Disposable] so the message bus subscription and the Vim insert listener
 * are cleaned up on plugin unload / dynamic reload — without this, each reload would
 * stack another copy of both listeners. The parent disposable is the application, so
 * cleanup happens automatically on IDE shutdown too.
 *
 * Note: uses [VimInsertListener] / [VimPlugin.getChange().addInsertListener][com.maddyhome.idea.vim.VimPlugin.getChange]
 * which are deprecated in newer IdeaVim versions in favor of `ModeChangeListener` /
 * `listenersNotifier`. Suppressing the warnings here — migration to the new API can be
 * done as a follow-up once we pin a specific IdeaVim version target.
 */
class VimImeInstaller : AppLifecycleListener, Disposable {

    private val storedChineseMode = AtomicBoolean(true)

    /** The insert listener we register — kept as a field so we can unregister on dispose. */
    private val insertListener = object : VimInsertListener {
        override fun insertModeStarted(editor: Editor) {
            if (!ImeSettings.getInstance().enableSmartSwitch) return
            applyMode(chinese = storedChineseMode.get())
        }
    }

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        // Tie this instance's lifetime to the application so [dispose] runs on IDE
        // shutdown, and register it so dynamic plugin unload also triggers cleanup.
        Disposer.register(ApplicationManager.getApplication() as Disposable, this)

        // (a) Detect exit-insert by matching the command name. The connection is
        //     bound to `this` so it's auto-disconnected when [dispose] runs.
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        messageBusConnection?.subscribe(
            CommandListener.TOPIC,
            object : CommandListener {
                override fun beforeCommandFinished(event: CommandEvent) {
                    if (!ImeSettings.getInstance().enableSmartSwitch) return
                    val name = event.commandName ?: return
                    if (name in INSERT_EXIT_COMMAND_NAMES) {
                        storedChineseMode.set(ImeSettings.getInstance().isChineseMode)
                        applyMode(chinese = false)
                    }
                }
            }
        )

        // (b) Detect enter-insert via IdeaVim's own listener API.
        VimPlugin.getChange().addInsertListener(insertListener)
    }

    private var messageBusConnection: com.intellij.util.messages.MessageBusConnection? = null

    override fun dispose() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        // Remove the insert listener we added, so reloads don't stack.
        runCatching { VimPlugin.getChange().removeInsertListener(insertListener) }
    }

    /**
     * Set `isChineseMode` to [chinese] and refresh the status bar widget across every
     * open project so the 中/英 label updates immediately rather than waiting for the
     * next focus change or widget poll.
     */
    private fun applyMode(chinese: Boolean) {
        val settings = ImeSettings.getInstance()
        if (settings.isChineseMode == chinese) return
        settings.isChineseMode = chinese
        for (project in ProjectManager.getInstance().openProjects) {
            WindowManager.getInstance().getStatusBar(project)
                ?.updateWidget(ImeStatusWidget.WIDGET_ID)
        }
    }

    private companion object {
        // Command names IdeaVim fires when leaving insert mode. Sourced from the
        // reference plugin (io.github.hadixlin.iss.InputMethodAutoSwitcher).
        val INSERT_EXIT_COMMAND_NAMES = setOf("Escape", "Esc", "VimInsertExitModeAction")
    }
}
