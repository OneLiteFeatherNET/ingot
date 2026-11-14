#!/usr/bin/env python3
"""Adds released plugin versions to ingot-plugin-catalogue.json, or yanks a listed version.

The release job copies each published jar into a local mirror of the Maven layout, lists the mirror's catalogue, and runs
this script on the mirror. It never touches the network.

    python3 .github/scripts/generate_plugin_catalogue.py publish --repository DIR --version 3.5.29 --generated 2026-11-14T10:00:00Z
    python3 .github/scripts/generate_plugin_catalogue.py yank --repository DIR --plugin opensearch-plugin --version 3.5.28

The catalogue is append-only. A listed version is never removed and never rewritten: a re-run with the same digest changes
nothing, and a rebuilt jar with another digest stops the run before anything is written. A yank changes only the `yanked`
flag of one entry. The generation time is an argument, so two runs with the same inputs write the same bytes.

Each entry is the format of schema 1 that the plugin store reads (see PluginCatalogue.kt): id, name, publisher, description,
coordinates, sha256, compatibility and yanked. The compatibility range of a release is its own version: `min` is the release
version and `below` the next patch version, so one release is one range.

Exit status: 0 on success, 1 when the run is refused, 2 on a usage error.
"""

import argparse
import hashlib
import json
import os
import re
import sys

SCHEMA = 1
CATALOGUE_FILE = "ingot-plugin-catalogue.json"
GROUP_ID = "net.onelitefeather.ingot"
PLUGIN_API = 1

# The plugins this release publishes. Adding a plugin is one line here and one build step in the release job.
OFFICIAL_PLUGINS = {
    "opensearch-plugin": {
        "name": "OpenSearch search backend",
        "description": "Answers artifact search from an OpenSearch cluster.",
        "publisher": "OneLiteFeather",
    },
}

VERSION_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
GENERATED_PATTERN = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")


class Refusal(Exception):
    """A run that must stop without writing anything. The message names the problem."""


def jar_path(repository, plugin, version):
    """The Maven path of a plugin jar inside the mirror."""
    return os.path.join(repository, *GROUP_ID.split("."), plugin, version, f"{plugin}-{version}.jar")


def coordinates(plugin, version):
    return f"{GROUP_ID}:{plugin}:{version}"


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def next_patch(version):
    """The version after `version` with the patch number raised by one, used as the exclusive upper bound."""
    major, minor, patch = version.split(".")
    return f"{major}.{minor}.{int(patch) + 1}"


def load_catalogue(path, generated):
    """Reads the listed catalogue, or starts an empty one when the mirror has none yet."""
    if not os.path.exists(path):
        return {"schema": SCHEMA, "generated": generated, "plugins": []}
    try:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
    except (OSError, ValueError) as error:
        raise Refusal(f"the listed catalogue cannot be read: {error}") from error
    if not isinstance(document, dict) or document.get("schema") != SCHEMA:
        raise Refusal(f"the listed catalogue is not schema {SCHEMA}, so it is not extended")
    if not isinstance(document.get("plugins"), list):
        raise Refusal("the listed catalogue has no plugins list")
    return document


def matching_entries(document, plugin, version):
    """The entries of one plugin id and version. A valid catalogue has at most one."""
    wanted = coordinates(plugin, version)
    return [entry for entry in document["plugins"] if entry.get("coordinates") == wanted]


def write_catalogue(path, document):
    """Writes the catalogue through a temporary file, so a reader never sees half of it."""
    text = json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n"
    temporary = f"{path}.tmp"
    with open(temporary, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    os.replace(temporary, path)


def publish(arguments):
    if not VERSION_PATTERN.match(arguments.version):
        raise Refusal(f"version {arguments.version!r} is not three dotted numbers, so it has no compatibility range")
    if not GENERATED_PATTERN.match(arguments.generated):
        raise Refusal(f"generated {arguments.generated!r} is not a UTC time such as 2026-11-14T10:00:00Z")

    path = os.path.join(arguments.repository, CATALOGUE_FILE)
    document = load_catalogue(path, arguments.generated)
    plugins = arguments.plugin or sorted(OFFICIAL_PLUGINS)

    additions = []
    for plugin in plugins:
        if plugin not in OFFICIAL_PLUGINS:
            raise Refusal(f"plugin {plugin!r} is not an official plugin of this release")
        jar = jar_path(arguments.repository, plugin, arguments.version)
        if not os.path.isfile(jar):
            raise Refusal(f"the jar of {coordinates(plugin, arguments.version)} is missing from the mirror at {jar}")
        digest = sha256_of(jar)

        listed = matching_entries(document, plugin, arguments.version)
        if len(listed) > 1:
            raise Refusal(f"the catalogue lists {coordinates(plugin, arguments.version)} {len(listed)} times")
        if listed:
            if listed[0].get("sha256") != digest:
                raise Refusal(
                    f"{coordinates(plugin, arguments.version)} is listed with another sha256 than the rebuilt jar, "
                    "so the run stops and nothing is written"
                )
            print(f"unchanged {coordinates(plugin, arguments.version)}")
            continue

        additions.append(entry_for(plugin, arguments.version, digest, arguments.plugin_api))
        print(f"added {coordinates(plugin, arguments.version)} sha256={digest}")

    if not additions:
        return 0
    document["plugins"].extend(additions)
    document["generated"] = arguments.generated
    write_catalogue(path, document)
    return 0


def entry_for(plugin, version, digest, plugin_api):
    metadata = OFFICIAL_PLUGINS[plugin]
    return {
        "id": plugin,
        "name": metadata["name"],
        "publisher": metadata["publisher"],
        "description": metadata["description"],
        "coordinates": coordinates(plugin, version),
        "sha256": digest,
        "compatibility": {
            "ingot": {"min": version, "below": next_patch(version)},
            "pluginApi": plugin_api,
        },
        "yanked": False,
    }


def yank(arguments):
    path = os.path.join(arguments.repository, CATALOGUE_FILE)
    document = load_catalogue(path, generated=None)
    listed = matching_entries(document, arguments.plugin, arguments.version)
    if len(listed) != 1:
        raise Refusal(f"{coordinates(arguments.plugin, arguments.version)} is listed {len(listed)} times, so nothing is yanked")
    if listed[0].get("yanked") is True:
        print(f"already yanked {coordinates(arguments.plugin, arguments.version)}")
        return 0
    listed[0]["yanked"] = True
    write_catalogue(path, document)
    print(f"yanked {coordinates(arguments.plugin, arguments.version)}")
    return 0


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    commands = parser.add_subparsers(dest="command", required=True)

    publish_parser = commands.add_parser("publish", help="append released versions to the catalogue")
    publish_parser.add_argument("--repository", required=True, help="local mirror of the Maven layout")
    publish_parser.add_argument("--version", required=True, help="the release version, e.g. 3.5.29")
    publish_parser.add_argument("--generated", required=True, help="generation time, UTC, e.g. 2026-11-14T10:00:00Z")
    publish_parser.add_argument("--plugin", action="append", help="official plugin id; repeatable, default all")
    publish_parser.add_argument("--plugin-api", type=int, default=PLUGIN_API, dest="plugin_api")
    publish_parser.set_defaults(handler=publish)

    yank_parser = commands.add_parser("yank", help="mark one listed version yanked")
    yank_parser.add_argument("--repository", required=True, help="local mirror of the Maven layout")
    yank_parser.add_argument("--plugin", required=True, help="plugin id")
    yank_parser.add_argument("--version", required=True, help="listed version to yank")
    yank_parser.set_defaults(handler=yank)

    return parser


def main(argv=None):
    parser = build_parser()
    try:
        arguments = parser.parse_args(argv)
    except SystemExit as exit_request:
        return 2 if exit_request.code else 0
    try:
        return arguments.handler(arguments)
    except Refusal as refusal:
        print(f"refused: {refusal}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
