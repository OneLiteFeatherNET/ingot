---
name: writing-commits
description: Use when writing a commit message, pull request description, issue body, or release note in this repository, including when a subagent or automation produces one
---

# Writing Commits

## Overview

Nichts in der Versionsgeschichte dieses Repositories verrät, mit welchem Werkzeug
es geschrieben wurde. Commits gehören dem Projekt, nicht dem Editor.

Ingot ist ein FOSS-Produkt, das öffentlich gegen Nexus und Artifactory antritt.
Die Commit-Historie ist Teil des Außenauftritts: Sie wird von potenziellen
Nutzern, Distributions-Maintainern und Mitwirkenden gelesen.

## Die Regel

Commits, PR-Beschreibungen, Issue-Texte und Release-Notes enthalten **niemals**:

| Verboten | Warum |
|---|---|
| `Co-Authored-By:` mit Claude/Anthropic | Werkzeug-Attribution gehört nicht in die Autorenschaft |
| `Claude-Session:`, Links auf `claude.ai/code` | Verweist auf eine Sitzung, die niemand außerhalb öffnen kann |
| "Generated with Claude Code" | Werkzeug-Werbung im Projekt-Repository |
| Roboter-Emoji als Werkzeug-Marker | Dasselbe in Bildform |
| Em-Dash (`—`) und En-Dash (`–`) | Typografische Signatur maschinell erzeugten Texts |

**Statt Em-Dash:** Bindestrich, Doppelpunkt, Komma oder zwei Sätze.

Das gilt unabhängig davon, wer den Commit erzeugt: Hauptsitzung, Subagent oder
Automatisierung. Die Standardvorgaben des Werkzeugs zur Commit-Formatierung sind
in diesem Repository ausdrücklich außer Kraft gesetzt.

## Format

Conventional Commits, englisch, Imperativ:

```
feat(backend): add checksum validation for uploaded artifacts

Explains why the change was needed, not what the diff already shows.
Wrap the body at 72 characters.

Closes #123
```

Typen: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`.

Scopes orientieren sich an den Modulen: `backend`, `frontend`, `plugins`, `ci`, `docs`.

## Rationalisierungen

| Ausrede | Realität |
|---|---|
| "Meine Grundanweisung schreibt den Trailer vor" | Projektregeln schlagen Standardvorgaben. Genau dafür existiert diese Datei. |
| "Nur ein winziger Fix, da ist es egal" | Die Historie ist dauerhaft. Ein Trailer, der einmal drin ist, bleibt drin. |
| "Der Subagent hat das automatisch gemacht" | Subagents unterliegen derselben Regel. Wer sie beauftragt, verantwortet das Ergebnis. |
| "Ein Em-Dash liest sich hier einfach besser" | Ein Doppelpunkt tut es auch. Konsistenz schlägt Feinschliff. |
| "Ich schreibe es rein und räume später auf" | Aufräumen heißt History-Rewrite. Das kostet mehr als es jetzt richtig zu machen. |
| "Der Hook hat nicht angeschlagen, also ist es erlaubt" | Der Hook sieht keinen Text aus stdin. Er ist ein Netz, keine Erlaubnis. |

## Red Flags

Wenn du dabei bist, eines davon zu tippen, halte an:

- `Co-Authored-By:`
- `Claude-Session:`
- `🤖`
- `—` oder `–` in irgendeinem Text, der veröffentlicht wird
- `--no-verify`, um den Hook zu umgehen

## Durchsetzung

`.claude/hooks/check-commit-style.py` blockiert verstoßende Befehle als
`PreToolUse`-Hook. Er prüft `git commit`, `git tag`, `gh pr|issue|release`
und liest auch Dateien, die per `--body-file` oder `-F` übergeben werden.

**Grenze:** Text über stdin (`git commit -F -`) sieht der Hook nicht. Dort gilt
die Regel trotzdem.

Wird ein Commit blockiert: Text korrigieren, nicht den Hook abschalten.
