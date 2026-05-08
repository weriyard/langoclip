package com.floatingclipboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.floatingclipboard.actions.PartOfSpeech

/**
 * Stała mapa kolor per część mowy. Tony Material 500/700 — czytelne i na light, i na dark theme.
 * Każda część zdania o tej samej części mowy będzie miała ten sam kolor w całym wyniku.
 */
@Composable
fun colorForPartOfSpeech(pos: PartOfSpeech): Color = when (pos) {
    PartOfSpeech.NOUN -> Color(0xFF2196F3)         // blue 500
    PartOfSpeech.VERB -> Color(0xFF4CAF50)         // green 500
    PartOfSpeech.ADJECTIVE -> Color(0xFFFF9800)    // orange 500
    PartOfSpeech.ADVERB -> Color(0xFF9C27B0)       // purple 500
    PartOfSpeech.PRONOUN -> Color(0xFF009688)      // teal 500
    PartOfSpeech.PREPOSITION -> Color(0xFF607D8B)  // blue grey 500
    PartOfSpeech.IDIOM -> Color(0xFFFFA000)        // amber 700
    PartOfSpeech.PHRASAL_VERB -> Color(0xFFE91E63) // pink 500
    PartOfSpeech.OTHER -> Color(0xFF757575)        // grey 600
}

val PartOfSpeech.label: String
    get() = when (this) {
        PartOfSpeech.NOUN -> "rzeczownik"
        PartOfSpeech.VERB -> "czasownik"
        PartOfSpeech.ADJECTIVE -> "przymiotnik"
        PartOfSpeech.ADVERB -> "przysłówek"
        PartOfSpeech.PRONOUN -> "zaimek"
        PartOfSpeech.PREPOSITION -> "przyimek"
        PartOfSpeech.IDIOM -> "idiom"
        PartOfSpeech.PHRASAL_VERB -> "phrasal verb"
        PartOfSpeech.OTHER -> "—"
    }
