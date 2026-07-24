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
 * ## Lifecycle
 *
 * `AppLifecycleListener` implementations cannot also implement `Disposable` — the
 * platform manages those instances itself and explicitly rejects the combination
 * ("Listener implementation must not implement 'Disposable'"). So we keep the
 * cleanup resources in a separate internal [Disposable] ([cleanup]) and register
 * THAT with the application via [Disposer.register]. The result is equivalent:
 * on IDE shutdown or dynamic plugin reload, [cleanup] runs and tears down both
 * the message bus connection and the Vim insert listener.
 *
 * Note: uses [VimInsertListener] / [VimPlugin.getChange().addInsertListener][com.maddyhome.idea.vim.VimPlugin.getChange]
 * which are deprecated in newer IdeaVim versions in favor of `ModeChangeListener` /
 * `listenersNotifier`. Suppressing the warnings here — migration to the new API can be
 * done as a follow-up once we pin a specific IdeaVim version target.
 */
class VimImeInstaller : AppLifecycleListener {

    // Default matches ImeSettings.State.isChineseMode's default (English / false).
    // If the user enters insert mode without ever having left it (fresh start),
    // restoring to English is consistent with the plugin's default state.
    private val storedChineseMode = AtomicBoolean(false)

    /** The insert listener we register — kept as a field so we can unregister on dispose. */
    private val insertListener = object : VimInsertListener {
        override fun insertModeStarted(editor: Editor) {
            if (!ImeSettings.getInstance().enableSmartSwitch) return
            applyMode(chinese = storedChineseMode.get())
        }
    }

    private var messageBusConnection: com.intellij.util.messages.MessageBusConnection? = null

    /**
     * Internal [Disposable] that owns the cleanup work: disconnect the message bus
     * subscription and unregister the Vim insert listener. Registered with the
     * application in [appFrameCreated] so it fires on IDE shutdown and on dynamic
     * plugin reload (without which each reload would stack another copy of both
     * listeners).
     */
    private val cleanup = Disposable {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        runCatching { VimPlugin.getChange().removeInsertListener(insertListener) }
    }

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        // Tie cleanup's lifetime to the application. We can't make VimImeInstaller
        // itself Disposable (platform rejects it), but registering a child Disposable
        // achieves the same effect.
        Disposer.register(ApplicationManager.getApplication() as Disposable, cleanup)

        // (a) Detect exit-insert by matching the command name. The connection is
        //     bound to [cleanup] so it's auto-disconnected when cleanup runs.
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect(cleanup)
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
