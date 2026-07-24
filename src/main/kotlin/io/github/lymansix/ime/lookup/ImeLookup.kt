package io.github.lymansix.ime.lookup
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import io.github.lymansix.ime.dict.FlyPyDict
import io.github.lymansix.ime.state.ImeState
import java.util.concurrent.atomic.AtomicInteger

object ImeLookup {

    /**
     * Coalesces scheduled popup refreshes. Each show() bumps this and captures the new value;
     * the invokeLater callback runs only if its captured value still matches (i.e., no newer
     * show() or hide() has been scheduled since). Prevents the "two popups flash, then one
     * disappears" flicker that occurs when keystrokes arrive faster than the EDT drains the
     * invokeLater queue — without coalescing, each queued callback does hideLookup+showLookup,
     * so stale popups briefly render before being torn down by the next callback.
     */
    private val sequence = AtomicInteger(0)

    fun show(editor: Editor, state: ImeState) {
        val project = editor.project ?: return
        val code = state.composing.toString()
        val list = FlyPyDict.getCandidates(code)
        if (list.isEmpty()) return

        val items = list.take(9).mapIndexed { i, candidate ->
            LookupElementBuilder.create(candidate.word)
                .withPresentableText("${i + 1}. ${candidate.word}")
        }.toTypedArray()

        val mySeq = sequence.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (sequence.get() != mySeq) return@invokeLater
            LookupManager.getActiveLookup(editor)?.hideLookup(true)
            LookupManager.getInstance(project).showLookup(editor, *items)
        }
    }

    fun hide(editor: Editor) {
        // Invalidate any pending show() so it doesn't resurrect the lookup after we hide it.
        sequence.incrementAndGet()
        LookupManager.getActiveLookup(editor)?.hideLookup(true)
    }
}
