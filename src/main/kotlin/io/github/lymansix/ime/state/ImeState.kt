package io.github.lymansix.ime.state

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

class ImeState {
    val composing = StringBuilder()
    var startOffset: Int = -1

    fun start(editor: Editor) {
        if (startOffset == -1) {
            startOffset = editor.caretModel.offset
        }
    }

    fun reset() {
        composing.clear()
        startOffset = -1
    }

    companion object {
        private val KEY = Key.create<ImeState>("IME_STATE")

        fun get(editor: Editor): ImeState =
            editor.getUserData(KEY) ?: ImeState().also {
                editor.putUserData(KEY, it)
            }
    }
}
