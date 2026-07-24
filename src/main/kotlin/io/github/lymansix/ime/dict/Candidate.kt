package io.github.lymansix.ime.dict

/**
 * A single candidate word/phrase returned by the dictionary.
 */
data class Candidate(
    val word: String,
    val code: String
)
