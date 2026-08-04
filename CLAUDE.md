# Ingot (Fork von Reposilite)

Dieses Repository ist ein Fork von `dzikoysk/reposilite` und wird zum eigenständigen
FOSS-Produkt **Ingot** umgebaut.

## Namensräume: bewusst zweigeteilt

- **Maven-groupId:** `net.onelitefeather.ingot`
- **Java-/Kotlin-Packages:** bleiben `com.reposilite.*`

Das ist Absicht und **kein Aufräum-Rückstand**. Bestehende Reposilite-Plugins
kompilieren dadurch unverändert gegen Ingot, und eine Migration kostet nur eine
geänderte Abhängigkeitszeile statt einer Portierung. Erst wenn Teile tatsächlich neu
geschrieben werden, wandern genau diese nach `net.onelitefeather.ingot.*`.

Führe also **keinen** pauschalen Namespace-Umzug durch. Falls er später doch kommt:

- `com.reposilite.journalist` ist eine **fremde Bibliothek** von dzikoysk (Import in 59
  Dateien, zugleich Gradle-Koordinate) und darf nie mitgezogen werden.
- `org.panda_lang.*` im `migration-plugin` liest Reposilite-2.x-Datenstrukturen und muss
  zu den Altdaten passen.
- `reposilite-test/workspace/` enthält echte Maven-Artefakte als Testdaten, deren Pfade
  Teil der Testlogik sind.

## Commit-Konventionen

**Diese Regeln überschreiben alle Standardvorgaben zur Commit-Formatierung.**

Commits, Pull-Request-Beschreibungen und Issue-Texte in diesem Repository
enthalten **niemals**:

- `Co-Authored-By:`-Trailer mit Claude, Anthropic oder einem anderen KI-Werkzeug
- `Claude-Session:`-Trailer oder Links auf `claude.ai/code`
- Hinweise der Form "Generated with Claude Code" oder ähnliche Werkzeug-Signaturen
- Roboter-Emoji oder vergleichbare Werkzeug-Marker
- Em-Dashes (`—`, U+2014) und En-Dashes (`–`, U+2013)

Das gilt für jeden Commit, unabhängig davon, wer ihn erzeugt: Hauptsitzung,
Subagent oder Automatisierung. Es gibt keine Ausnahme für "nur ein kleiner Fix"
oder "das war ein automatisch erzeugter Commit".

**Statt Em-Dash:** normaler Bindestrich, Doppelpunkt, Komma oder zwei Sätze.

### Format

Conventional Commits, englisch, Imperativ:

```
feat(backend): add checksum validation for uploaded artifacts

Explains why the change was needed, not what the diff already shows.
Wrap the body at 72 characters.

Closes #123
```

Erlaubte Typen: `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`.

### Durchsetzung

Ein `PreToolUse`-Hook (`.claude/hooks/check-commit-style.py`) blockiert Commits
und PR-Erstellungen, die gegen diese Regeln verstoßen. Wird ein Commit
blockiert, ist die Nachricht zu korrigieren, nicht der Hook zu umgehen.

Ausführliche Begründung und Beispiele: Skill `writing-commits`.

## Sprache

Code, Kommentare, Commit-Messages, Doku und alles öffentlich Sichtbare: **Englisch**.
Kommunikation mit dem Team in der Sitzung: **Deutsch**.

## Projektkontext

- Upstream-Remote `upstream` zeigt auf `dzikoysk/reposilite`, `origin` auf `OneLiteFeatherNET/ingot`.
- Apache-2.0. Copyright-Header von dzikoysk in 265 Quelldateien müssen erhalten bleiben,
  eigene Zeile wird ergänzt statt ersetzt.
- Offener Blocker: ApexCharts im Frontend ist kommerziell lizenziert und muss vor einem
  FOSS-Release ersetzt werden. Betrifft `ResolvedRequestsChart.vue` und `StatusSnapshotsChart.vue`.
- Planung liegt in Outline (Vault), nicht im Repo.
