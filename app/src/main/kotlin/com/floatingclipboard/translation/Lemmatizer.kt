package com.floatingclipboard.translation

import com.floatingclipboard.data.LogStore
import com.floatingclipboard.data.lemma.LemmaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Lemmatizer(
    private val db: LemmaDatabase?,
    private val logs: LogStore? = null,
) {

    suspend fun lemmatize(input: String): String {
        val w = input.lowercase().trim()
        if (w.contains(' ')) return w   // multi-word: cache by original, LLM returns baseForm

        IRREGULAR_FORMS[w]?.let {
            logs?.d(TAG, "irregular '$w' → '$it'")
            return it
        }
        dbLookup(w)?.let {
            logs?.d(TAG, "en_lemmas HIT '$w' → '$it'")
            return it
        }
        val stem = applyHeuristics(w)
        logs?.d(TAG, "en_lemmas MISS '$w' → heuristic '$stem'")
        return stem
    }

    /**
     * Wrapped in [runCatching] because the bundled SQLite asset can fail Room's strict schema
     * validation on first open (e.g. NOT NULL constraint drift between the .db file and the
     * Kotlin entity). When that happens we fall through to the heuristic stemmer rather than
     * crashing the whole flow.
     */
    private suspend fun dbLookup(w: String): String? = db?.let {
        runCatching {
            withContext(Dispatchers.IO) { it.lemmaDao().lookup(w)?.lemma }
        }.getOrNull()
    }

    private fun applyHeuristics(w: String): String {
        val stem = when {
            w.endsWith("ying")                               -> w.dropLast(4) + "ie"
            w.endsWith("ing") && w.length > 6               -> w.dropLast(3)
            w.endsWith("ied")                                -> w.dropLast(3) + "y"
            w.endsWith("ed") && w.length > 5                -> w.dropLast(2)
            w.endsWith("ies") && w.length > 4               -> w.dropLast(3) + "y"
            w.endsWith("ves")                                -> w.dropLast(3) + "f"
            w.endsWith("s") && !w.endsWith("ss") && w.length > 3 -> w.dropLast(1)
            else                                             -> w
        }
        // Fix doubled consonants: running→runn→run, sitting→sitt→sit
        return if (stem.length > 2) {
            val last = stem.last()
            if (last == stem[stem.length - 2] && last in "bcdfgklmnprst") stem.dropLast(1)
            else stem
        } else stem
    }

    companion object {
        private const val TAG = "Lemma"

        val IRREGULAR_FORMS = mapOf(
            "was" to "be", "were" to "be", "been" to "be", "am" to "be", "is" to "be", "are" to "be",
            "had" to "have", "has" to "have",
            "did" to "do", "done" to "do",
            "went" to "go", "gone" to "go",
            "saw" to "see", "seen" to "see",
            "took" to "take", "taken" to "take",
            "gave" to "give", "given" to "give",
            "knew" to "know", "known" to "know",
            "thought" to "think",
            "bought" to "buy",
            "brought" to "bring",
            "taught" to "teach",
            "caught" to "catch",
            "fought" to "fight",
            "sought" to "seek",
            "found" to "find",
            "held" to "hold",
            "built" to "build",
            "sent" to "send",
            "spent" to "spend",
            "lost" to "lose",
            "met" to "meet",
            "left" to "leave",
            "understood" to "understand",
            "stood" to "stand",
            "sat" to "sit",
            "wrote" to "write", "written" to "write",
            "drove" to "drive", "driven" to "drive",
            "rode" to "ride", "ridden" to "ride",
            "flew" to "fly", "flown" to "fly",
            "grew" to "grow", "grown" to "grow",
            "threw" to "throw", "thrown" to "throw",
            "blew" to "blow", "blown" to "blow",
            "children" to "child",
            "men" to "man", "women" to "woman",
            "mice" to "mouse", "geese" to "goose",
            "feet" to "foot", "teeth" to "tooth",
            "leaves" to "leaf", "lives" to "life",
            "knives" to "knife", "wolves" to "wolf",
            "halves" to "half", "shelves" to "shelf",
            "better" to "good", "best" to "good",
            "worse" to "bad", "worst" to "bad",
            "more" to "many", "most" to "many",
            "less" to "little", "least" to "little",
            "further" to "far", "furthest" to "far",
            "elder" to "old", "eldest" to "old",
        )
    }
}
