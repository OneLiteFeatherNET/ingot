---
id: systemd
title: Systemd
---

You can launch Ingot as a service using [systemd](https://en.wikipedia.org/wiki/Systemd).
Here is an example configuration of `/etc/systemd/system/ingot.service` file:

```json5
[Unit]
Description=Ingot Service

[Service]
# Non-root user
User=ingot
# Ingot workspace directory
WorkingDirectory=/opt/ingot
# Path to Ingot executable/script and its configuration.
ExecStart=java -jar ingot.jar --local-configuration=/etc/ingot/configuration.cdn --working-directory=/opt/ingot
# Policy
SuccessExitStatus=0
TimeoutStopSec=10
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

~ Associated issue on GitHub: [GH-468 Service file for Linux environments](https://github.com/dzikoysk/reposilite/issues/468)
