package io.github.lymansix.ime.dict

/**
 * ASCII → Chinese punctuation mapping for Chinese-mode input.
 *
 * When the user types a punctuation key in Chinese mode (and the plugin isn't
 * currently consuming letters for composing), we substitute the ASCII char
 * with its Chinese/fullwidth equivalent before passing it through to the editor.
 *
 * This covers the common halfwidth → fullwidth and ASCII → CJK punctuation
 * substitutions that users expect from a Chinese IME. Quote pairing (opening
 * vs closing) is not tracked — first cut maps both to the closing form. Can
 * be extended later if needed.
 *
 * Reference: most of these follow the GB 2312 / Unicode fullwidth block or
 * the CJK Symbols and Punctuation block. The backslash → 顿号 mapping is
 * specific to Xiaohe / most Pinyin IMEs.
 */
object Punctuation {

    private val mapping: Map<Char, Char> = mapOf(
        ','  to '，',    // fullwidth comma
        '.'  to '。',    // ideographic full stop
        ';'  to '；',    // fullwidth semicolon
        ':'  to '：',    // fullwidth colon
        '?'  to '？',    // fullwidth question mark
        '!'  to '！',    // fullwidth exclamation mark
        '('  to '（',    // fullwidth left parenthesis
        ')'  to '）',    // fullwidth right parenthesis
        '['  to '【',    // left black lenticular bracket
        ']'  to '】',    // right black lenticular bracket
        '<'  to '《',    // left double angle bracket (book title mark)
        '>'  to '》',    // right double angle bracket
        '\\' to '、',    // ideographic comma (顿号 — common in enumeration lists)
        '/'  to '／',    // fullwidth slash
        '$'  to '￥',    // RMB / yuan sign
        '~'  to '～',    // fullwidth tilde
    )

    /**
     * @return the Chinese/fullwidth equivalent of [c] if one is defined, else `null`.
     */
    fun toChinese(c: Char): Char? = mapping[c]
}
