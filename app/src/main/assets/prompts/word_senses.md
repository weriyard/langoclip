# Word senses

List ALL ways the phrase **"{phrase}"** can function grammatically, and what it means in each role.
Context hint — the phrase was encountered with this meaning: **"{context}"**.

The output MUST be JSON matching the schema.

## baseForm

First, determine the **canonical dictionary/lemma form** of the phrase:
- Verbs → infinitive (e.g. "drew on" → "draw on", "ran" → "run", "was built" → "build")
- Nouns → singular nominative (e.g. "children" → "child")
- Adjectives → base form (e.g. "better" → "good")
- Phrasal verbs → infinitive of the verb part (e.g. "gave up" → "give up")
- If already in base form, return it unchanged.

Put this in the `baseForm` field FIRST — the senses must correspond to this canonical form.

## Rules for senses

- Include every distinct grammatical role the phrase can take: NOUN, VERB, ADJECTIVE, ADVERB,
  IDIOM, PHRASAL_VERB, etc.
- If the phrase has multiple distinct meanings within the SAME part of speech, list each as a
  separate item.
- Include idiomatic / fixed-expression uses as IDIOM or PHRASAL_VERB.
- Start with the most common / core meaning.
- SKIP trivially identical meanings — each entry must add new information.
- Do NOT repeat the part of speech word in the meaning text.

## Fields per item

- **partOfSpeech** — NOUN, VERB, ADJECTIVE, ADVERB, PRONOUN, PREPOSITION, IDIOM, PHRASAL_VERB,
  or OTHER.
- **meaning** — concise meaning / translation in **{targetLanguage}** (typically 3–10 words).
- **example** — one short English sentence illustrating THIS specific sense.
- **exampleTranslation** — translation of `example` into **{targetLanguage}**.
