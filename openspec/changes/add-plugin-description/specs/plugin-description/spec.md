# Spec Delta

## Purpose

An optional, short description for each plugin, set by the plugin author on the `@Plugin` annotation and shown in the places
where the plugins are already listed. Without a description, nothing changes.

## ADDED Requirements

### Requirement: Optional description element

The `@Plugin` annotation SHALL have an element `description` of type string whose default is the empty text. A plugin that does
not set the element SHALL behave exactly as it did before the element existed. A plugin compiled against an older version of
the annotation SHALL load and SHALL read the empty text.

#### Scenario: A plugin without the element reads the empty text

- **WHEN** a plugin class that does not set `description` is registered
- **THEN** its description is the empty text and it is registered with the same name, version and tier as before

#### Scenario: A plugin sets the element

- **WHEN** a plugin sets `description = "Verifies checksums of uploaded artifacts"`
- **THEN** the registry returns that text for the plugin

### Requirement: Normalisation and limit

The system SHALL store a description as plain text. It SHALL remove leading and trailing whitespace and every control character.
It SHALL cut a description longer than 280 characters to its first 280 code points and log one WARN line that names the plugin.
The system SHALL NOT refuse, skip or reorder a plugin because of its description.

#### Scenario: Whitespace and line breaks are removed

- **WHEN** the description is `"  First line\nsecond line  "`
- **THEN** the stored description is `"First linesecond line"`

#### Scenario: A long description is cut with one warning

- **WHEN** the description has 300 characters
- **THEN** the stored description has 280 characters, and the start logs one WARN line that names the plugin

#### Scenario: A description never blocks a plugin

- **WHEN** a plugin's description is longer than the limit or contains control characters
- **THEN** the plugin loads and initializes in the same order as before

### Requirement: Shown in the existing lists

The system information page SHALL show the description under the name of a plugin when the description is not empty. The
support information endpoint and the `plugins.json` file of the support bundle SHALL carry the field `description` for a plugin
only when the description is not empty. The store catalogue entry SHALL read an optional `description` field, and a missing
field SHALL read as the empty text.

#### Scenario: A plugin without a description produces the same output

- **WHEN** no loaded plugin sets a description
- **THEN** the support information JSON and `plugins.json` are byte-identical to the output before this change

#### Scenario: A plugin with a description shows it as text

- **WHEN** a plugin has the description `"<b>bold</b>"`
- **THEN** the system information page shows the characters `<b>bold</b>` as text and renders no markup

#### Scenario: The description is redacted, never a reason to refuse the bundle

- **WHEN** a plugin's description contains a value that the redaction rules match
- **THEN** the value is masked, the bundle is served, and the redaction is counted in the manifest

### Requirement: Store entry without the field

A catalogue entry without the `description` field SHALL be accepted, and the schema version of the index SHALL stay 1.

#### Scenario: An entry without the field is accepted

- **WHEN** the index holds an entry with no `description`
- **THEN** the entry is accepted and shows no description
