package io.github.lymansix.ime.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent settings for the IME plugin.
 */
@State(
    name = "ImeSettings",
    storages = [Storage("fly-ime-settings.xml")]
)
@Service(Service.Level.APP)
class ImeSettings : PersistentStateComponent<ImeSettings.State> {

    data class State(
        /**
         * Default is `false` (English mode). Users flip to Chinese via the status bar
         * widget or `Ctrl+Alt+\`. The choice of English as default matches the
         * convention that most IDE editing is in English (code, commands, searches);
         * users who primarily type Chinese can flip once and stay there.
         *
         * Note: existing installing with a persisted `fly-ime-settings.xml` are not
         * affected — their saved value is loaded over this default.
         */
        var isChineseMode: Boolean = false,
        /**
         * When IdeaVim is installed, controls whether the IME auto-switches on Vim
         * mode transitions (exit-insert → English; enter-insert → restore previous
         * mode). Driven by [io.github.lymansix.ime.vim.VimImeInstaller]. When IdeaVim
         * is not installed, this field is persisted but has no runtime effect.
         */
        var enableSmartSwitch: Boolean = true
    )

    private var state = State()

    var isChineseMode: Boolean
        get() = state.isChineseMode
        set(value) {
            state.isChineseMode = value
        }

    var enableSmartSwitch: Boolean
        get() = state.enableSmartSwitch
        set(value) {
            state.enableSmartSwitch = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): ImeSettings {
            return ApplicationManager.getApplication().getService(ImeSettings::class.java)
        }
    }
}
