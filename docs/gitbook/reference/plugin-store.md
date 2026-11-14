---
description: Reference for the Ingot plugin store, covering the catalogue file, the entry fields, the layout and signatures of a Maven source, the compatibility rule, the verification steps, the sources and budget of a start, the cache, and the states a selected plugin can end in.
---

# Plugin store

The plugin store installs plugins from a catalogue that a Maven repository publishes. A selected plugin is downloaded at start, checked and loaded like a jar of the plugins directory. This page describes what the store reads and what it does with it. The variables are in [Plugin store](environment-variables.md#plugin-store), and the steps are in [Install a plugin](../how-to/install-a-plugin.md#install-from-the-plugin-store).

## Sources and file layout

A source is an `https` base URL of a Maven repository. For each source Ingot reads these files, where `<source>` is the base URL without a trailing slash:

| File | Location | Required |
|------|----------|----------|
| Catalogue | `<source>/ingot-plugin-catalogue.json` | Yes, for a source that lists a plugin |
| Catalogue signature | `<source>/ingot-plugin-catalogue.json.asc` | Yes, when a trusted key is configured, unless unsigned content is allowed |
| Jar | `<source>/<group path>/<artifactId>/<version>/<artifactId>-<version>.jar` | Yes, when the catalogue lists the plugin |
| Jar signature | the jar location with `.asc` appended | Yes, when a trusted key is configured, unless unsigned content is allowed |

The group path replaces each dot of the group id with a slash. For `org.example:ingot-example-plugin:1.0.0` the jar is `org/example/ingot-example-plugin/1.0.0/ingot-example-plugin-1.0.0.jar`. The `.sha256` files that Maven repositories publish are not read. The `sha256` field of the catalogue is the authority for the jar.

## Catalogue

The catalogue is one JSON document.

| Field | Type | Meaning |
|-------|------|---------|
| `schema` | integer | Must be `1`. Any other value, or a missing value, refuses the whole catalogue. |
| `plugins` | array | The entries. A missing array refuses the whole catalogue. |

Each entry has these fields. Unknown fields are ignored, so a schema 1 reader accepts later additions.

| Field | Type | Required | Meaning |
|-------|------|----------|---------|
| `id` | text | Yes | The plugin id. It must equal the `name` of the `@Plugin` annotation of the jar. |
| `name` | text | Yes | Display name. |
| `publisher` | text | Yes | Who publishes the plugin. |
| `coordinates` | text | Yes | `groupId:artifactId:version`, exactly three non-blank segments. |
| `sha256` | text | Yes | SHA-256 of the jar in hexadecimal, in either case. |
| `compatibility` | object | Yes, for the entry to be usable | See the compatibility rule below. |
| `yanked` | boolean | No, `false` by default | A yanked entry stays listed, but a new selection of it is refused. A copy already in the cache still loads. |

A text field that is missing or blank, or a `coordinates` value that is malformed, refuses the whole catalogue. An entry without a complete `compatibility` block is refused by itself, and the other entries of the same catalogue stay usable.

```json
{
  "schema": 1,
  "plugins": [
    {
      "id": "example-plugin",
      "name": "Example plugin",
      "publisher": "Example Publisher",
      "coordinates": "org.example:ingot-example-plugin:1.0.0",
      "sha256": "<64 hexadecimal characters of the jar>",
      "compatibility": {
        "ingot": { "min": "1.2.0", "below": "2.0.0" },
        "pluginApi": 1
      },
      "yanked": false
    }
  ]
}
```

## Compatibility rule

An entry may be selected only when all of these hold:

* `pluginApi` equals `1`, the plugin API number that this Ingot reads.
* The running Ingot version is at least `ingot.min` and below `ingot.below`. `min` is inclusive and `below` is exclusive.

Versions are compared segment by segment as numbers. A missing segment counts as `0`, and a segment counts its leading digits, so `1.2.0-SNAPSHOT` compares as `1.2.0`. An entry that fails the rule is refused with the reason in the log.

## Verification

Trust comes from the keys in `INGOT_PLUGIN_TRUSTED_KEYS`, read once at start, and not from the files of the source. The keys are OpenPGP public keys in an ASCII-armoured file, or in a directory of such files. A signature verifies only when its key is one of these keys and the key has not expired at the time of the start.

* **Catalogue.** When a trusted key is configured, the signature file must verify the catalogue. A missing signature file refuses the catalogue, unless unsigned content is allowed.
* **Jar.** The digest of the jar must equal the `sha256` of its entry. This check always applies, and it applies to unsigned jars as well. A signature file, when present, must verify the jar. A missing signature file refuses the jar, unless unsigned content is allowed.
* **A signature that is present and does not verify** refuses its file, even when unsigned content is allowed.
* **No trusted key.** The store uses no source unless `INGOT_PLUGIN_ALLOW_UNSIGNED` is `true`. With that switch, the catalogue and the jars load without a signature check, and each such plugin is `UNVERIFIED`.

A jar is written to the cache only after its checks pass, so nothing unverified is cached.

## Sources and order

* A source that is not an `https` address is dropped at start, and the log names it.
* The sources are asked in the order of `INGOT_PLUGIN_SOURCES`. The first source whose catalogue lists the coordinate provides the plugin.
* Once a source lists the plugin, the search for that plugin ends there. When that source then refuses the jar, or cannot deliver it, the next source is not tried.

## Budget and limits of a start

| Limit | Value |
|-------|-------|
| Time for all selected plugins together | 60 seconds. Plugins that are not resolved by then are `UNAVAILABLE`, and the server starts without them. |
| Time for one HTTP request | 30 seconds, for connecting and for the response. |
| Size of the catalogue | 5 MiB |
| Size of a signature file | 64 KiB |
| Size of a jar | 64 MiB |

A file larger than its limit counts as unreachable. Redirects are followed, except a redirect from `https` to `http`.

## Cache

Verified copies are kept in `plugin-cache` in the working directory. In the container image that is `/app/data/plugin-cache`. The cache has one directory per coordinate, with the jar, its signature if it has one, and a record of the source, the digest and the time of the check.

* A selected plugin with a verified cached copy loads without any network request.
* A cached signed jar is checked again against the trusted keys at every start. When the check fails, for example because its key is no longer trusted, the sources are asked for a new copy.
* A cached jar whose digest no longer matches its record is refused and not loaded.
* Ingot does not delete the cache. A plugin that is no longer selected leaves its directory behind until you remove it.

## Precedence

At start the plugins directory is loaded first, then the store, then the bundled jars of the image.

* A store plugin whose id is also held by a jar of the plugins directory is `SHADOWED`. The jar of the directory wins.
* A store plugin that loads replaces a bundled jar with the same id, and the bundled jar is then skipped.
* A jar must declare exactly one plugin. Its declared id must equal the `id` of its catalogue entry, otherwise it is refused.

## States

Each selected plugin ends in one state. The log line is `Plugin store: <id> <STATE>` for the states below, followed by the reason when the plugin does not load. `LOADED` and `CACHED` name the source in the log line, and `UNVERIFIED` is logged as a warning at every start.

| State | Loaded | Meaning |
|-------|--------|---------|
| `LOADED` | Yes | Downloaded, with its digest and signature verified, and written to the cache. |
| `CACHED` | Yes | Loaded from the cache, with the signature checked again against the trusted keys. |
| `UNVERIFIED` | Yes | Loaded without a signature check, because unsigned content is allowed. |
| `REFUSED` | No | A check failed, or the entry may not be selected. The reason names the check. |
| `UNAVAILABLE` | No | No source could be reached and no verified copy is cached, or the start budget ran out. |
| `FAILED` | No, after loading | The class of the plugin could not be initialised. The server starts without it. Routes that it registered before the failure stay until the next start. |
| `SHADOWED` | No | A jar of the plugins directory has the same plugin id. |

A failed plugin never stops the start. The server logs the state and starts with the plugins that loaded.

## Related

* [Install a plugin](../how-to/install-a-plugin.md) shows the steps for the store and for a jar.
* [Plugin store](environment-variables.md#plugin-store) lists the variables and their defaults.
* [Configuration file and startup parameters](configuration-and-startup.md) describes the local key `plugins`, which adds to `INGOT_PLUGINS`.
* [Plugin API](plugin-api.md) describes what a plugin can do once it is loaded.
