#!/usr/bin/env python3
"""Validates the CycloneDX SBOMs of the server jar and the dashboard.

The release job runs this before it attests the SBOMs, and the sbom job of the pull request checks run it on the
files it generates. It checks, in order:

- the document against the CycloneDX 1.6 JSON schema, vendored under schemas/ so that no run needs the network;
- bomFormat CycloneDX and specVersion 1.6;
- metadata.timestamp, metadata.tools and the name and version of metadata.component;
- every component has a name, a version and a purl, and every bom-ref is unique;
- every reference in dependencies names a component or the metadata component;
- the share of components with a licence reaches the threshold (95 percent by default).

The shares of components with a hash and with a supplier are printed and never fail the run. The generators fill them
from different sources, so a gap there is reported for the operator rather than blocking the release.

Usage:

    python3 .github/scripts/verify_sbom.py [--min-licence-share 0.95] FILE [FILE ...]

Exits 0 when every file passes, 1 when a file fails and 2 on a usage error. Requires jsonschema.
"""

import argparse
import json
import os
import sys
from dataclasses import dataclass, field

from jsonschema import Draft7Validator
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT7

SCHEMA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schemas")
SCHEMA_FILE = "bom-1.6.schema.json"
SCHEMA_REFERENCES = ("spdx.schema.json", "jsf-0.82.schema.json")
EXPECTED_SPEC_VERSION = "1.6"
DEFAULT_MIN_LICENCE_SHARE = 0.95


@dataclass
class Report:
    path: str
    errors: list = field(default_factory=list)
    components: int = 0
    licence_share: float = 0.0
    hash_share: float = 0.0
    supplier_share: float = 0.0

    @property
    def ok(self):
        return not self.errors

    def summary(self):
        return (
            f"{self.path}: {self.components} components, licence {self.licence_share:.1%}, "
            f"hash {self.hash_share:.1%}, supplier {self.supplier_share:.1%}"
        )


def load_schema_registry():
    """Registers the vendored schemas under their canonical ids, so that the references resolve without the network."""
    resources = []
    for name in (SCHEMA_FILE,) + SCHEMA_REFERENCES:
        with open(os.path.join(SCHEMA_DIR, name), encoding="utf-8") as handle:
            contents = json.load(handle)
        resources.append((contents["$id"], Resource.from_contents(contents, default_specification=DRAFT7)))
    return Registry().with_resources(resources)


def schema_errors(document):
    """Returns the schema violations of the document, each naming the path that violates the schema."""
    with open(os.path.join(SCHEMA_DIR, SCHEMA_FILE), encoding="utf-8") as handle:
        bom_schema = json.load(handle)
    validator = Draft7Validator(bom_schema, registry=load_schema_registry())
    errors = []
    for error in sorted(validator.iter_errors(document), key=lambda e: [str(p) for p in e.absolute_path]):
        location = "/".join(str(part) for part in error.absolute_path) or "<root>"
        errors.append(f"schema violation at {location}: {error.message}")
    return errors


def dicts(value):
    """Keeps the entries of a list that are objects. A malformed document is reported by the schema, not by a crash."""
    if not isinstance(value, list):
        return []
    return [entry for entry in value if isinstance(entry, dict)]


def share(items, predicate):
    """Returns the fraction of items that match the predicate, or 0.0 for an empty list."""
    if not items:
        return 0.0
    return sum(1 for item in items if predicate(item)) / len(items)


def has_hash(component):
    """True when the component carries a hash itself or on its distribution reference, as npm SBOMs do."""
    if component.get("hashes"):
        return True
    return any(reference.get("hashes") for reference in dicts(component.get("externalReferences")))


def check_document(document, path="<document>", min_licence_share=DEFAULT_MIN_LICENCE_SHARE):
    """Validates a parsed SBOM and returns a report. Nothing is printed and nothing is raised for an invalid document."""
    report = Report(path=path)
    report.errors.extend(schema_errors(document))
    if not isinstance(document, dict):
        return report

    if document.get("bomFormat") != "CycloneDX":
        report.errors.append(f"bomFormat must be CycloneDX, found {document.get('bomFormat')!r}")
    if document.get("specVersion") != EXPECTED_SPEC_VERSION:
        report.errors.append(
            f"specVersion must be {EXPECTED_SPEC_VERSION}, found {document.get('specVersion')!r}"
        )

    metadata = document.get("metadata") if isinstance(document.get("metadata"), dict) else {}
    for key in ("timestamp", "tools"):
        if not metadata.get(key):
            report.errors.append(f"metadata.{key} is missing")
    main_component = metadata.get("component") if isinstance(metadata.get("component"), dict) else {}
    for key in ("name", "version"):
        if not main_component.get(key):
            report.errors.append(f"metadata.component.{key} is missing")

    components = dicts(document.get("components"))
    report.components = len(components)
    refs = [main_component["bom-ref"]] if main_component.get("bom-ref") else []
    for index, component in enumerate(components):
        label = component.get("name") or f"components[{index}]"
        if not component.get("version"):
            report.errors.append(f"component '{label}' has no version")
        if not component.get("purl"):
            report.errors.append(f"component '{label}' has no purl")
        if component.get("bom-ref"):
            refs.append(component["bom-ref"])

    seen = set()
    for ref in refs:
        if ref in seen:
            report.errors.append(f"duplicate bom-ref '{ref}'")
        seen.add(ref)

    for entry in dicts(document.get("dependencies")):
        if entry.get("ref") not in seen:
            report.errors.append(f"dependency entry names an unknown reference '{entry.get('ref')}'")
        children = entry.get("dependsOn") if isinstance(entry.get("dependsOn"), list) else []
        for child in children:
            if child not in seen:
                report.errors.append(f"dependency '{entry.get('ref')}' names an unknown reference '{child}'")

    report.licence_share = share(components, lambda c: bool(c.get("licenses")))
    report.hash_share = share(components, has_hash)
    report.supplier_share = share(components, lambda c: bool(c.get("supplier")))
    if report.licence_share < min_licence_share:
        report.errors.append(
            f"only {report.licence_share:.1%} of components declare a licence, "
            f"the threshold is {min_licence_share:.1%}"
        )
    return report


def check_file(path, min_licence_share=DEFAULT_MIN_LICENCE_SHARE):
    """Reads one SBOM file and returns its report, including a read or parse failure as an error."""
    try:
        with open(path, encoding="utf-8") as handle:
            document = json.load(handle)
    except (OSError, json.JSONDecodeError) as error:
        return Report(path=path, errors=[f"cannot read {path}: {error}"])
    return check_document(document, path=path, min_licence_share=min_licence_share)


def main(argv=None):
    parser = argparse.ArgumentParser(description="Validate CycloneDX SBOMs of the release.")
    parser.add_argument(
        "--min-licence-share", type=float, default=DEFAULT_MIN_LICENCE_SHARE,
        help="fraction of components that must declare a licence (default: %(default)s)",
    )
    parser.add_argument("files", nargs="+", metavar="FILE")
    args = parser.parse_args(argv)

    failed = False
    for path in args.files:
        report = check_file(path, args.min_licence_share)
        print(report.summary())
        for error in report.errors:
            print(f"  error: {error}", file=sys.stderr)
        failed = failed or not report.ok
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
