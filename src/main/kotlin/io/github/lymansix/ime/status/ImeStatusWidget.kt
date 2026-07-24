package io.github.lymansix.ime.status

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import io.github.lymansix.ime.ImeBundle
import io.github.lymansix.ime.settings.ImeSettings

/**
 * Status bar widget showing IME state (中/英).
 *
 * Click the widget to toggle between Chinese and English mode.
 * Keyboard shortcut (Ctrl+Alt+\) also toggles.
 */
class ImeStatusWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val settings = ImeSettings.getInstance()
        return if (settings.isChineseMode) "中" else "英"
    }

    override fun getAlignment(): Float = 0.5f

    override fun getTooltipText(): String = ImeBundle.message("status.widget.tooltip")

    override fun getClickConsumer(): com.intellij.util.Consumer<java.awt.event.MouseEvent> {
        return com.intellij.util.Consumer {
            val settings = ImeSettings.getInstance()
            settings.isChineseMode = !settings.isChineseMode
            // Refresh this widget so the text updates immediately
            statusBar?.updateWidget(WIDGET_ID)
        }
    }

    companion object {
        const val WIDGET_ID = "ImeStatusWidget"
    }
}

/**
 * Factory for creating ImeStatusWidget instances.
 */
class ImeStatusWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ImeStatusWidget.WIDGET_ID

    override fun getDisplayName(): String = ImeBundle.message("widget.display.name")

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return ImeStatusWidget()
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * Action to toggle IME mode (Chinese/English).
 *
 * Triggered via keyboard shortcut (Ctrl+Alt+\) or from Find Action dialog.
 * The status bar widget click is a more direct alternative (see ImeStatusWidget.getClickConsumer).
 */
class ToggleImeModeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = ImeSettings.getInstance()
        settings.isChineseMode = !settings.isChineseMode

        // Update all status bar widgets in the project
        val project = e.project
        if (project != null) {
            val statusBar = com.intellij.openapi.wm.WindowManager.getInstance()
                .getStatusBar(project)
            statusBar?.updateWidget(ImeStatusWidget.WIDGET_ID)
        }
    }

    override fun update(e: AnActionEvent) {
        val settings = ImeSettings.getInstance()
        e.presentation.text = if (settings.isChineseMode)
            ImeBundle.message("action.SwitchToEnglish.text")
        else
            ImeBundle.message("action.SwitchToChinese.text")
        e.presentation.description = if (settings.isChineseMode)
            ImeBundle.message("action.CurrentMode.english")
        else
            ImeBundle.message("action.CurrentMode.chinese")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
