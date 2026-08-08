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
