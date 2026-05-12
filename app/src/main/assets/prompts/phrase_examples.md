# Usage examples

Provide EXACTLY 5 different English example sentences using the phrase: **"{phrase}"**.

**COUNT IS MANDATORY**: The `examples` array MUST contain exactly 5 objects — no more, no fewer.
Do NOT stop after 1, 2, or 3 examples. Write all 5 before closing the array.

**CRITICAL FORMAT REQUIREMENT**: The `examples` field MUST be a real JSON array of objects.
Do NOT return it as a stringified JSON. Output:
`{"examples": [{"english": "...", ...}, ...]}` — never `{"examples": "[...]"}`.

The examples MUST illustrate DIFFERENT contexts, registers, and tenses. Specifically:
- Use at least **3 different tenses** across the 5 sentences (e.g. present simple, past simple, present perfect, future, conditional).
- Cover at least **2 different registers**: one formal/academic and one informal/conversational.
- Vary the **sentence structure**: simple, compound, and complex sentences.
- Vary the **position** of the phrase: beginning, middle, and end of sentence.
- Each sentence should feel like it comes from a **completely different situation or topic**.

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
