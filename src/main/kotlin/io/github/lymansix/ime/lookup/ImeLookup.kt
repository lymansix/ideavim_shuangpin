package io.github.lymansix.ime.lookup
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import io.github.lymansix.ime.dict.FlyPyDict
import io.github.lymansix.ime.state.ImeState

object ImeLookup {

    fun show(editor: Editor, state: ImeState) {
        val project = editor.project ?: return
        val code = state.composing.toString()
        val list = FlyPyDict.getCandidates(code)
        if (list.isEmpty()) return

        val items = list.take(9).mapIndexed { i, candidate ->
            LookupElementBuilder.create(candidate.word)
                .withPresentableText("${i + 1}. ${candidate.word}")
        }.toTypedArray()

        ApplicationManager.getApplication().invokeLater {
            LookupManager.getActiveLookup(editor)?.hideLookup(true)
            LookupManager.getInstance(project).showLookup(editor, *items)
        }
    }

    fun hide(editor: Editor) {
        LookupManager.getActiveLookup(editor)?.hideLookup(true)
    }
}