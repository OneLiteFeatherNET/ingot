# Ingot (Fork von Reposilite)

Dieses Repository ist ein Fork von `dzikoysk/reposilite` und wird zum eigenständigen
FOSS-Produkt **Ingot** umgebaut.

## Namensräume: bewusst zweigeteilt

- **Maven-groupId:** `net.onelitefeather.ingot`
- **Java-/Kotlin-Packages:** bleiben `com.reposilite.*`
- **Modulverzeichnisse und Bibliotheks-artifactIds:** bleiben `reposilite-*`
- **Server-Bundle:** `net.onelitefeather.ingot:ingot` (Ersatz fuer `com.reposilite:reposilite`)

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
- Planung liegt in Outline (Vault), nicht im Repo.

## Rückwärtskompatibilität: Ingot ist ein Drop-In-Ersatz

**Diese Regel steht über Härtung, Aufräumen und Geschmack.**

Der Umstieg von Reposilite auf Ingot kostet genau eine geänderte Zeile: die
Abhängigkeitskoordinate im Build oder die Image-Referenz im Deployment. Nichts sonst.
Wer eine bestehende Reposilite-Installation auf Ingot zeigen lässt, darf **keine**
Konfigurationsdatei, kein Volume, kein Manifest und kein Plugin anfassen müssen.

Das ist der Grund, warum dieser Fork überhaupt Nutzer gewinnen kann. Eine Änderung, die
diesen Satz unwahr macht, ist ein Fehler, auch wenn sie technisch besser ist.

### Was der Kontrakt umfasst

- **Container-Image:** `ghcr.io/onelitefeathernet/ingot` ersetzt `dzikoysk/reposilite`
  1:1. Gleicher Entrypoint (`/app/entrypoint.sh`), gleiche Pfade (`/app/data`,
  `/var/log/reposilite`), gleicher Port `8080`, gleiches Volume, gleicher Healthcheck,
  gleiche Env-Variablen (`JAVA_OPTS`, `REPOSILITE_OPTS`, `PUID`, `PGID`,
  `REPOSILITE_FORCE_CHOWN`), gleiches Standardverhalten. Der Server liefert das Dashboard
  weiterhin selbst aus.
- **Startprivilegien:** Das Image setzt bewusst **kein** `USER`. Upstream startet als root
  und lässt den Entrypoint selbst auf den Service-Account wechseln. Nur dieser Weg kann
  `PUID`/`PGID` auswerten und ein Volume chownen, das jemand anderem gehört. Ein
  Non-Root-Default würde genau die Deployments zerlegen, für die das Image gedacht ist.
  Unprivilegiert läuft es trotzdem: `user: "977:977"` beziehungsweise
  `runAsUser`/`runAsGroup` genügt, und der Entrypoint erkennt das.
- **Dateinamen im Image:** Das Artefakt heisst `ingot.jar`, `/app/reposilite.jar` bleibt
  als Symlink daneben bestehen. Deployments, die Entrypoint oder Command überschreiben,
  nennen den Jar-Namen selbst.
- **Konfiguration und Daten:** siehe die Liste unten. Ein Datenverzeichnis aus Reposilite
  wird ohne Migrationsschritt übernommen.
- **Plugin-API:** Packages bleiben `com.reposilite.*`, siehe oben.

### Wie Neues trotzdem hinzukommt

Additiv und abschaltbar, nie ersetzend:

- Neue Env-Variablen bekommen das Präfix `INGOT_`, die `REPOSILITE_`-Variante bleibt
  gültig und wird als Fallback gelesen.
- Neue Betriebsformen sind Opt-in. Der geteilte Dashboard-Container ist das Muster: ein
  zweites Image plus ein Schalter (`INGOT_LOCAL_DEFAULTFRONTEND=false`), während der
  Default unverändert bleibt.
- Härtung, die den Kontrakt einschränken würde, wird dokumentiert und dem Betreiber
  überlassen, statt sie ihm ins Image zu backen (`no-new-privileges`, `read-only`,
  `cap-drop` gehören in die Compose-Datei und in die Doku).

### Vor jedem Commit an Image, Entrypoint oder Konfiguration prüfen

1. Startet eine unveränderte Upstream-Compose-Datei damit noch, wenn nur die Image-Zeile
   getauscht wird?
2. Übernimmt sie ein bestehendes `/app/data`, egal wem es gehört?
3. Wirken `PUID`, `PGID` und `REPOSILITE_OPTS` unverändert?

Der CI-Job "Container images" beantwortet diese drei Fragen automatisch, indem er das
Server-Image startet. Schlägt er fehl, ist die Änderung falsch, nicht der Test.

### Wie die Images gebaut werden

- **Build-Stages hängen an `--platform=$BUILDPLATFORM`.** Ein Jar enthält keinen
  Maschinencode, gebündeltes JavaScript auch nicht, deshalb wird pro Zielarchitektur nur die
  Run-Stage gebaut. Wer den Pin entfernt, schickt den kompletten Gradle- und npm-Build durch
  QEMU und verzehnfacht die Bauzeit für ein identisches Ergebnis.
- **Beide Images gehen für `linux/amd64` und `linux/arm64` raus**, Nightlies eingeschlossen,
  und CI baut die zweite Architektur bei jedem Pull Request mit.
- **Jeder Push trägt SBOM und Provenance** (`sbom: true`, `provenance: mode=max`).
- **Basis-Images sind auf Tag *und* Digest gepinnt**, Renovate hebt beides zusammen an.
  Trivy scannt sowohl die Abhängigkeitsbäume als auch die fertigen Images.

## Laufzeit-Kontrakte, die bewusst `reposilite` heissen

Umbenennen würde bestehende Deployments oder Plugins brechen, ohne dass jemand etwas
davon hätte. Diese Namen bleiben:

- Konfigurationsdateien (`configuration.cdn`, `configuration.shared.json`) und die
  SQLite-Datei `reposilite.db`
- `{{REPOSILITE.*}}`-Platzhalter im Frontend, über die auch Plugins eigene Werte
  registrieren
- Container-Pfade `/app/data`, `/var/log/reposilite`, der Service-User `reposilite` und
  `/app/reposilite.jar`
- Die alten Env- und Property-Präfixe `REPOSILITE_LOCAL_` / `reposilite.local.` sowie
  `REPOSILITE_OPTS`. Die `INGOT_`-Varianten existieren zusätzlich und haben Vorrang.
