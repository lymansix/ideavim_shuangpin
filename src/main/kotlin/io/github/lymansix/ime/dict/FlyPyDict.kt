package io.github.lymansix.ime.dict

import java.io.InputStreamReader

/**
 * FlyPy (小鹤) dictionary loader and matcher.
 *
 * Loads the dictionary from `src/main/resources/dict/fly.txt` at first access.
 * File format: one line per code, whitespace-separated:
 *     <code>   <word1> [<word2>]
 * The code column is space-padded to a fixed width; multiple words sharing the
 * same code are separated by a single space on the same line (up to 2 words).
 * Codes are 1-4 lowercase letters (Xiaohe Yin-xing / 小鹤音形 encoding).
 *
 * Lookup: prefix-match against all entries whose code starts with the same first letter.
 * With ~48k lines (~49k entries after expanding multi-word lines), filtering
 * 2-4k entries per letter is fast enough for interactive use.
 */
object FlyPyDict {

    private data class DictEntry(val word: String, val code: String)

    /** Entries grouped by the first letter of their code (for fast first-level lookup). */
    private val entriesByFirstLetter: Map<Char, List<DictEntry>> by lazy {
        loadDict().groupBy { it.code[0] }
    }

    /**
     * Get candidate words for the given input code.
     *
     * @param code the typed input (1-4 lowercase letters)
     * @return candidates whose code starts with [code], in the dict's original order
     */
    fun getCandidates(code: String): List<Candidate> {
        if (code.isEmpty()) return emptyList()
        val firstLetter = code[0]
        val entries = entriesByFirstLetter[firstLetter] ?: return emptyList()

        return entries
            .asSequence()
            .filter { it.code.startsWith(code) }
            .take(MAX_CANDIDATES)
            .map { Candidate(it.word, it.code, computeWeight(it.code, code)) }
            .toList()
    }

    /**
     * Weight heuristic:
     *   - Exact code match → 1000 (highest priority)
     *   - Code length close to input → higher weight (prefer tighter matches)
     *   - Shorter codes rank higher when input is short (for snappier single-char input)
     */
    private fun computeWeight(entryCode: String, inputCode: String): Int {
        if (entryCode == inputCode) return 1000
        val lenDiff = entryCode.length - inputCode.length
        return (500 - lenDiff * 50).coerceAtLeast(1)
    }

    /** Precompiled whitespace splitter — used once per line during dict load. */
    private val WS = Regex("\\s+")

    /**
     * Load the dictionary from the bundled resource file.
     *
     * Each non-empty line yields one DictEntry per word on that line (all sharing
     * the same code). A line like "anqi    按期 暗器" produces:
     *     DictEntry("按期", "anqi"), DictEntry("暗器", "anqi")
     */
    private fun loadDict(): List<DictEntry> {
        val stream = FlyPyDict::class.java.getResourceAsStream("/dict/fly.txt")
            ?: return emptyList()
        return InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            reader.readLines()
                .asSequence()
                .filter { it.isNotEmpty() }
                .flatMap { line ->
                    val tokens = line.trim().split(WS)
                    if (tokens.size < 2 || tokens[0].isEmpty()) return@flatMap emptyList()
                    val code = tokens[0]
                    tokens.drop(1).map { word -> DictEntry(word, code) }
                }
                .toList()
        }
    }

    /** Maximum candidates to return per lookup. */
    private const val MAX_CANDIDATES = 50
}
