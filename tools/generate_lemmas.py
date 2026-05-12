#!/usr/bin/env python3
"""
Generuje SQLite z mapowaniem forma_fleksyjna → lemat dla języka angielskiego.
Źródło: kaikki.org (Wiktionary EN sparsowany do JSONL)
Wynik:  en_lemmas.db (~10 MB) → skopiuj do app/src/main/assets/

Użycie:
    pip install tqdm
    curl -L -o kaikki-en.jsonl.gz https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl.gz
    gunzip kaikki-en.jsonl.gz
    python generate_lemmas.py
    cp en_lemmas.db ../app/src/main/assets/en_lemmas.db
"""

import json
import sqlite3
import sys
from pathlib import Path
from tqdm import tqdm

INPUT_FILE  = "kaikki-en.jsonl"
OUTPUT_FILE = "en_lemmas.db"

RELEVANT_TAGS = {
    "plural", "singular",
    "past", "present", "future",
    "participle", "gerund",
    "third-person",
    "comparative", "superlative",
    "inflection-template",
}


def should_include_form(form: dict) -> bool:
    tags = set(form.get("tags", []))
    if "alternative" in tags or "archaic" in tags:
        return False
    return bool(tags & RELEVANT_TAGS) or not tags


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

    conn = sqlite3.connect(OUTPUT_FILE)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS lemma_forms (
            surface TEXT PRIMARY KEY,
            lemma   TEXT NOT NULL
        )
    """)

    pairs: dict[str, str] = {}

    print("Parsowanie JSONL (~3-5 min)...")
    with open(INPUT_FILE, encoding="utf-8") as f:
        for line in tqdm(f, unit=" wpisów"):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue

            if entry.get("lang_code", "en") != "en":
                continue

            lemma = entry.get("word", "").lower().strip()
            if not lemma or len(lemma) > 80:
                continue

            for form in entry.get("forms", []):
                surface = form.get("form", "").lower().strip()
                if not surface or surface == lemma or len(surface) > 80:
                    continue
                if not should_include_form(form):
                    continue
                if surface not in pairs:
                    pairs[surface] = lemma

    print(f"\nZnaleziono {len(pairs):,} par forma→lemat")
    print("Zapisywanie do SQLite...")

    conn.executemany(
        "INSERT OR IGNORE INTO lemma_forms (surface, lemma) VALUES (?, ?)",
        pairs.items(),
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_surface ON lemma_forms(surface)")
    conn.commit()

    size_mb = Path(OUTPUT_FILE).stat().st_size / (1024 * 1024)
    count = conn.execute("SELECT COUNT(*) FROM lemma_forms").fetchone()[0]
    print(f"\nGotowe: {OUTPUT_FILE} ({size_mb:.1f} MB, {count:,} wpisów)")

    print("\nSzybki test:")
    for word in ["running", "went", "children", "better", "caught", "flies", "worse", "geese"]:
        result = conn.execute(
            "SELECT lemma FROM lemma_forms WHERE surface = ?", (word,)
        ).fetchone()
        print(f"  {word:12} → {result[0] if result else '(brak — sprawdź warstwę 1)'}")

    conn.close()
    print(f"\nNastępny krok:\n  cp {OUTPUT_FILE} ../app/src/main/assets/{OUTPUT_FILE}")


if __name__ == "__main__":
    main()
