#!/usr/bin/env python3
"""
Wyciąga przykładowe zdania z kaikki.org (Wiktionary EN) do SQLite, żeby
DictionaryClient miał czym uzupełnić sensy bez `example` z dictionaryapi.dev.

Tylko `type == "example"` (czyste, krótkie, kurowane) — pomijamy `quotation`
(długie cytaty literackie, częściowo archaiczne).

Wynik:  en_examples.db  → skopiuj do app/src/main/assets/

Użycie:
    pip install tqdm
    python generate_examples.py
    cp en_examples.db ../app/src/main/assets/en_examples.db

Kaikki dostarczamy raz dla obu skryptów (generate_lemmas + generate_examples)
— oba czytają ten sam jsonl.
"""

import json
import sqlite3
import sys
from collections import defaultdict
from pathlib import Path
from tqdm import tqdm

INPUT_FILE  = "kaikki-en.jsonl"
OUTPUT_FILE = "en_examples.db"

# Filtry — dobrane tak, żeby zostawić zdania użyteczne w UI tłumaczenia (nie
# fragment, nie cały paragraf). Wartości można poluzować, ale waga bazy szybko
# rośnie.
MIN_LEN          = 30          # poniżej tego zdania są zazwyczaj fragmentami
MAX_LEN          = 180         # powyżej tego są zazwyczaj cytatami w przebraniu
MAX_PER_KEY      = 2           # ile przykładów trzymamy na (lemma, pos)
MAX_LEMMA_LEN    = 40          # multi-word frazówki tak, ale bez całych zdań

# Mapowanie POS — kaikki używa swojego zbioru, zachowujemy go 1:1 i tłumaczymy
# po stronie Kotlina (PartOfSpeech → kaikki tag). Zbiór nieoczywistych tagów
# (name, prefix, suffix, character, …) odrzucamy.
ALLOWED_POS = {
    "noun", "verb", "adj", "adv",
    "pron", "prep", "conj", "intj",
    "phrase", "proverb", "idiom",
}


def iter_examples(entry):
    """Zwraca pary (example_text, pos) z jednego entry kaikki."""
    pos = entry.get("pos", "").lower().strip()
    if pos not in ALLOWED_POS:
        return
    lemma = entry.get("word", "").lower().strip()
    if not lemma or len(lemma) > MAX_LEMMA_LEN:
        return
    for sense in entry.get("senses", []):
        for ex in sense.get("examples", []):
            if ex.get("type") != "example":
                continue
            text = (ex.get("text") or "").strip()
            if not (MIN_LEN <= len(text) <= MAX_LEN):
                continue
            # Pomijamy przykłady, w których lematu nie ma — bez kontekstu są bezużyteczne.
            if lemma not in text.lower():
                continue
            yield lemma, pos, text


def main():
    if not Path(INPUT_FILE).exists():
        print(f"Błąd: nie znaleziono {INPUT_FILE}")
        print(
            "Pobierz:\n"
            "  curl -L -o kaikki-en.jsonl.gz "
            "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz\n"
            "  gunzip kaikki-en.jsonl.gz"
        )
        sys.exit(1)

    # Zbieramy do słownika z capem MAX_PER_KEY na klucz — w pamięci, bo i tak
    # przepuszczamy całość przez jeden strumień.
    buckets: dict[tuple[str, str], list[str]] = defaultdict(list)

    print("Parsowanie JSONL (~3-5 min)...")
    with open(INPUT_FILE, encoding="utf-8") as f:
        for line in tqdm(f, unit=" wpisów"):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("lang_code", "en") != "en":
                continue
            for lemma, pos, text in iter_examples(entry):
                bucket = buckets[(lemma, pos)]
                if len(bucket) >= MAX_PER_KEY:
                    continue
                if text in bucket:
                    continue
                bucket.append(text)

    rows = [
        (lemma, pos, text)
        for (lemma, pos), texts in buckets.items()
        for text in texts
    ]
    print(f"\nZebrano {len(rows):,} przykładów dla {len(buckets):,} kluczy (lemma, pos)")

    print(f"Zapisywanie do {OUTPUT_FILE}...")
    Path(OUTPUT_FILE).unlink(missing_ok=True)
    conn = sqlite3.connect(OUTPUT_FILE)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS examples (
            lemma TEXT NOT NULL,
            pos   TEXT NOT NULL,
            text  TEXT NOT NULL
        )
    """)
    conn.executemany(
        "INSERT INTO examples (lemma, pos, text) VALUES (?, ?, ?)",
        rows,
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_examples_lookup ON examples(lemma, pos)")
    conn.commit()
    # Konsolidujemy WAL z głównym plikiem i wracamy do dziennika DELETE — bez tego
    # obok `.db` zostają sieroty `.db-shm` / `.db-wal`, które potem trafiałyby do APK.
    conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    conn.execute("PRAGMA journal_mode=DELETE")
    conn.commit()

    size_mb = Path(OUTPUT_FILE).stat().st_size / (1024 * 1024)
    count = conn.execute("SELECT COUNT(*) FROM examples").fetchone()[0]
    print(f"Gotowe: {OUTPUT_FILE} ({size_mb:.1f} MB, {count:,} wierszy)")

    print("\nSzybki test:")
    for word, pos in [
        ("free", "adj"), ("run", "verb"), ("dictionary", "noun"),
        ("look up", "phrase"), ("quickly", "adv"),
    ]:
        row = conn.execute(
            "SELECT text FROM examples WHERE lemma = ? AND pos = ? LIMIT 1",
            (word, pos),
        ).fetchone()
        print(f"  {word:14} {pos:6} → {row[0] if row else '(brak)'}")
    conn.close()

    print(f"\nNastępny krok:\n  cp {OUTPUT_FILE} ../app/src/main/assets/{OUTPUT_FILE}")


if __name__ == "__main__":
    main()
