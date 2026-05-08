# Rozbiór zdania

Rozbij zdanie użytkownika na znaczące części. Wynik MUSI być JSON-em zgodnym ze schematem.

## Zasady tworzenia listy `items`

- POMIŃ całkowicie spójniki (and, but, or, że, ale, więc, oraz, ...) i znaki interpunkcyjne (przecinki, kropki, dwukropki, średniki, cudzysłowy) — nie dodawaj ich jako oddzielnych itemów.
- Idiomy i phrasal verbs traktuj jako JEDNĄ jednostkę (np. "look forward to", "give up").
- Zachowaj kolejność występowania w oryginalnym zdaniu.

## Pola każdego itemu

- **original** — oryginalna fraza z tekstu użytkownika.
- **translation** — jej tłumaczenie na język: **{targetLanguage}**.
- **partOfSpeech** — jeden z: `NOUN`, `VERB`, `ADJECTIVE`, `ADVERB`, `PRONOUN`, `PREPOSITION`, `IDIOM`, `PHRASAL_VERB`, `OTHER`.
- **explanation** — BARDZO krótko (max 1 zdanie, zwykle pół zdania).
  - NIE powtarzaj nazwy części mowy ("rzeczownik", "czasownik", "przymiotnik" itp.) — jest już oznaczona w `partOfSpeech`.
  - Skup się tylko na FUNKCJI / ROLI lub niuansie gramatycznym.
  - Pisz w języku: **{targetLanguage}**.

### Przykłady

DOBRE:
- "podmiot zdania"
- "opisuje rodzaj opieki"
- "Present Perfect Continuous — czynność trwająca do teraz"
- "wskazuje cel"

ZŁE (tak NIE rób):
- "Złożony przymiotnik opisujący rodzaj opieki"
- "Rzeczownik oznaczający..."
