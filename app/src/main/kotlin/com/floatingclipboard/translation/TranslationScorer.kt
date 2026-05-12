package com.floatingclipboard.translation

/**
 * Two-layer translation quality scorer.
 *
 * Layer 1 — free heuristics, always run.
 * Layer 2 — EmbeddingGemma cosine similarity (when model is available).
 *
 * Heuristics can produce negative values (sum of penalties). Before combining
 * with embedding, clamp to [0, 1]. A negative raw heuristic score short-circuits
 * to finalScore = 0f → routes directly to Sonnet.
 */
object TranslationScorer {

    // Calibration constants — tune these after collecting real-world data.
    const val THRESHOLD_LOCAL_OK = 0.75f
    const val THRESHOLD_HAIKU_JUDGE_LOW = 0.55f
    const val THRESHOLD_HAIKU_DIRECT = 0.40f
    const val HAIKU_JUDGE_ACCEPT = 0.7f

    fun score(source: String, translation: String, embeddingScorer: EmbeddingScorer? = null): Float {
        val heuristic = heuristicScore(source, translation)
        if (heuristic < 0f) return 0f   // clearly bad — skip embedding, go to Sonnet

        val hClamped = heuristic.coerceIn(0f, 1f)
        return if (embeddingScorer != null) {
            val embedding = embeddingScorer.cosineSimilarity(source, translation)
            hClamped * 0.3f + embedding * 0.7f
        } else {
            hClamped
        }
    }

    private fun heuristicScore(source: String, translation: String): Float {
        if (translation.isBlank()) return 0f

        var score = 0f

        // Too short
        if (translation.length < source.length * 0.3f) score -= 0.3f

        // Source words (len > 3) present in translation — likely untranslated
        val sourceWords = source.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
        if (sourceWords.isNotEmpty()) {
            val translationLower = translation.lowercase()
            val untranslatedCount = sourceWords.count { it in translationLower }
            score -= 0.5f * (untranslatedCount.toFloat() / sourceWords.size)
        }

        // Polish diacritics present → likely real Polish text
        if (translation.any { it in "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ" }) score += 0.1f

        // Suspiciously long
        if (source.isNotEmpty() && translation.length > source.length * 8) score -= 0.2f

        return score
    }

    fun decide(score: Float): RoutingDecision = when {
        score >= THRESHOLD_LOCAL_OK          -> RoutingDecision.USE_LOCAL
        score >= THRESHOLD_HAIKU_JUDGE_LOW   -> RoutingDecision.HAIKU_JUDGE
        score >= THRESHOLD_HAIKU_DIRECT      -> RoutingDecision.HAIKU_DIRECT
        else                                 -> RoutingDecision.SONNET
    }
}

enum class RoutingDecision { USE_LOCAL, HAIKU_JUDGE, HAIKU_DIRECT, SONNET }

/** Pluggable embedding scorer — implement with EmbeddingGemma when model is available. */
interface EmbeddingScorer {
    fun cosineSimilarity(textA: String, textB: String): Float
}
