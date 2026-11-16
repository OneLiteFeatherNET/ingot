# Spec Delta

## ADDED Requirements

### Requirement: Ingot default scheme

The dashboard SHALL use a steel-toned default scheme when `frontend.appearance.seedColor` is empty. The default scheme SHALL NOT
use the Reposilite magenta as its primary colour, and its surfaces SHALL be neutral, without a pink tint.

#### Scenario: Default look without a seed

- **WHEN** an installation has no `seedColor` and a visitor opens the dashboard in light mode
- **THEN** the primary colour is the Ingot steel tone and the page background is a neutral surface

#### Scenario: Dark mode follows the same seed

- **WHEN** a visitor opens the dashboard in dark mode without a seed
- **THEN** the primary colour is the dark variant of the same steel tone, and the page background is a neutral dark surface

### Requirement: Operator seed keeps precedence

A non-empty `frontend.appearance.seedColor` SHALL replace the built-in scheme with the scheme derived from that seed, exactly as
before this change. Operators SHALL be able to restore the previous magenta scheme by setting `seedColor` to `#ac1c67`.

#### Scenario: Operator restores the old look

- **WHEN** an operator sets `seedColor` to `#ac1c67`
- **THEN** the dashboard uses the scheme derived from that seed, and the built-in default is not applied

#### Scenario: Empty seed keeps the default

- **WHEN** `seedColor` is empty or missing
- **THEN** the built-in Ingot scheme is applied and no operator theme is written to the page

### Requirement: Token names are stable

The colour roles SHALL keep their `--md-sys-color-*` and `--md-ext-color-*` names. A role SHALL NOT be renamed or removed, so that
plugins and stylesheets that read the roles keep working and receive the new values.

#### Scenario: Plugin reads a role

- **WHEN** a plugin stylesheet reads `--md-sys-color-primary`
- **THEN** the variable is defined in both schemes and holds the value of the active scheme

### Requirement: Text and interface contrast

Every text pair and every interface pair the dashboard uses SHALL meet WCAG AA in both the light and the dark scheme: text at least
4.5 to 1, and the outline of an interactive control against its surface at least 3 to 1.

#### Scenario: Contrast test fails on a weak pair

- **WHEN** a palette change makes a text pair fall below 4.5 to 1
- **THEN** the contrast test fails and names the pair and the scheme

#### Scenario: Status colours are not the only signal

- **WHEN** a status is shown with a success, warning or error colour
- **THEN** the status is also named in words, so a visitor with a colour vision deficiency can read it

### Requirement: Distinct chart series

The first four categorical chart hues SHALL differ from one another by at least 15 in lightness or 30 degrees of hue in both schemes,
and no categorical hue SHALL equal a status colour.

#### Scenario: Two series in one chart

- **WHEN** a chart shows the first and the second series
- **THEN** their hues are distinguishable by lightness or by hue, and neither is a status colour
