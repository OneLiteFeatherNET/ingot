/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.onelitefeather.ingot.identity.corpus

import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.Role

private const val ADMIN_ALL = "ingot.admin.*"
private const val ARTIFACT_NET_ONELITEFEATHER_READ = "ingot.artifact.net.onelitefeather.read"

/** Requests decided by `allows` and `decidePath`: legacy routes, path grants, roles, admins, token narrowing and path variants. */
internal fun requestRows(): List<CorpusRow> = legacyAndGrantRows() + tokenAndAdminRows() + pathVariantRows() + decidePathRows()

/** Only the artifact subtree of the principal, the fixture of the path variant rows. */
private fun artifactReader() = subjectOf(own(listOf(allow(ARTIFACT_NET_ONELITEFEATHER_READ))))

private fun legacyAndGrantRows(): List<CorpusRow> = listOf(
    // Legacy routes: a plain, case-insensitive prefix with no segment boundary.
    allowsRow(
        "request.legacy-route-prefix-is-unbounded", "legacy route /releases/com/example READ",
        { subjectOf(team(grants = listOf(legacy("/releases/com/example", Role.LEGACY_READ)))) },
        "/releases/com/examples/y.jar", Action.READ, true,
        note = "legacy routes match plain prefixes, a sibling directory that starts the same way is covered",
    ),
    allowsRow(
        "request.legacy-route-matches-case-insensitively", "legacy route /releases READ",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_READ)))) },
        "/RELEASES/com/x", Action.READ, true,
    ),
    allowsRow(
        "request.legacy-route-not-covering-its-parent", "legacy route /releases/com READ",
        { subjectOf(team(grants = listOf(legacy("/releases/com", Role.LEGACY_READ)))) },
        "/releases", Action.READ, false,
    ),
    allowsRow(
        "request.legacy-route-matches-name-prefix", "legacy route /releases READ",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_READ)))) },
        "/releasesX/a", Action.READ, true,
    ),
    allowsRow(
        "request.legacy-write-includes-delete", "legacy route /releases LEGACY_WRITE",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_WRITE)))) },
        "/releases/x", Action.DELETE, true,
        note = "LEGACY_WRITE includes DELETE because the old code deleted under WRITE",
    ),
    allowsRow(
        "request.legacy-write-excludes-read", "legacy route /releases LEGACY_WRITE",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_WRITE)))) },
        "/releases/x", Action.READ, false,
    ),
    allowsRow(
        "request.legacy-read-excludes-write", "legacy route /releases LEGACY_READ",
        { subjectOf(team(grants = listOf(legacy("/releases", Role.LEGACY_READ)))) },
        "/releases/x", Action.WRITE, false,
    ),
    allowsRow(
        "request.legacy-root-covers-every-path", "legacy route / LEGACY_READ",
        { subjectOf(team(grants = listOf(legacy("/", Role.LEGACY_READ)))) },
        "/anything/at/all", Action.READ, true,
    ),
    // Path grants of the new model: whole segments, case-sensitive, within one repository.
    allowsRow(
        "request.path-grant-is-segment-bounded", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "/releases/com/examples/y.jar", Action.READ, false,
    ),
    allowsRow(
        "request.path-grant-covers-its-directory", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "/releases/com/example", Action.READ, true,
    ),
    allowsRow(
        "request.path-grant-prefix-is-case-sensitive", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "/releases/COM/example/x.jar", Action.READ, false,
    ),
    allowsRow(
        "request.path-grant-covers-nested-path", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "/releases/com/example/a/b.jar", Action.READ, true,
    ),
    allowsRow(
        "request.path-grant-other-repository", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "/snapshots/com/example/x", Action.READ, false,
    ),
    allowsRow(
        "request.repository-grant-covers-every-path", "READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) },
        "/releases/a/b/c.jar", Action.READ, true,
    ),
    allowsRow(
        "request.grant-without-repository-covers-every-repository", "READ on every repository",
        { subjectOf(team(grants = listOf(grant(Role.READ)))) },
        "/snapshots/x", Action.READ, true,
    ),
    allowsRow(
        "request.deploy-role-includes-read", "DEPLOY on releases",
        { subjectOf(team(grants = listOf(grant(Role.DEPLOY, "releases")))) },
        "/releases/x", Action.READ, true,
    ),
    allowsRow(
        "request.deploy-role-excludes-delete", "DEPLOY on releases",
        { subjectOf(team(grants = listOf(grant(Role.DEPLOY, "releases")))) },
        "/releases/x", Action.DELETE, false,
    ),
    allowsRow(
        "request.maintain-role-includes-delete", "MAINTAIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.MAINTAIN, "releases")))) },
        "/releases/x", Action.DELETE, true,
    ),
    allowsRow(
        "request.admin-role-manages-repository", "ADMIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.ADMIN, "releases")))) },
        "/releases/x", Action.MANAGE_REPOSITORY, true,
    ),
    allowsRow(
        "request.admin-role-is-not-instance-admin", "ADMIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.ADMIN, "releases")))) },
        "/", Action.INSTANCE_ADMIN, false,
    ),
)

private fun tokenAndAdminRows(): List<CorpusRow> = listOf(
    allowsRow(
        "request.disabled-denies-everything", "disabled, own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), disabled = true) }, "/releases/x", Action.READ, false,
    ),
    allowsRow(
        "request.disabled-denies-instance-admin", "disabled, own allow ingot.admin.*",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), disabled = true) }, "/", Action.INSTANCE_ADMIN, false,
    ),
    allowsRow(
        "request.instance-admin-ignores-repository-denial", "own allow ingot.admin.*, deny releases.read",
        { subjectOf(own(listOf(allow(ADMIN_ALL), deny("ingot.repository.releases.read")))) },
        "/releases/x", Action.READ, true,
        note = "known surprise: an instance admin is not restricted by a repository denial",
    ),
    allowsRow(
        "request.instance-admin-ignores-artifact-denial", "own allow ingot.admin.*, deny artifact net.onelitefeather.read",
        { subjectOf(own(listOf(allow(ADMIN_ALL), deny(ARTIFACT_NET_ONELITEFEATHER_READ)))) },
        "/releases/net/onelitefeather/x.jar", Action.READ, true,
        note = "known surprise: an instance admin is not restricted by an artifact denial either",
    ),
    allowsRow(
        "request.instance-admin-narrowed-by-token", "own allow ingot.admin.*, token lists repository reads",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow("ingot.repository.*.read"))) },
        "/releases/x", Action.WRITE, false,
    ),
    allowsRow(
        "request.instance-admin-token-without-admin-node-loses-shortcut", "own allow ingot.admin.*, token lists repository reads",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow("ingot.repository.*.read"))) },
        "/releases/x", Action.READ, false,
        note = "a token that does not list the admin node turns the instance admin shortcut off, and the owner has no" +
            " repository rule of its own, so the read is denied",
    ),
    allowsRow(
        "request.instance-admin-token-with-admin-node-reads", "own allow ingot.admin.*, token lists ingot.admin.* and repository reads",
        { subjectOf(own(listOf(allow(ADMIN_ALL))), token = tokenOf(allow(ADMIN_ALL), allow("ingot.repository.*.read"))) },
        "/releases/x", Action.READ, true,
    ),
    allowsRow(
        "request.token-narrows-owner-grant", "READ on releases, token lists snapshots.read",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), token = tokenOf(allow("ingot.repository.snapshots.read"))) },
        "/releases/x", Action.READ, false,
    ),
    allowsRow(
        "request.token-cannot-widen-owner", "own empty, token lists releases.read",
        { subjectOf(own(), token = tokenOf(allow("ingot.repository.releases.read"))) },
        "/releases/x", Action.READ, false,
    ),
    allowsRow(
        "request.token-without-node-list-narrows-nothing", "READ on releases, no token list",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases")))) },
        "/releases/x", Action.READ, true,
    ),
    allowsRow(
        "request.token-list-denies-unlisted-repository", "own allow *.read, token lists releases.read",
        { subjectOf(own(listOf(allow("ingot.repository.*.read"))), token = tokenOf(allow("ingot.repository.releases.read"))) },
        "/snapshots/x", Action.READ, false,
        note = "a token list is a whitelist: a node it does not list is denied",
    ),
    allowsRow(
        "request.token-denial-narrows-owner", "own allow *.read, token denies releases.read",
        { subjectOf(own(listOf(allow("ingot.repository.*.read"))), token = tokenOf(deny("ingot.repository.releases.read"))) },
        "/releases/x", Action.READ, false,
    ),
)

/**
 * Path variants. Each variant has a raw row (what an upload passes: `ctx.uri()`, see ContextDsl) and a normalized row (what a
 * read passes: the `Location` string). Both are recorded against the artifact reader; the raw rows describe the evaluator
 * input today and do not change when the canonical path is introduced, because uploads move to it outside the evaluator.
 */
private fun pathVariantRows(): List<CorpusRow> = listOf(
    allowsRow(
        "path.encoded-slash.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net%2Fonelitefeather%2Fx.jar", Action.READ, false,
        note = "upload form: the encoded slash stays one segment and is not inside the artifact subtree",
    ),
    allowsRow(
        "path.encoded-slash.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/x.jar", Action.READ, true,
        note = "read form of the same file",
    ),
    allowsRow(
        "path.encoded-dot.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/%2e%2e/secret.jar", Action.READ, true,
        note = "known surprise: an encoded dot segment is a literal segment and sits inside the artifact subtree",
    ),
    allowsRow(
        "path.encoded-dot.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/../secret.jar", Action.READ, false,
        note = "the decoded form holds a parent segment, which cannot be normalised",
    ),
    allowsRow(
        "path.dot-segment.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/other/../onelitefeather/x.jar", Action.READ, false,
        note = "a parent segment that cannot be normalised is covered by no artifact node",
    ),
    allowsRow(
        "path.dot-segment.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/x.jar", Action.READ, true,
        note = "the same file after the dot segment is resolved",
    ),
    allowsRow(
        "path.dot-segment-escape.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/../../x.jar", Action.READ, false,
    ),
    allowsRow(
        "path.dot-segment-escape.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/x.jar", Action.READ, false,
    ),
    allowsRow(
        "path.double-slash.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases//net/onelitefeather/x.jar", Action.READ, true,
    ),
    allowsRow(
        "path.double-slash.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/x.jar", Action.READ, true,
    ),
    allowsRow(
        "path.mixed-case-artifact.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/Net/onelitefeather/x.jar", Action.READ, false,
        note = "artifact groups compare case-sensitively",
    ),
    allowsRow(
        "path.mixed-case-artifact.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/Net/onelitefeather/x.jar", Action.READ, false,
        note = "normalisation keeps the case, so the read form is judged the same way",
    ),
    allowsRow(
        "path.mixed-case-repository.raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "/Releases/net/onelitefeather/x.jar", Action.READ, true,
        note = "an unqualified artifact node applies to every repository, whatever its case",
    ),
    allowsRow(
        "path.mixed-case-repository.with-repository-node", "own allow releases.read",
        { subjectOf(own(listOf(allow("ingot.repository.releases.read")))) }, "/Releases/x", Action.READ, false,
        note = "repository names compare case-sensitively in repository nodes",
    ),
    allowsRow(
        "path.trailing-slash.normalized", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases/net/onelitefeather/", Action.READ, true,
        note = "the location drops a trailing slash",
    ),
    allowsRow(
        "path.repository-root-not-covered-by-artifact", "artifact net.onelitefeather.read",
        { artifactReader() }, "/releases", Action.READ, false,
    ),
)

private fun decidePathRows(): List<CorpusRow> = listOf(
    decidePathRow(
        "decide-path.grant-prefix-bounded", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "releases", "com/example/x.jar", "read", "true GRANT -",
    ),
    decidePathRow(
        "decide-path.grant-prefix-bounded-denied", "READ on releases under com/example",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases", "com/example")))) },
        "releases", "com/examples/x.jar", "read", "false - -",
    ),
    decidePathRow(
        "decide-path.encoded-slash-raw", "artifact net.onelitefeather.read",
        { artifactReader() }, "releases", "net%2Fonelitefeather%2Fx.jar", "read", "false - -",
    ),
    decidePathRow(
        "decide-path.artifact-subtree", "artifact net.onelitefeather.read",
        { artifactReader() }, "releases", "net/onelitefeather/x.jar", "read",
        "true ARTIFACT ingot.artifact.net.onelitefeather.read",
    ),
    decidePathRow(
        "decide-path.deploy-not-covered-by-read-artifact", "artifact net.onelitefeather.read",
        { artifactReader() }, "releases", "net/onelitefeather/x.jar", "deploy", "false - -",
    ),
    decidePathRow(
        "decide-path.unknown-action-refused", "artifact net.onelitefeather.read",
        { artifactReader() }, "releases", "x.jar", "list", "threw IllegalArgumentException",
        note = "only the repository actions read, deploy, delete and manage are decided",
    ),
    decidePathRow(
        "decide-path.manage-with-admin-role", "ADMIN on releases",
        { subjectOf(team(grants = listOf(grant(Role.ADMIN, "releases")))) }, "releases", "x.jar", "manage", "true GRANT -",
    ),
    decidePathRow(
        "decide-path.disabled-subject", "disabled, READ on releases",
        { subjectOf(team(grants = listOf(grant(Role.READ, "releases"))), disabled = true) },
        "releases", "x.jar", "read", "false - -",
    ),
)
