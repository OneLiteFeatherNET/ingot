#!/usr/bin/env sh

# shellcheck disable=SC2086
set -e

# INGOT_OPTS is the name to use going forward; REPOSILITE_OPTS stays supported so an
# existing compose file or Kubernetes manifest keeps working after switching images.
INGOT_ARGS="${INGOT_OPTS:-$REPOSILITE_OPTS}"
case "$INGOT_ARGS" in
  *"--working-directory"*) ;;
  *"-wd"*                ) ;;
  *                      ) INGOT_ARGS="--working-directory=/app/data $INGOT_ARGS";;
esac

# Extra certificate authorities. INGOT_EXTRA_CA_CERTS (REPOSILITE_EXTRA_CA_CERTS as fallback) names a directory of PEM
# files. Every certificate in them is imported into a copy of the JDK trust store, and the JVM is pointed at that copy,
# so the public authorities stay trusted. When neither variable is set this returns before touching anything. A value
# that cannot be honoured stops the start with exit code 1 and one line that names the variable.

EXTRA_CA_NAME=""
EXTRA_CA_DIR=""

extra_ca_fail() {
  printf "%s: %s\n" "$EXTRA_CA_NAME" "$1" 1>&2
  exit 1
}

import_extra_ca_certificates() {
  if [ -z "${INGOT_EXTRA_CA_CERTS:-}" ] && [ -z "${REPOSILITE_EXTRA_CA_CERTS:-}" ]; then
    return 0
  fi
  if [ -n "${INGOT_EXTRA_CA_CERTS:-}" ]; then
    EXTRA_CA_NAME=INGOT_EXTRA_CA_CERTS
    EXTRA_CA_DIR="$INGOT_EXTRA_CA_CERTS"
  else
    EXTRA_CA_NAME=REPOSILITE_EXTRA_CA_CERTS
    EXTRA_CA_DIR="$REPOSILITE_EXTRA_CA_CERTS"
  fi

  case "${JAVA_OPTS:-}" in
    *javax.net.ssl.trustStore*) extra_ca_fail "JAVA_OPTS already sets javax.net.ssl.trustStore, so the bundle cannot be imported" ;;
  esac
  [ -d "$EXTRA_CA_DIR" ] || extra_ca_fail "$EXTRA_CA_DIR is not a directory"
  [ -r "$EXTRA_CA_DIR" ] && [ -x "$EXTRA_CA_DIR" ] || extra_ca_fail "$EXTRA_CA_DIR is not readable"
  command -v keytool >/dev/null 2>&1 || extra_ca_fail "keytool is not available in this image"

  jdk_store="$JAVA_HOME/lib/security/cacerts"
  [ -r "$jdk_store" ] || extra_ca_fail "the JDK trust store $jdk_store is missing"

  # A private directory per start, readable by the service account after the privilege drop and written by nothing else.
  work=$(mktemp -d "${TMPDIR:-/tmp}/ingot-ca.XXXXXX") || extra_ca_fail "cannot create a directory for the trust store copy under ${TMPDIR:-/tmp}"
  chmod 755 "$work"
  copy="$work/cacerts"
  cp "$jdk_store" "$copy" && chmod 644 "$copy" || extra_ca_fail "cannot copy the JDK trust store to $copy"

  # Dot entries (..data and its siblings) are skipped by the glob, and a symbolic link to a file is followed by -f.
  total=0
  imported=""
  for file in "$EXTRA_CA_DIR"/*; do
    [ -f "$file" ] || continue
    [ -r "$file" ] || extra_ca_fail "cannot read $file"
    base=$(basename "$file")
    pem_dir="$work/pem"
    rm -rf "$pem_dir" && mkdir -p "$pem_dir"
    # keytool imports one certificate per call, so the chain in a file is split into one file per certificate.
    count=$(awk -v dir="$pem_dir" '
      /^-----BEGIN CERTIFICATE-----/ { n++; out = dir "/" n ".pem"; incert = 1 }
      incert { print > out }
      /^-----END CERTIFICATE-----/ { incert = 0; close(out) }
      END { print n + 0 }
    ' "$file") || extra_ca_fail "cannot split $file"
    [ "$count" -gt 0 ] || extra_ca_fail "no certificate found in $file"
    i=1
    while [ "$i" -le "$count" ]; do
      keytool -importcert -noprompt -alias "ingot-extra-$base-$i" -file "$pem_dir/$i.pem" \
        -keystore "$copy" -storepass changeit </dev/null >/dev/null 2>&1 \
        || extra_ca_fail "keytool rejected a certificate in $file"
      i=$((i + 1))
    done
    total=$((total + count))
    imported="$imported $base"
  done
  rm -rf "$work/pem"

  [ "$total" -gt 0 ] || extra_ca_fail "no certificate found in $EXTRA_CA_DIR"
  printf "%s\n" "$EXTRA_CA_NAME: imported $total certificate(s) from $EXTRA_CA_DIR (${imported# }) into $copy" 1>&2
  JAVA_OPTS="${JAVA_OPTS:-} -Djavax.net.ssl.trustStore=$copy -Djavax.net.ssl.trustStorePassword=changeit"
}

import_extra_ca_certificates

# GH-1762: support for running as non-root user

if [ "$(id -u)" != 0 ]; then
  exec java \
       -Dtinylog.writerFile.file="/var/log/reposilite/log_{date}.txt" \
       -Dtinylog.writerFile.latest=/var/log/reposilite/latest.log \
       $JAVA_OPTS \
       -jar ingot.jar \
       $INGOT_ARGS
# GH-1200: run as non-root user
else

  # GH-1634: support custom user and group ids
  #
  # Unlike the upstream image, this one ships the account pre-created, so the "does it
  # exist" guards below no longer decide anything on their own: without the adjusting
  # branches, PUID and PGID would be read and then silently ignored, and a deployment that
  # relies on them would come up owning nothing it can write to.
  GROUP_ID="${PGID:-977}"
  if ! grep -q "^reposilite" /etc/group;
  then
    addgroup --gid "$GROUP_ID" reposilite;
  elif [ "$(getent group reposilite | cut -d: -f3)" != "$GROUP_ID" ];
  then
    groupmod --gid "$GROUP_ID" reposilite;
  fi
  USER_ID="${PUID:-977}"
  if ! grep "^reposilite" /etc/passwd;
  then
    adduser --system -uid "$USER_ID" --ingroup reposilite --shell /bin/sh reposilite;
  elif [ "$(id -u reposilite)" != "$USER_ID" ] || [ "$(id -g reposilite)" != "$GROUP_ID" ];
  then
    # Both ids are passed together: a changed group id alone already leaves the account
    # pointing at a group that no longer exists under that number.
    usermod --uid "$USER_ID" --gid "$GROUP_ID" reposilite;
  fi

  # GH-2288: Dockerfile Step-1 migration warning
  # shellcheck disable=SC2012
  existing_uid=$(ls -dln data | awk '{print $3}')
  # shellcheck disable=SC2012
  existing_gid=$(ls -dln data | awk '{print $4}')
  if [ "$existing_uid" != "$USER_ID" ] || [ "$existing_gid" != "$GROUP_ID" ]; then
    printf "\033[1;31mThe data directory is owned by %s:%s, the server runs as %s:%s, so ownership is being changed now.\033[0m\n" "$existing_uid" "$existing_gid" "$USER_ID" "$GROUP_ID" 1>&2
    printf "\033[1;31mSet PUID and PGID if you need a different owner. Reposilite images before 3.5.20 used 999, which stops working after 3.6.0.\033[0m\n" 1>&2
    printf "\033[1;31mFor more information see: https://github.com/dzikoysk/reposilite/issues/2288\033[0m\n" 1>&2
    printf "\033[1;31mIF YOU DOWNGRADE PAST THIS POINT \"Hic sunt dracones\"\033[0m\n" 1>&2
  fi

  # GH-2457: skip chown if ownership already matches target UID/GID
  FORCE_CHOWN="${INGOT_FORCE_CHOWN:-$REPOSILITE_FORCE_CHOWN}"
  if [ "$FORCE_CHOWN" = "true" ] || [ "$existing_uid" != "$USER_ID" ] || [ "$existing_gid" != "$GROUP_ID" ]; then
    chown -R reposilite:reposilite /app
    chown -R reposilite:reposilite /var/log/reposilite
  fi

  exec runuser -u reposilite -- \
    java \
       -Dtinylog.writerFile.file="/var/log/reposilite/log_{date}.txt" \
       -Dtinylog.writerFile.latest=/var/log/reposilite/latest.log \
       $JAVA_OPTS \
       -jar ingot.jar \
       $INGOT_ARGS
fi
