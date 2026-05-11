# Usage examples

Provide EXACTLY 5 different English example sentences using the phrase: **"{phrase}"**.

**CRITICAL FORMAT REQUIREMENT**: The `examples` field MUST be a real JSON array of objects.
Do NOT return it as a stringified JSON. Output:
`{"examples": [{"english": "...", ...}, ...]}` — never `{"examples": "[...]"}`.

The examples should illustrate DIFFERENT contexts / meanings / registers (formal, everyday, idiomatic, ...).

## Fields per example

- **english** — a complete English sentence containing the phrase.
- **highlightedSpan** — the EXACT substring of `english` that represents the phrase as it
  appears in THIS sentence. Match must be character-for-character (same case, same spacing).
  If the phrase appears in a different grammatical form (e.g. tense change, plural), use the
  ACTUAL form from the sentence: phrase "give up" → highlightedSpan "gave up" / "gives up" /
  "giving up" depending on context. Phrase "child" → highlightedSpan "children" if plural is used.
- **translation** — translation of the sentence into: **{targetLanguage}**.
- **usageNote** — a short single sentence (in **{targetLanguage}**) explaining HOW the phrase is used
  in THIS specific context (meaning, nuance, whether it's idiomatic or literal).

## Note on function words

If the input phrase is a standalone function word (article like `a/an/the`, basic preposition
like `in/on/at/to/of/for`, or grammatical pronoun), only show how it's used in CONSTRUCTIONS
where it matters (idioms, phrasal verbs, fixed expressions). Do not produce trivial sentences
where the word is interchangeable or unremarkable.
