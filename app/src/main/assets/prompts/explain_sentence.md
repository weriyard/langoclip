# Sentence breakdown

Break the user's sentence into meaningful parts. The output MUST be JSON matching the schema.

## Rules for building the `items` list

- SKIP entirely:
  - conjunctions (and, but, or, że, ale, więc, oraz, ...)
  - punctuation (commas, periods, colons, semicolons, quotes)
  - **standalone function words**: articles (a, an, the), basic prepositions on their own
    (in, on, at, of, to, for, with, from, by), pronouns used purely grammatically
    (it, this, that as fillers).
  - Do NOT add these as separate items.
- EXCEPTION — DO include the word if it is part of a meaningful construction:
  - phrasal verbs (`look up`, `give up`, `take off`) — include as ONE phrase
  - idioms (`in time`, `at all costs`, `for good`) — include as ONE phrase
  - emphatic / stylistic uses (`THE one and only`, `a real disaster`) — include with explanation
- Keep idioms and phrasal verbs as a single unit; never split them.
- Preserve the order of appearance from the original sentence.

## Fields per item

- **original** — the exact phrase from the user's text.
- **translation** — its translation into: **{targetLanguage}**.
- **partOfSpeech** — one of: `NOUN`, `VERB`, `ADJECTIVE`, `ADVERB`, `PRONOUN`, `PREPOSITION`, `IDIOM`, `PHRASAL_VERB`, `OTHER`.
- **explanation** — VERY brief (max 1 sentence, usually half a sentence). Write in: **{targetLanguage}**.
  - Do NOT repeat the part of speech name ("noun", "verb", "adjective", or their target-language equivalents) — it's already encoded in `partOfSpeech`.
  - Focus only on FUNCTION/ROLE in the sentence, or a grammatical nuance.

### Examples

GOOD explanations (written here in Polish — write yours in {targetLanguage}):
- "podmiot zdania"
- "opisuje rodzaj opieki"
- "Present Perfect Continuous — czynność trwająca do teraz"
- "wskazuje cel"

BAD (do NOT write like this):
- "Złożony przymiotnik opisujący rodzaj opieki" (repeats "przymiotnik")
- "Rzeczownik oznaczający..." (repeats "rzeczownik")
