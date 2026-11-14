#!/usr/bin/env python3
"""Selects the backend test packages that a pull request can reach.

Usage:
    select_backend_tests.py [--root DIR] < changed-paths.txt

Reads one changed path per line on stdin (the three-dot diff of the pull request) and prints:

    mode=partial|full
    reason=<one line>
    package=<domain>          one per selected package (partial only)
    test=<--tests pattern>    one per filter of the unit source set (partial only)
    integration=<pattern>     one per filter of the integration source set (partial only)

A full decision prints only mode and reason. Any failure of the script itself prints mode=full, so the job
runs the whole suite. The decision rule is the one of design.md, Decision 3 of select-backend-tests-by-package.

A domain is a package directly below com.reposilite or net.onelitefeather.ingot. The selection of a changed
domain is the domain itself plus the domains whose sources import it directly (one hop, Decision 2). The
smoke set (SMOKE_CLASSES) is added to every partial run, so a renamed smoke class fails here, not silently.

The deny list mirrors coverage_changes.sh. Keep the two in step when one of them changes.
"""

import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, field

BACKEND = "reposilite-backend"
SOURCE_SETS = {
    "main": f"{BACKEND}/src/main/kotlin",
    "test": f"{BACKEND}/src/test/kotlin",
    "integration": f"{BACKEND}/src/integration/kotlin",
}
KOTLIN_ROOTS = tuple(SOURCE_SETS.values())

# Mirrors DENIED in coverage_changes.sh: documentation, OpenSpec, templates and repository metadata.
DENIED = re.compile(
    r"^(docs/|openspec/|\.github/ISSUE_TEMPLATE/|\.well-known/)"
    r"|\.md$"
    r"|^(\.github/CODEOWNERS|\.github/pull_request_template\.md|\.clomonitor\.yml|LICENSE|NOTICE)$"
)

# Packages whose change cannot be narrowed by who imports them: the composition, the configuration, the
# shared types, the HTTP and plugin hooks, the storage facade and the schema locks.
INFRASTRUCTURE = frozenset({
    "com.reposilite",
    "com.reposilite.configuration",
    "com.reposilite.shared",
    "com.reposilite.web",
    "com.reposilite.plugin",
    "com.reposilite.storage",
    "net.onelitefeather.ingot.web",
    "net.onelitefeather.ingot.schema",
})

# Test classes that run on every partial pull request run, whatever the selection. Named here, so a
# renamed class makes the lookup in smoke_filters fail instead of disappearing.
SMOKE_CLASSES = (
    "DependencyRulesTest",
    "WritePathEventCharacterisationTest",
    "OpenApiCompletenessTest",
    "OpenApiAccessIntegrationTest",
    "OpenApiSecuritySchemesIntegrationTest",
    "SecurityHeadersSettingsTest",
    "DashboardPolicyTest",
    "StartupParametersIntegrationTest",
    "StartupRefusalTest",
    "StartupRulesTest",
)

THRESHOLD = 0.60
ANNOTATION = re.compile(r"@(?:ParameterizedTest|Test)\b")
IMPORT = re.compile(r"^import\s+([A-Za-z_][\w.]*)", re.MULTILINE)


class SelectionError(Exception):
    """The tree does not match what the script expects, for example a smoke class is missing."""


@dataclass
class SourceFile:
    source_set: str
    package: str
    name: str
    domain: object
    annotations: int
    imports: list = field(default_factory=list)


@dataclass
class Decision:
    mode: str
    reason: str
    selected: list = field(default_factory=list)
    test_filters: list = field(default_factory=list)
    integration_filters: list = field(default_factory=list)


def domain_of_package(package):
    """Returns the domain of a package of the backend, or None when the script does not map it."""
    parts = package.split(".") if package else []
    if package == "com.reposilite":
        return "com.reposilite"
    if len(parts) >= 3 and parts[:2] == ["com", "reposilite"]:
        return ".".join(parts[:3])
    if len(parts) >= 4 and parts[:3] == ["net", "onelitefeather", "ingot"]:
        return ".".join(parts[:4])
    return None


def domain_of_import(name):
    """Returns the domain an imported name belongs to, or None. A class directly in the root package is 'com.reposilite'."""
    parts = name.split(".")
    if parts[:2] == ["com", "reposilite"]:
        if len(parts) >= 3 and parts[2][:1].islower():
            return ".".join(parts[:3])
        return "com.reposilite"
    if len(parts) >= 4 and parts[:3] == ["net", "onelitefeather", "ingot"] and parts[3][:1].islower():
        return ".".join(parts[:4])
    return None


def index_sources(root):
    """Reads every Kotlin source of the three source sets. The package is the directory below the source root."""
    files = []
    for source_set, relative in SOURCE_SETS.items():
        base = os.path.join(root, relative)
        if not os.path.isdir(base):
            continue
        for directory, subdirectories, names in os.walk(base):
            subdirectories.sort()
            for name in sorted(names):
                if not name.endswith(".kt"):
                    continue
                inside = os.path.relpath(directory, base)
                package = "" if inside == "." else inside.replace(os.sep, ".")
                with open(os.path.join(directory, name), encoding="utf-8") as handle:
                    text = handle.read()
                files.append(SourceFile(
                    source_set=source_set,
                    package=package,
                    name=name,
                    domain=domain_of_package(package),
                    annotations=len(ANNOTATION.findall(text)),
                    imports=IMPORT.findall(text),
                ))
    return files


def direct_importers(files):
    """Maps each domain to the set of domains whose sources import it directly."""
    known = {f.domain for f in files if f.domain}
    importers = defaultdict(set)
    for source in files:
        if not source.domain:
            continue
        for name in source.imports:
            target = domain_of_import(name)
            if target in known and target != source.domain:
                importers[target].add(source.domain)
    return importers


def smoke_filters(files):
    """Returns (source_set, fully qualified name) for each smoke class. A missing or doubled class is an error."""
    resolved = []
    for class_name in SMOKE_CLASSES:
        matches = [f for f in files if f.name == f"{class_name}.kt" and f.source_set in ("test", "integration")]
        if len(matches) != 1:
            raise SelectionError(f"smoke class {class_name} is found {len(matches)} times, expected exactly once")
        match = matches[0]
        resolved.append((match.source_set, f"{match.package}.{class_name}"))
    return resolved


def kotlin_package(path):
    """Returns (root, package) of a changed path under a Kotlin source root, or (None, None)."""
    for root in KOTLIN_ROOTS:
        if path.startswith(root + "/"):
            directory = os.path.dirname(path[len(root) + 1:])
            return root, directory.replace("/", ".")
    return None, None


def decide(root, changed_paths):
    """Returns the Decision for the changed paths of a pull request, read from the tree under root."""
    relevant = []
    for raw in changed_paths:
        path = raw.strip()
        if path and not DENIED.search(path):
            relevant.append(path)
    if not relevant:
        return Decision("full", "no changed path outside the documentation and metadata list")

    changed_domains = set()
    for path in relevant:
        kotlin_root, package = kotlin_package(path)
        if kotlin_root is None or not path.endswith(".kt"):
            return Decision("full", f"{path} is outside the backend Kotlin sources")
        domain = domain_of_package(package)
        if domain is None:
            return Decision("full", f"{path} is in package {package or '(default)'}, which the script does not map")
        if domain in INFRASTRUCTURE:
            return Decision("full", f"{path} is in the infrastructure package {domain}")
        changed_domains.add(domain)

    files = index_sources(root)
    smoke = smoke_filters(files)
    importers = direct_importers(files)
    selected = set(changed_domains)
    for domain in changed_domains:
        selected |= importers.get(domain, set())

    selected_annotations = sum(f.annotations for f in files if f.domain in selected)
    total_annotations = sum(f.annotations for f in files if f.source_set in ("test", "integration"))
    if selected_annotations == 0:
        return Decision("full", "no test is reachable from the changed packages")
    if selected_annotations / total_annotations > THRESHOLD:
        return Decision(
            "full",
            f"the selection holds {selected_annotations} of {total_annotations} test annotations, above {THRESHOLD:.0%}",
        )

    test_filters = []
    integration_filters = []
    for domain in sorted(selected):
        if any(f.domain == domain and f.source_set == "test" and f.annotations for f in files):
            test_filters.append(f"{domain}.*")
        if any(f.domain == domain and f.source_set == "integration" and f.annotations for f in files):
            integration_filters.append(f"{domain}.*")
    for source_set, name in smoke:
        target = test_filters if source_set == "test" else integration_filters
        if name not in target:
            target.append(name)

    reason = (
        f"{len(changed_domains)} changed package(s), {len(selected)} selected, "
        f"{selected_annotations} of {total_annotations} test annotations"
    )
    return Decision("partial", reason, sorted(selected), test_filters, integration_filters)


def render(decision):
    lines = [f"mode={decision.mode}", f"reason={decision.reason}"]
    if decision.mode == "partial":
        lines += [f"package={domain}" for domain in decision.selected]
        lines += [f"test={pattern}" for pattern in decision.test_filters]
        lines += [f"integration={pattern}" for pattern in decision.integration_filters]
    return "\n".join(lines) + "\n"


def main(argv):
    root = "."
    if len(argv) == 2 and argv[0] == "--root":
        root = argv[1]
    elif argv:
        sys.stderr.write("usage: select_backend_tests.py [--root DIR] < changed-paths.txt\n")
        return 2

    changed = sys.stdin.read().splitlines()
    try:
        decision = decide(root, changed)
    except (SelectionError, OSError, UnicodeDecodeError) as error:
        decision = Decision("full", f"script error, running the full suite: {error}")
    sys.stdout.write(render(decision))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
