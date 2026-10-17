---
description: Run the Ingot JAR as a systemd service with a dedicated user, the working directory, restart on failure, and the steps to upgrade the versioned JAR.
---

# Run as a service

This page runs the Ingot JAR under systemd, so that it starts at boot and restarts after a failure. It assumes that the JAR runs by hand, as described in [Run the JAR](install-jar.md).

## Before you begin

* A Linux host with systemd and Java 17 or newer.
* The JAR in the data directory, `/opt/ingot/ingot-<version>.jar`.
* Root access for the installation steps.

## Steps

1. Create a system user that owns the data directory:

   ```bash
   sudo useradd --system --home /opt/ingot --shell /usr/sbin/nologin ingot
   sudo chown -R ingot:ingot /opt/ingot
   ```

2. Create the unit file `/etc/systemd/system/ingot.service`:

   ```ini
   [Unit]
   Description=Ingot repository server
   After=network-online.target
   Wants=network-online.target

   [Service]
   User=ingot
   Group=ingot
   WorkingDirectory=/opt/ingot
   ExecStart=/usr/bin/java -Xmx512m -jar /opt/ingot/ingot-<version>.jar --working-directory=/opt/ingot --disable-interactive-console
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

   Replace `/usr/bin/java` with the output of `command -v java` if Java lives elsewhere. Set `-Xmx` for the memory you can give to Ingot.

3. Load the unit and start the service:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now ingot
   ```

4. Read the status and the log:

   ```bash
   systemctl status ingot
   journalctl -u ingot -n 50
   ```

## Upgrade the JAR

The JAR name contains the version, so each upgrade changes the `ExecStart` line:

1. Stop the service and [back up](back-up-before-upgrading.md) the data directory.
2. Copy the new `ingot-<version>.jar` into `/opt/ingot` and change the name in `ExecStart`.
3. Run `sudo systemctl daemon-reload` and `sudo systemctl restart ingot`.
4. Check the result with the steps in [Verify an upgrade](verify-an-upgrade.md).

To return to the previous version, restore the old file name in `ExecStart` and restart. [Roll back an upgrade](roll-back-an-upgrade.md) describes the data side.

## Verify

* `systemctl is-active ingot` prints `active`.
* `curl -f http://localhost:8080/api/status/health` returns `200 OK`.
* The logs are in `/opt/ingot/logs/`. The service uses that directory as its working directory, so the rolling log files are written there.

## Troubleshooting

* The service restarts in a loop: read `journalctl -u ingot`. A port that is already in use or a data directory owned by another user are the usual causes.
* `Permission denied` on a port below 1024: Ingot cannot bind it as user `ingot`. Use a port above 1023, or let a proxy listen on the low port. See [Run behind a reverse proxy](run-behind-reverse-proxy.md).

## Related

* [Run the JAR](install-jar.md)
* [Enable TLS](enable-tls.md)
