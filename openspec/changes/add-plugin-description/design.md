# Design

This design follows the pattern of the plugin tier: an optional element on `@Plugin` with a default that equals today's
behaviour, one value in the plugin registry, and the existing lists as the only places where it is shown.

## D1. The element and its default

`@Plugin` gets `String description() default "";`. The default is the empty text, which means "no description". A class
compiled against the older annotation does not declare the element, and the JVM returns the default from the annotation type
when it reads the annotation. The empty text therefore needs no code path for old plugins.

The element is a plain Java annotation element, the same as `name`, so Kotlin and Java plugins write it the same way:
`@Plugin(name = "checksum", description = "Verifies checksums of uploaded artifacts")`.

## D2. Normalisation

One pure function turns the raw value into the stored value. It is the only place that knows the rules:

| Step | Rule |
|------|------|
| 1 | Remove leading and trailing whitespace. |
| 2 | Remove control characters (Unicode category Cc), including line breaks. A description is one line. |
| 3 | Cut the result to 280 code points. A cut is logged once, as a WARN line that names the plugin and the limit. |
| 4 | An empty result is the empty text, which reads as "no description". |

The function never throws. A description is never a reason to refuse a plugin, which matches the rule of the plugin tier.
The limit of 280 characters is short enough for a list row and long enough for one sentence.

## D3. Where the value lives

The plugin registry (`Extensions`) already keeps the metadata of every registered plugin, and the tier is read from it by
name, with the same fallback for an unknown name. The description is read the same way: a function by plugin name returns
the normalised text, and an unknown name returns the empty text. No new registry or cache is added.

## D4. Where it is shown

| Place | Shape | When it is written |
|-------|-------|--------------------|
| System information page | The plugin row gets a second line with the description, in text, under the name. | Only when the description is not empty. |
| `system-info.json` (`/api/admin/support/system-info`) | `PluginInformation` gets `description`. | Only when the description is not empty. The field is absent otherwise. |
| `plugins.json` in the support bundle | The same field as `system-info.json`. | Same rule. |
| Store catalogue entry | Optional `description` in `ingot-plugin-catalogue.json`, read by the store. | When the index carries it. A missing field reads as the empty text. |

The description is text. The page renders it as text content, never as HTML, and the store does not turn it into a link.

## D5. Redaction

The support bundle passes every value through the final redaction check. A description that looks like a secret would make
the check refuse the whole bundle, which would be an effect of one plugin's text on the operator's support tooling. So the
description is handled as a plugin field with the same rules as the other values: a match is masked and counted as a
redaction, and the bundle is still served. The check never refuses a bundle because of a description.

## D6. Frontend

The system information page is already a read-only list of plugins. The description is one more text node in that row. It uses
the existing text style of the page, and no new control, dialog or route is added. A new `data-ingot-part` anchor for the
description is added only if the anchor registry needs it, and it is set on the element that holds the text.

## D7. Compatibility

- No configuration key, environment variable, volume, manifest or plugin changes.
- The default is the empty text, so the registry, the system information and the bundle produce the same output for every
  plugin that sets nothing.
- The new JSON field is absent when the description is empty, so a consumer that parses `plugins.json` sees no new key for
  existing plugins.
- The new element is an addition in the plugin API. The plugin ABI gate reports it as an addition, and nothing is removed.

## D8. Test strategy

- Normalisation is a pure function, tested as a table of raw input and expected stored value, with no clock and no I/O.
- The registry lookup is tested with an in-memory registry, including an unknown name.
- The start warning for a cut description is tested with a captured appender.
- The support information is tested as a serialised JSON: one plugin with a description, one without. The second must be
  byte-identical to the output before this change.
- The frontend row is tested with a fixture that has a description and one that has none.
- Every test is fast, independent and repeatable, and takes no sleep, no system clock and no network.
