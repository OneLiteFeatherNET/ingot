#!/usr/bin/env bash
# Publishes the official plugin jars and their signed catalogue to the official Maven repository.
#
# The plugins job of .github/workflows/release-please.yml calls one subcommand per step. The signing key and its
# passphrase arrive as environment variables from the plugin-release environment. Nothing here prints, echoes or traces a
# secret, passes one as a command-line argument, or writes one into the checkout. The signing keyring lives in
# PLUGIN_SIGNING_HOME, which the workflow deletes in an always() step.
#
# Subcommands, in the order the job runs them:
#   check-config    the secrets and variables are present and well formed; stops before anything is uploaded
#   stage           copies the built jar into the Maven layout of the local mirror
#   sign-jars       imports the key, signs every staged jar and checks each signature against the key id
#   check-bundled   compares the jar bundled in the release image with the staged jar
#   fetch           downloads the listed catalogue into the mirror (absent when the repository has none yet)
#   generate        runs generate_plugin_catalogue.py on the mirror
#   sign-catalogue  signs the catalogue with the same key
#   upload          uploads jars and signatures first and the catalogue last; skips what is already published
#   verify          downloads the published files and checks digests and signatures with the public key only
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
# Every scratch file lives under the runner's temporary directory, which the job removes with its runner.
: "${RUNNER_TEMP:?RUNNER_TEMP is not set}"
readonly GROUP_PATH="net/onelitefeather/ingot"
readonly PLUGIN="opensearch-plugin"
readonly BUILT_JAR="reposilite-test/workspace/plugins/${PLUGIN}.jar"
readonly IMAGE="ghcr.io/onelitefeathernet/ingot"
readonly CATALOGUE="ingot-plugin-catalogue.json"
readonly KEY_ID_PATTERN="^[0-9A-F]{40}$"

die() {
    echo "error: $*" >&2
    exit 1
}

require_var() {
    # Names the variable only. The value is never shown.
    if [ -z "${!1:-}" ]; then
        die "$1 is not set"
    fi
}

signing_key_id() {
    require_var PLUGIN_SIGNING_KEY_ID
    local key_id
    key_id="$(printf '%s' "$PLUGIN_SIGNING_KEY_ID" | tr -d ' ' | tr '[:lower:]' '[:upper:]')"
    if ! [[ "$key_id" =~ $KEY_ID_PATTERN ]]; then
        die "PLUGIN_SIGNING_KEY_ID must be a 40 character OpenPGP fingerprint"
    fi
    printf '%s' "$key_id"
}

# The bearer token is written to curl through a pipe. printf is a shell builtin, so it never appears in the process list.
auth_config() {
    printf 'header = "Authorization: Bearer %s"\n' "${PLUGIN_REPOSITORY_TOKEN:?PLUGIN_REPOSITORY_TOKEN is not set}"
}

# Downloads with the repository credential. Prints the HTTP status and leaves the body in the destination.
http_get_authenticated() {
    curl --silent --show-error --output "$2" --write-out '%{http_code}' --config <(auth_config) "$1"
}

# Downloads without any credential, as an anonymous reader would.
http_get_anonymous() {
    curl --silent --show-error --output "$2" --write-out '%{http_code}' "$1"
}

put_authenticated() {
    curl --fail --silent --show-error --config <(auth_config) --upload-file "$1" "$2"
}

sha256_of() {
    sha256sum "$1" | cut -d ' ' -f 1
}

# Imports the signing key into PLUGIN_SIGNING_HOME and checks that its primary fingerprint is the expected key id.
# Importing again is harmless, so each signing step calls this itself.
import_signing_key() {
    require_var PLUGIN_SIGNING_KEY
    require_var PLUGIN_SIGNING_HOME
    local expected fingerprint
    expected="$(signing_key_id)"
    mkdir -p -m 700 "$PLUGIN_SIGNING_HOME"
    if ! printf '%s\n' "$PLUGIN_SIGNING_KEY" | gpg --batch --quiet --homedir "$PLUGIN_SIGNING_HOME" --import >/dev/null 2>&1; then
        die "the PLUGIN_SIGNING_KEY secret cannot be imported as an OpenPGP key"
    fi
    fingerprint="$(gpg --batch --homedir "$PLUGIN_SIGNING_HOME" --with-colons --list-secret-keys \
        | awk -F: '$1 == "fpr" { print $10; exit }')"
    if [ "$fingerprint" != "$expected" ]; then
        die "the imported key is not the key of PLUGIN_SIGNING_KEY_ID"
    fi
    SIGNING_FINGERPRINT="$fingerprint"
}

# Checks that a signature was made by the expected primary key. VALIDSIG ends with the primary fingerprint.
signature_is_from() {
    local home="$1" fingerprint="$2" signature="$3" file="$4" status_file
    status_file="$(mktemp "$RUNNER_TEMP/gpg-status.XXXXXX")"
    if ! gpg --batch --homedir "$home" --status-fd 1 --verify "$signature" "$file" >"$status_file" 2>/dev/null; then
        rm -f "$status_file"
        return 1
    fi
    local matched=0
    grep -E "^\[GNUPG:\] VALIDSIG .* ${fingerprint}\$" "$status_file" >/dev/null && matched=1
    rm -f "$status_file"
    [ "$matched" -eq 1 ]
}

sign_file() {
    local file="$1"
    printf '%s' "${PLUGIN_SIGNING_PASSPHRASE:-}" | gpg --batch --yes --quiet --homedir "$PLUGIN_SIGNING_HOME" \
        --pinentry-mode loopback --passphrase-fd 0 --local-user "$SIGNING_FINGERPRINT" \
        --armor --detach-sign --output "$file.asc" "$file"
    if ! signature_is_from "$PLUGIN_SIGNING_HOME" "$SIGNING_FINGERPRINT" "$file.asc" "$file"; then
        die "the signature of $(basename "$file") does not verify against the signing key"
    fi
}

staged_jar() {
    printf '%s/%s/%s/%s/%s-%s.jar' "$PLUGIN_STAGE" "$GROUP_PATH" "$PLUGIN" "$VERSION" "$PLUGIN" "$VERSION"
}

cmd_check_config() {
    require_var VERSION
    require_var PLUGIN_SIGNING_KEY
    signing_key_id >/dev/null
    if [ -n "${PLUGIN_REPOSITORY_URL:-}" ]; then
        case "$PLUGIN_REPOSITORY_URL" in
            https://*) ;;
            *) die "PLUGIN_REPOSITORY_URL must be an https URL" ;;
        esac
        require_var PLUGIN_REPOSITORY_TOKEN
    fi
    echo "configuration complete"
}

cmd_stage() {
    require_var VERSION
    require_var PLUGIN_STAGE
    if [ ! -f "$BUILT_JAR" ]; then
        die "the built jar is missing at $BUILT_JAR"
    fi
    local target
    target="$(staged_jar)"
    mkdir -p "$(dirname "$target")"
    cp "$BUILT_JAR" "$target"
    echo "staged $target"
}

cmd_sign_jars() {
    require_var VERSION
    require_var PLUGIN_STAGE
    import_signing_key
    local jar count=0
    while IFS= read -r jar; do
        sign_file "$jar"
        count=$((count + 1))
        echo "signed $(basename "$jar") with $SIGNING_FINGERPRINT"
    done < <(find "$PLUGIN_STAGE/$GROUP_PATH" -name '*.jar' | sort)
    if [ "$count" -eq 0 ]; then
        die "no jar is staged, so there is nothing to sign"
    fi
    require_var PLUGIN_PUBLIC_KEY
    gpg --batch --homedir "$PLUGIN_SIGNING_HOME" --armor --export "$SIGNING_FINGERPRINT" > "$PLUGIN_PUBLIC_KEY"
    # The fingerprint is public. The private key never leaves the keyring.
    echo "signing key fingerprint $SIGNING_FINGERPRINT"
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        echo "- signing key fingerprint \`$SIGNING_FINGERPRINT\`" >> "$GITHUB_STEP_SUMMARY"
    fi
}

cmd_check_bundled() {
    require_var VERSION
    require_var PLUGIN_STAGE
    local staged work bundled_sha staged_sha cid
    staged="$(staged_jar)"
    work="$(mktemp -d "$RUNNER_TEMP/bundled.XXXXXX")"
    docker pull --quiet "$IMAGE:$VERSION" >/dev/null
    cid="$(docker create "$IMAGE:$VERSION")"
    docker cp "$cid:/app/bundled-plugins/$PLUGIN.jar" "$work/bundled.jar"
    docker rm "$cid" >/dev/null
    bundled_sha="$(sha256_of "$work/bundled.jar")"
    staged_sha="$(sha256_of "$staged")"
    rm -rf "$work"
    if [ "$bundled_sha" != "$staged_sha" ]; then
        die "the image bundles $PLUGIN.jar with sha256 $bundled_sha, but the released jar is $staged_sha"
    fi
    echo "the bundled $PLUGIN.jar is the released jar, sha256 $staged_sha"
}

cmd_fetch() {
    require_var PLUGIN_STAGE
    require_var PLUGIN_REMOTE_COPY
    mkdir -p "$PLUGIN_STAGE"
    if [ -z "${PLUGIN_REPOSITORY_URL:-}" ]; then
        echo "no repository configured, the listed catalogue is not fetched"
        return 0
    fi
    local status
    status="$(http_get_authenticated "$PLUGIN_REPOSITORY_URL/$CATALOGUE" "$PLUGIN_STAGE/$CATALOGUE")"
    case "$status" in
        200)
            cp "$PLUGIN_STAGE/$CATALOGUE" "$PLUGIN_REMOTE_COPY"
            echo "fetched the listed catalogue"
            ;;
        404)
            rm -f "$PLUGIN_STAGE/$CATALOGUE"
            echo "the repository has no catalogue yet, so the first one is created"
            ;;
        *) die "the listed catalogue answered HTTP $status" ;;
    esac
}

cmd_generate() {
    require_var VERSION
    require_var PLUGIN_STAGE
    local generated
    generated="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    python3 "$SCRIPT_DIR/generate_plugin_catalogue.py" publish \
        --repository "$PLUGIN_STAGE" --version "$VERSION" --generated "$generated"
}

cmd_sign_catalogue() {
    require_var PLUGIN_STAGE
    local catalogue="$PLUGIN_STAGE/$CATALOGUE"
    if [ ! -f "$catalogue" ]; then
        die "there is no catalogue to sign at $catalogue"
    fi
    import_signing_key
    sign_file "$catalogue"
    echo "signed $CATALOGUE with $SIGNING_FINGERPRINT"
}

# Adds the public key to ingot-plugin-keys.asc at the repository root. Every key ever used stays in that file, so a
# version signed by an earlier key keeps verifying. A key that is already listed is not added twice.
publish_key_file() {
    local work="$1" remote="$PLUGIN_REPOSITORY_URL/ingot-plugin-keys.asc" fingerprint status
    fingerprint="$(gpg --batch --with-colons --show-keys "$PLUGIN_PUBLIC_KEY" | awk -F: '$1 == "fpr" { print $10; exit }')"
    status="$(http_get_authenticated "$remote" "$work/keys.asc")"
    case "$status" in
        200)
            if gpg --batch --with-colons --show-keys "$work/keys.asc" \
                | awk -F: '$1 == "fpr" { print $10 }' | grep -Fx "$fingerprint" >/dev/null; then
                echo "ingot-plugin-keys.asc already lists $fingerprint"
                return 0
            fi
            cat "$PLUGIN_PUBLIC_KEY" >> "$work/keys.asc"
            ;;
        404) cp "$PLUGIN_PUBLIC_KEY" "$work/keys.asc" ;;
        *) die "ingot-plugin-keys.asc answered HTTP $status" ;;
    esac
    put_authenticated "$work/keys.asc" "$remote"
    echo "published ingot-plugin-keys.asc with $fingerprint"
}

cmd_upload() {
    require_var PLUGIN_REPOSITORY_URL
    require_var PLUGIN_STAGE
    require_var PLUGIN_PUBLIC_KEY
    local jar relative remote work status
    work="$(mktemp -d "$RUNNER_TEMP/upload.XXXXXX")"
    # Jars and their signatures first, so the catalogue never names a file that is not there yet.
    while IFS= read -r jar; do
        relative="${jar#"$PLUGIN_STAGE/"}"
        remote="$PLUGIN_REPOSITORY_URL/$relative"
        status="$(http_get_authenticated "$remote" "$work/published.jar")"
        case "$status" in
            200)
                if [ "$(sha256_of "$work/published.jar")" != "$(sha256_of "$jar")" ]; then
                    die "$relative is already published with other bytes, so it is not replaced"
                fi
                echo "already published $relative"
                ;;
            404)
                put_authenticated "$jar" "$remote"
                put_authenticated "$jar.asc" "$remote.asc"
                echo "published $relative"
                ;;
            *) die "$relative answered HTTP $status" ;;
        esac
    done < <(find "$PLUGIN_STAGE/$GROUP_PATH" -name '*.jar' | sort)
    publish_key_file "$work"
    rm -rf "$work"

    # The catalogue last, and only when it changed. An unchanged catalogue is already what the repository serves.
    if [ -f "$PLUGIN_REMOTE_COPY" ] && cmp -s "$PLUGIN_STAGE/$CATALOGUE" "$PLUGIN_REMOTE_COPY"; then
        echo "catalogue unchanged, nothing more is uploaded"
        return 0
    fi
    put_authenticated "$PLUGIN_STAGE/$CATALOGUE" "$PLUGIN_REPOSITORY_URL/$CATALOGUE"
    put_authenticated "$PLUGIN_STAGE/$CATALOGUE.asc" "$PLUGIN_REPOSITORY_URL/$CATALOGUE.asc"
    echo "published $CATALOGUE and its signature"
}

cmd_verify() {
    require_var PLUGIN_REPOSITORY_URL
    require_var PLUGIN_PUBLIC_KEY
    local expected work home fingerprint status coordinates digest group artifact version path file
    expected="$(signing_key_id)"
    work="$(mktemp -d "$RUNNER_TEMP/verify.XXXXXX")"
    home="$work/gnupg"
    mkdir -m 700 "$home"
    gpg --batch --quiet --homedir "$home" --import "$PLUGIN_PUBLIC_KEY"
    fingerprint="$(gpg --batch --homedir "$home" --with-colons --list-keys | awk -F: '$1 == "fpr" { print $10; exit }')"
    if [ "$fingerprint" != "$expected" ]; then
        die "the published public key is not the key of PLUGIN_SIGNING_KEY_ID"
    fi

    status="$(http_get_anonymous "$PLUGIN_REPOSITORY_URL/$CATALOGUE" "$work/$CATALOGUE")"
    [ "$status" = "200" ] || die "the published catalogue answered HTTP $status"
    status="$(http_get_anonymous "$PLUGIN_REPOSITORY_URL/$CATALOGUE.asc" "$work/$CATALOGUE.asc")"
    [ "$status" = "200" ] || die "the published catalogue signature answered HTTP $status"
    if ! signature_is_from "$home" "$expected" "$work/$CATALOGUE.asc" "$work/$CATALOGUE"; then
        die "the published catalogue does not verify against the published key"
    fi
    echo "catalogue signature verified"

    while IFS=$'\t' read -r coordinates digest; do
        IFS=: read -r group artifact version <<<"$coordinates"
        path="${group//.//}/$artifact/$version/$artifact-$version.jar"
        file="$work/$(basename "$path")"
        status="$(http_get_anonymous "$PLUGIN_REPOSITORY_URL/$path" "$file")"
        [ "$status" = "200" ] || die "$coordinates answered HTTP $status"
        status="$(http_get_anonymous "$PLUGIN_REPOSITORY_URL/$path.asc" "$file.asc")"
        [ "$status" = "200" ] || die "the signature of $coordinates answered HTTP $status"
        if [ "$(sha256_of "$file")" != "$digest" ]; then
            die "$coordinates does not match its catalogue sha256"
        fi
        if ! signature_is_from "$home" "$expected" "$file.asc" "$file"; then
            die "the signature of $coordinates does not verify against the published key"
        fi
        echo "verified $coordinates"
        if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
            echo "- verified \`$coordinates\`, sha256 \`$digest\`" >> "$GITHUB_STEP_SUMMARY"
        fi
    done < <(jq -r '.plugins[] | [.coordinates, .sha256] | @tsv' "$work/$CATALOGUE")
    rm -rf "$work"
}

case "${1:-}" in
    check-config | stage | sign-jars | check-bundled | fetch | generate | sign-catalogue | upload | verify)
        "cmd_${1//-/_}"
        ;;
    *)
        die "usage: publish_plugins.sh <check-config|stage|sign-jars|check-bundled|fetch|generate|sign-catalogue|upload|verify>"
        ;;
esac
