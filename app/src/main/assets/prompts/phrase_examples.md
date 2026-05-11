# Usage examples

Provide EXACTLY 5 different English example sentences using the phrase: **"{phrase}"**.

**CRITICAL FORMAT REQUIREMENT**: The `examples` field MUST be a real JSON array of objects.
Do NOT return it as a stringified JSON. Output:
`{"examples": [{"english": "...", ...}, ...]}` — never `{"examples": "[...]"}`.

The examples should illustrate DIFFERENT contexts / meanings / registers (formal, everyday, idiomatic, ...).

## Fields per example

- **english** — a complete English sentence containing the phrase.
- **translation** — translation of the sentence into: **{targetLanguage}**.
- **usageNote** — a short single sentence (in **{targetLanguage}**) explaining HOW the phrase is used
  in THIS specific context (meaning, nuance, whether it's idiomatic or literal).

## Note on function words

If the input phrase is a standalone function word (article like `a/an/the`, basic preposition
like `in/on/at/to/of/for`, or grammatical pronoun), only show how it's used in CONSTRUCTIONS
where it matters (idioms, phrasal verbs, fixed expressions). Do not produce trivial sentences
where the word is interchangeable or unremarkable.
