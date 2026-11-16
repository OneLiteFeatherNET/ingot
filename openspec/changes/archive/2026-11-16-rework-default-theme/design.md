# Design

## Context

Measured on `main`:

- The built-in scheme is `reposilite-frontend/src/styles/md3/color.css`. The accent roles come from the OLF table generated from
  `#EC008B` with SchemeVibrant (the header comment), and the light primary is `#ac1c67`. The neutral roles are a near-neutral
  palette at hue 357, chroma 3, so the surfaces are pinkish white.
- `src/store/appearance.js` reads `frontend.appearance`. When `seedColor` is empty it removes any operator theme and the built-in
  scheme applies. When it is set, `theme/runtime` derives the scheme from the seed with `schemeVariant` (default `VIBRANT`).
- `src/store/palette.js` reads the role values from the root element on every render, so a theme switch is picked up without a
  second copy. Eight chart tokens (`--md-ext-color-chart-1` to `-8`) are harmonised toward the seed and are not MD3 roles.
- `settings-domains.md` documents `seedColor` and `schemeVariant` under `frontend.appearance`.

## Decisions

### D1. The default is an Ingot colour, not the Reposilite magenta

The seed of the built-in scheme changes from the magenta to a steel blue. The reason is the product, not taste alone: the
dashboard is a place of tables and status, and the accent should read as a tool colour. Steel is the recommendation. Bronze is the
warmer alternative. Iron is the most neutral. The maintainer picks one in review (open question 1).

### D2. Candidate seeds and the resulting roles

The three candidates below are hand-set hex values, checked with a WCAG relative-luminance calculation. They are candidates for the
review, not final values: the implementation generates the full tonal set with the Material theme builder from the chosen seed and
replaces these values with the generated ones, and the contrast test (task 2.2) runs on the generated values.

Light scheme, contrast ratios as computed (AA needs 4.5 for text, 3 for interface):

| Role pair | Steel (`#3A5F85`) | Bronze (`#7A4A1C`) | Iron (`#3E5566`) |
| --- | --- | --- | --- |
| `on-primary` on `primary` | `#FFFFFF` on `#3A5F85`, 6.66 | `#FFFFFF` on `#7A4A1C`, 7.44 | `#FFFFFF` on `#3E5566`, 7.79 |
| `on-primary-container` on `primary-container` | `#0E2A45` on `#D3E4F7`, 11.29 | `#2E1500` on `#FFDCC0`, 13.28 | `#0A1E2C` on `#CFE0EE`, 12.59 |
| `primary` on `surface` (links, text actions) | 6.32 | 7.08 | 7.38 |
| `secondary` on-secondary | `#56616F`, 6.30 | `#5F5A52`, 6.84 | `#4F6070`, 6.48 |
| `tertiary` on-tertiary | `#7A5A2E`, 6.31 | `#2F6470`, 6.61 | `#5B5A7E`, 6.54 |
| `on-surface` on `surface` | `#1B1C1F` on `#F8F9FB`, 16.17 | `#1E1B18` on `#FBF9F6`, 16.31 | `#191C1E` on `#F7F9FA`, 16.22 |
| `on-surface-variant` on `surface-variant` | 7.19 | 7.14 | 7.25 |
| `outline` on `surface` (UI 3 to 1) | `#71787F`, 4.25 | `#7E756B`, 4.30 | `#70787D`, 4.26 |
| `success` / `warning` / `error` on `surface` | 6.19 / 6.57 / 6.13 | 6.21 / 6.59 / 6.15 | 6.18 / 6.55 / 6.12 |

Dark scheme:

| Role pair | Steel (`#A4C9EE`) | Bronze (`#F2B98A`) | Iron (`#A8C8DC`) |
| --- | --- | --- | --- |
| `on-primary` on `primary` | `#0B2F4C`, 7.98 | `#4A2800`, 7.58 | `#0D2A3A`, 8.49 |
| `on-primary-container` on `primary-container` | 7.18 | 7.20 | 7.40 |
| `primary` on `surface` | 10.70 | 10.66 | 10.55 |
| `on-secondary` on `secondary` | 7.69 | 7.72 | 7.81 |
| `on-tertiary` on `tertiary` | 7.78 (`#E2C29A`) | 7.65 (`#8FD0DC`) | 7.87 (`#C5C4EA`) |
| `on-surface` on `surface` | 14.30 | 14.34 | 14.40 |
| `on-surface-variant` on `surface-variant` | 5.44 | 5.42 | 5.50 |
| `outline` on `surface` | 5.87 | 5.89 | 5.86 |
| `success` / `warning` / `error` on `surface` | 11.09 / 11.19 / 10.88 | 11.13 / 11.23 / 10.92 | 11.12 / 11.22 / 10.91 |

Every pair listed clears its threshold in all three candidates. The tightest pair is `on-surface-variant` on `surface-variant` in
the dark scheme (5.4 against 4.5), and the tightest interface pair is `outline` on `surface` in the light scheme (4.25 against 3).

The status colours (`success` `#1E6B3A`, `warning` `#7A5200`, `error` `#BA1A1A` in light; the dark values in the table set) are
not chosen by the seed. They keep their roles and are checked here only for contrast.

Colour-blind safety: the green and the red of the light scheme have nearly the same luminance (6.19 and 6.13 on the surface). Under
red-green deficiency they can look alike. The dashboard already pairs every status colour with a word (the palette file says so),
so the colour never carries the meaning alone. The implementation adds a simulation test (task 2.2) for the three common forms of
deficiency. This design does not claim that the simulation has been run; it is the first thing the implementation checks, and a
failure there changes the status hues, not the seed.

### D3. Chart hues come from the seed

The eight categorical hues are re-derived from the chosen seed and harmonised the same way as today. The first four must be
distinguishable from each other: each pair differs by at least 15 in lightness or by at least 30 degrees of hue, and the test in
task 2.3 checks it. The status colours are never used as series colours, as today.

### D4. Token names and the operator seed do not change

- No token is renamed or removed. Plugins that read `--md-sys-color-*` or `--md-ext-color-*` keep working.
- `frontend.appearance.seedColor` keeps the precedence it has: a set seed replaces the built-in scheme. An operator who sets
  `#ac1c67` gets the old magenta scheme, because the same seed produces the same derived scheme as before.
- No new configuration key is added. The way back is the existing field.

### D5. What the upgrade note says

`upgrade-changes.md` gets a short section: the default colours change on upgrade; an installation without a seed looks steel
instead of magenta; to keep the old colours set `seedColor` to `#ac1c67` in the frontend appearance settings. The note names the
setting, not the environment variable, because the seed has no environment variable today.

## Alternatives considered

- **Keep the magenta and only neutralise the surfaces.** Smaller diff. Rejected: the accent is the colour people see first, and the
  maintainer's complaint is the accent.
- **A new key `frontend.appearance.defaultScheme`.** Rejected: it adds configuration for a presentation default, and the seed already
  is the way to choose a scheme.
- **Let the operator choose among the three candidates in the settings.** Rejected for this change: it is a settings feature with its
  own UI and tests, and it is not needed to fix the default.

## Risks

- **Operators notice the change without a note.** Mitigated by the upgrade note and the reference page; the release notes name it.
- **A plugin hard-codes the old hex value.** Not covered by the token contract. The change does not try to protect it.
- **The generated tonal set differs from the hand-set candidates.** The contrast test runs on the generated values, so a difference
  is caught before merge, not after.

## Open questions

1. Which candidate becomes the default. Default: steel. The reasons are the neutrality of the blue, the clearest separation from the
   amber warning hue, and that it reads as a tool colour. The alternative is bronze, which is warmer and more distinctive but sits
   nearer to the warning colour, which is amber. Iron is safe but the least recognisable of the three.
2. Whether the candidates are shown in Appearance settings as presets. Default: no. This change sets one default. The alternative adds
   a preset list, which is a separate settings change.
3. Whether the status hues change with the seed. Default: no, they keep their fixed roles. The alternative derives them, which makes
   the colour-blind check harder to keep.
