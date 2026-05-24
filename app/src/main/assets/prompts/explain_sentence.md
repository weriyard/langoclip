# Sentence breakdown

Break the user's sentence into meaningful parts. The output MUST be JSON matching the schema.

The response object has TWO top-level fields:
1. **fullTranslation** — a single, natural-sounding translation of the WHOLE user sentence into
   **{targetLanguage}**. One sentence (or as many as the original has). Provide it FIRST.
2. **items** — array of meaningful parts (see rules below).

**CRITICAL FORMAT REQUIREMENT**: The `items` field MUST be a real JSON array of objects.
Do NOT return it as a stringified JSON. Do NOT wrap it in quotes. Output:
`{"fullTranslation": "...", "items": [{"original": "...", ...}, ...]}` — never `{"items": "[...]"}`.

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

## CRITICAL: Multi-word constructions built from short / function words as ONE item

When a short word (so, such, as, if, just, even, only, not, no, neither, either, both,
each, all, every…) or a pronoun forms an INTEGRAL semantic unit with surrounding words,
KEEP THEM TOGETHER as a single breakdown item. Splitting "so" from "that" or "either"
from "or" hides the construction the learner is meant to recognise.

Group as one item:
- **Correlative pairs**: `so ... that`, `such ... that`, `as ... as`, `not as ... as`,
  `either ... or`, `neither ... nor`, `not only ... but also`, `both ... and`,
  `the more ... the more`, `whether ... or`, `if ... then`
- **Semi-modals / quasi-modal verbs**: `have to`, `has to`, `had to`, `need to`,
  `ought to`, `used to`, `going to`, `had better`, `would rather`, `be able to`,
  `be allowed to`, `be supposed to`
- **Discourse markers / fixed expressions**: `by the way`, `of course`, `in fact`,
  `for example`, `as well as`, `as long as`, `as soon as`, `as far as`, `as if`,
  `as though`, `no matter what`, `at least`, `at most`, `in order to`
- **Reflexive / reciprocal**: `each other`, `one another`; reflexive `myself`/`himself`/…
  when paired with the verb they modify
- **Pronoun + verb idioms**: `make it`, `take it`, `get it` (figurative meanings),
  `do it`, `lose it`

For these, set `partOfSpeech` to `IDIOM` (or `OTHER` if it doesn't fit any other POS) and
write the explanation about the FUNCTION of the whole construction (e.g. for `so ... that`
→ "korelacja przyczynowo-skutkowa").

## CRITICAL: Verbal constructions (tenses, aspect, voice, modals) as ONE item

When a verb appears with auxiliary words that together form a specific grammatical
construction, treat the ENTIRE verbal group as a SINGLE item. This is the most important
educational signal — splitting them defeats the purpose of grammar learning.

Group as one item:
- **Tenses & perfect aspect**: `have gone`, `has been`, `had eaten`, `will have finished`
- **Continuous / progressive**: `is running`, `was building`, `am working`, `had been writing`
- **Passive voice**: `was sailed`, `is being made`, `have been seen`, `will be done`
- **Modal constructions**: `would have gone`, `should be doing`, `must have been finished`,
  `could have done`, `might be working`
- **Future forms**: `will leave`, `will be doing`, `is going to leave`, `was going to`
- **Conditionals**: `would do`, `would have done`, `if I were`
- **Past/Present habits**: `used to go`, `would always come` (habitual)
- **Negations of all of above**: `hasn't been`, `wouldn't have gone` → ONE item

Auxiliary verbs (`be`, `have`, `do`, modals) are NEVER skipped when part of such
constructions. A "standalone" auxiliary appears only as a linking verb / copula
(e.g. `She is tall` → `is` is the main verb here).

### Explanation format for verbal constructions

For every item that is a verbal construction, the `explanation` MUST contain:

1. The NAME of the tense / construction in English (e.g. `Past Simple Passive`,
   `Present Perfect Continuous`, `III Conditional`).
2. In parentheses: the COMPONENTS — auxiliary + main verb form, using the actual words from
   the sentence.

**Examples (write yours in {targetLanguage} for non-grammatical parts, but keep tense names in English):**

| Original           | partOfSpeech | explanation                                                            |
|--------------------|--------------|------------------------------------------------------------------------|
| `were sailed`      | VERB         | `Past Simple Passive (were + Past Participle: sailed)` — bierne czynności w przeszłości |
| `has been working` | VERB         | `Present Perfect Continuous (have/has + been + verb-ing: working)` — czynność trwająca do teraz |
| `would have gone`  | VERB         | `III Conditional (would + have + Past Participle: gone)` — hipoteza nieosiągnięta |
| `is going to leave`| VERB         | `Future "going to" (be + going to + bezokolicznik: leave)` — bliski/planowany zamiar |
| `must have been`   | VERB         | `Modal Perfect (must + have + Past Participle: been)` — silne wnioskowanie o przeszłości |
| `have been`        | VERB         | `Present Perfect of "to be" (have/has + been)` — stan/cecha trwająca do teraz |
| `had been`         | VERB         | `Past Perfect of "to be" (had + been)` — stan przed innym wydarzeniem w przeszłości |
| `have to go`       | VERB         | `Semi-modal "have to" + bezokolicznik (go)` — obowiązek / konieczność |

## Fields per item

- **original** — the exact phrase from the user's text (for verbal constructions: the full
  multi-word phrase, e.g. `were sailed`, not just `sailed`).
- **translation** — its translation into: **{targetLanguage}**.
- **partOfSpeech** — one of: `NOUN`, `VERB`, `ADJECTIVE`, `ADVERB`, `PRONOUN`, `PREPOSITION`,
  `IDIOM`, `PHRASAL_VERB`, `OTHER`.
- **explanation** — VERY brief (max 1 sentence, usually half a sentence). Write in: **{targetLanguage}**.
  - Do NOT repeat the part of speech name ("noun", "verb", "adjective", or their target-language
    equivalents) — it's already encoded in `partOfSpeech`.
  - For VERBAL CONSTRUCTIONS use the format described above (tense name + components +
    short note).
  - For other words focus only on FUNCTION/ROLE in the sentence.

### Examples for non-verbal items

GOOD explanations:
- "podmiot zdania"
- "opisuje rodzaj opieki"
- "wskazuje cel"

BAD (do NOT write like this):
- "Złożony przymiotnik opisujący rodzaj opieki" (repeats "przymiotnik")
- "Rzeczownik oznaczający..." (repeats "rzeczownik")
