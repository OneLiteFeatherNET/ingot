#!/usr/bin/env python3
"""PreToolUse-Hook: blockiert Commits, PRs und Releases mit Werkzeug-Signaturen.

Prüft Bash-Befehle, die Text veröffentlichen (git commit/tag, gh pr|issue|release
create|edit) gegen die Commit-Konventionen aus CLAUDE.md. Exit 2 blockiert den
Tool-Call und gibt stderr an das Modell zurück.

Grenze: Text, der über stdin übergeben wird (z.B. `git commit -F -`), ist für den
Hook nicht sichtbar und wird nicht geprüft.
"""

import json
import re
import shlex
import sys
from pathlib import Path

# Befehle, deren Inhalt öffentlich sichtbar wird
RELEVANT = re.compile(
    r"\bgit\s+(commit|tag)\b|\bgh\s+(pr|issue|release)\s+(create|edit)\b"
)

# (Muster, Klartext-Begründung)
VERBOTEN = [
    (re.compile(r"Co-Authored-By:\s*.*(Claude|Anthropic)", re.I),
     "Co-Authored-By-Trailer mit Claude/Anthropic"),
    (re.compile(r"Claude-Session:", re.I),
     "Claude-Session-Trailer"),
    (re.compile(r"claude\.ai/code", re.I),
     "Link auf claude.ai/code"),
    (re.compile(r"Generated with.{0,30}Claude", re.I),
     "'Generated with Claude Code'-Hinweis"),
    (re.compile(r"\N{ROBOT FACE}"),
     "Roboter-Emoji"),
    (re.compile(r"—"),
     "Em-Dash (U+2014). Nutze Bindestrich, Doppelpunkt, Komma oder zwei Sätze."),
    (re.compile(r"–"),
     "En-Dash (U+2013). Nutze einen normalen Bindestrich."),
]

# Optionen, deren Wert eine Datei mit zu prüfendem Text ist
DATEI_OPTIONEN = {"-F", "--file", "--body-file", "--notes-file"}


def text_aus_dateien(befehl: str) -> str:
    """Liest Dateien ein, die per -F/--body-file übergeben wurden."""
    try:
        tokens = shlex.split(befehl)
    except ValueError:
        return ""

    inhalt = []
    for i, token in enumerate(tokens):
        pfad = None
        if token in DATEI_OPTIONEN and i + 1 < len(tokens):
            pfad = tokens[i + 1]
        elif "=" in token and token.split("=", 1)[0] in DATEI_OPTIONEN:
            pfad = token.split("=", 1)[1]

        if pfad and pfad != "-":
            try:
                inhalt.append(Path(pfad).read_text(encoding="utf-8", errors="replace"))
            except OSError:
                pass
    return "\n".join(inhalt)


def main() -> int:
    try:
        daten = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError) as fehler:
        # Fail-open: ein Parsing-Problem darf nicht jeden Bash-Befehl blockieren.
        # Die Warnung macht sichtbar, dass hier nicht geprüft wurde.
        print(f"check-commit-style: Input nicht lesbar, keine Prüfung ({fehler})",
              file=sys.stderr)
        return 0

    befehl = daten.get("tool_input", {}).get("command", "")
    if not befehl or not RELEVANT.search(befehl):
        return 0

    prueftext = befehl + "\n" + text_aus_dateien(befehl)

    treffer = [grund for muster, grund in VERBOTEN if muster.search(prueftext)]
    if not treffer:
        return 0

    print("Blockiert: Verstoß gegen die Commit-Konventionen dieses Repos.",
          file=sys.stderr)
    for grund in treffer:
        print(f"  - {grund}", file=sys.stderr)
    print("\nKorrigiere den Text. Den Hook zu umgehen ist keine Lösung.",
          file=sys.stderr)
    print("Details: CLAUDE.md, Abschnitt 'Commit-Konventionen'.", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main())
