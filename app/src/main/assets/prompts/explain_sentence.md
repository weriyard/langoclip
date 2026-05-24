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

## Multi-word constructions: only when ADJACENT

Group several words into ONE item ONLY when they sit next to each other in the sentence
and form a tight fixed expression. Do NOT attempt to span a long stretch — splitting
short adjacent helpers from their head loses the construction, but spanning a whole
clause buries vocabulary you should be showing separately.

Group as ONE item when adjacent:
- **Semi-modals / quasi-modal verbs**: `have to`, `has to`, `had to`, `need to`,
  `ought to`, `used to`, `going to` (when modal-like), `had better`, `would rather`,
  `be able to`, `be allowed to`, `be supposed to`
- **Short fixed discourse phrases (2–3 words, adjacent)**: `by the way`, `of course`,
  `in fact`, `for example`, `as well`, `as long as`, `as soon as`, `as far as`,
  `as if`, `as though`, `no matter`, `at least`, `at most`, `in order to`
- **Reciprocal**: `each other`, `one another`
- **Pronoun + verb idioms (when figurative)**: `make it`, `take it`, `get it`,
  `lose it`, `mean it`

Do NOT group across distant words:
- `so ... that` → "so" is its own item, "that" is its own item (or skipped as
  conjunction). The correlative meaning belongs in the `explanation` of the part the
  learner asks about, not in the structure.
- `either ... or`, `neither ... nor`, `not only ... but also`, `the more ... the more`
  → same rule. Each piece stands alone; mention the partner in the explanation if
  useful.

For grouped items, set `partOfSpeech` to `IDIOM` (or `OTHER` if it doesn't fit any other
POS) and put the FUNCTION of the construction in the explanation.

## Prefer SHORTER items over comprehensive ones

When in doubt, split. The goal is to surface vocabulary and grammar the learner cares
about — each item should be a tight unit (1 word for vocab, 2–4 words at most for tenses
and idioms). If you'd need a 5+ word item to "keep it together", that's a sign you should
split it.

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

### Subject pronoun is part of the verbal construction

When the subject of a verbal construction is a **personal pronoun** (`I`, `you`, `he`,
`she`, `it`, `we`, `they`) or `this` / `that` used as subject, INCLUDE IT as the head of
the same item. The learner needs to see the whole "subject + tense" unit at once.

- `I have been waiting` → ONE item (Present Perfect Continuous of `wait`, with subject `I`)
- `She would have gone` → ONE item (III Conditional)
- `They were sailed` → ONE item (Past Simple Passive)
- `He is going to leave` → ONE item (Future `going to`)

If the subject is a noun phrase longer than one word (e.g. `My older brother`, `The man
in the corner`), leave it as a separate item and the verbal construction starts at the
auxiliary. Single proper-noun subjects (`John`, `Anna`) MAY be grouped if it keeps the
example tight.

The `translation` field of such an item must include the Polish subject + verb
translation as one natural phrase (e.g. `I have been waiting` → `Czekam (od jakiegoś czasu)`).

### Explanation format for verbal constructions

For every item that is a verbal construction, the `explanation` MUST contain:

1. The NAME of the tense / construction in English (e.g. `Past Simple Passive`,
   `Present Perfect Continuous`, `III Conditional`).
2. In parentheses: the COMPONENTS — auxiliary + main verb form, using the actual words from
   the sentence.

**Examples (write yours in {targetLanguage} for non-grammatical parts, but keep tense names in English):**

| Original           | partOfSpeech | explanation                                                            |
|--------------------|--------------|------------------------------------------------------------------------|
| `They were sailed`       | VERB         | `Past Simple Passive (were + Past Participle: sailed)` — bierne czynności w przeszłości |
| `She has been working`   | VERB         | `Present Perfect Continuous (have/has + been + verb-ing: working)` — czynność trwająca do teraz |
| `I have been waiting`    | VERB         | `Present Perfect Continuous (have + been + verb-ing: waiting)` — czynność trwająca od pewnego momentu do teraz |
| `We would have gone`     | VERB         | `III Conditional (would + have + Past Participle: gone)` — hipoteza nieosiągnięta |
| `It is going to leave`   | VERB         | `Future "going to" (be + going to + bezokolicznik: leave)` — bliski/planowany zamiar |
| `He must have been`      | VERB         | `Modal Perfect (must + have + Past Participle: been)` — silne wnioskowanie o przeszłości |
| `I have been`            | VERB         | `Present Perfect of "to be" (have + been)` — stan trwający do teraz |
| `had been`               | VERB         | `Past Perfect of "to be" (had + been)` — stan przed innym wydarzeniem w przeszłości |
| `You have to go`         | VERB         | `Semi-modal "have to" + bezokolicznik (go)` — obowiązek / konieczność |

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
