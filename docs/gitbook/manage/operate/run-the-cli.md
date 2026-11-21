---
description: How to run the ingot command line client against a running Ingot on Kubernetes, with Docker Compose and from a CI job, using a token from a file and a dedicated token for automation.
---

# Run the command line client

The [command line client](../../references/cli.md) runs console commands on a running server without a shell in the server's container. This page shows the three places it is usually run, and how to give it its own token so that the audit log names the automation and not a person.

## Create a dedicated token

Give the client a token of its own. Then the audit log shows `ci-rotation` or `operator-laptop` as the actor of each command, and revoking that one token cuts off the automation without touching anybody's login.

1. Create a machine principal for the automation in the identity administration.
2. Give it the node `ingot.admin.console`. Without it the console API answers `403`, and the client exits with code `4`.
3. Create a token for the principal, and store its value as `name:secret`.

Store the token in a file that only its owner can read, for example `chmod 600`. In Kubernetes a secret is the right place, and mount it as a file:

```bash
kubectl create secret generic ingot-cli-token --from-file=token=./ingot-cli.token
```

The client reads the first line of the file. It never reads the token from a command-line option.

## Kubernetes

Run the client as a pod next to the server, attach a terminal to it, and remove it when you leave. The manifest keeps the token in a read-only mount:

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: ingot-cli
spec:
  restartPolicy: Never
  containers:
    - name: cli
      image: ghcr.io/onelitefeathernet/ingot-cli:latest
      stdin: true
      tty: true
      env:
        - name: INGOT_URL
          value: http://ingot.ingot.svc.cluster.local:8080
        - name: INGOT_TOKEN_FILE
          value: /run/secrets/ingot/token
      volumeMounts:
        - name: token
          mountPath: /run/secrets/ingot
          readOnly: true
  volumes:
    - name: token
      secret:
        secretName: ingot-cli-token
```

```bash
kubectl apply -f ingot-cli.yaml
kubectl attach -it ingot-cli     # the interactive mode
kubectl delete pod ingot-cli
```

For a single command, use `kubectl exec` on the same pod instead of attaching, or run a one-off pod with `kubectl run ingot-cli -it --rm`. The one-off form needs the same environment and mount, so it is easier to keep the manifest.

Inside the cluster, point `INGOT_URL` at the service. A server behind an ingress with a certificate from a private CA needs `INGOT_CA_CERT` pointing at a file that holds that CA, mounted the same way as the token.

## Docker Compose

Add the client as a service in a profile, so that `docker compose up` does not start it:

```yaml
services:
  ingot:
    image: ghcr.io/onelitefeathernet/ingot:latest
    # the server configuration, as in the Docker install guide

  ingot-cli:
    image: ghcr.io/onelitefeathernet/ingot-cli:latest
    profiles: ["tools"]
    depends_on:
      - ingot
    environment:
      INGOT_URL: http://ingot:8080
      INGOT_TOKEN_FILE: /run/secrets/ingot-cli-token
    secrets:
      - ingot-cli-token
    stdin_open: true
    tty: true

secrets:
  ingot-cli-token:
    file: ./secrets/ingot-cli-token
```

Run one command, or the interactive mode:

```bash
docker compose run --rm ingot-cli exec status
docker compose --profile tools run --rm ingot-cli
```

The client runs as user `10001`. A token file that only your own user can read is not readable inside the container, so make the file readable by that user, or give the container your uid with `user: "<your uid>"` in the service.

## CI

A job that must fail when a command fails should use `exec`, which sets the exit code, and should pass `--yes` only for a command the job means to run. The token comes from the CI secret store, written to a file for the duration of the job:

```yaml
- name: Check the server
  env:
    INGOT_URL: https://ingot.example.org
    INGOT_TOKEN: ${{ secrets.INGOT_CI_TOKEN }}
  run: |
    docker run --rm \
      -e INGOT_URL -e INGOT_TOKEN \
      ghcr.io/onelitefeathernet/ingot-cli:latest \
      exec status
```

Do not rotate the token the job itself authenticates with, because the next run would no longer have a valid token. Rotate a different token, such as one for a deployment, and only with `--yes` in the step that means it.

`INGOT_TOKEN` passed through `-e` names the variable and does not put the value on the command line, so the value stays out of the job log. If the runner does not allow that, write the secret to a file with `install -m 600` and pass `-v` with `-e INGOT_TOKEN_FILE`, reading it as the container's user.

The exit code is the job's result. A step that runs several commands can pipe a script to `exec -` and add `--keep-going` only where the later commands do not depend on the earlier ones.

## Interactive sessions

The interactive mode keeps one WebSocket connection to `/api/console/sock` open for the whole session, the same connection the dashboard console uses. `exec` uses plain HTTP requests only. So:

* A reverse proxy or an ingress between the client and the server must allow the WebSocket upgrade on `/api/console/sock`, and its idle timeout should be long enough for a session in which nobody types. A proxy that serves the dashboard console already does both.
* When the connection drops, for a restart of the server or of a proxy, the client reconnects at the next command, which it does not send, with a wait that starts at about one second and grows to 30 seconds. The log continues where it stopped. A command that was running at the drop is reported as unknown and is not sent again, so check its effect before you repeat it.
* Behind a load balancer with several replicas, the session stays on one replica, and a reconnect may reach another one. The commands `level`, `status`, `failures`, `stats` and `cache` answer for the replica that runs them. Point `INGOT_URL` at one replica to look at that replica.
* A revoked token ends the session at its next command with exit code `4`. The client does not reconnect with it.

The protocol, its limits and its close codes are described in [Console commands](../../references/console-commands.md#websocket-protocol-version-1).

## Check that it works

```bash
ingot exec status
```

Exit code `0` and the status lines mean the server answered. The other codes are listed in [Exit codes](../../references/cli.md#exit-codes): `4` is the token, `5` is the network, `6` is an older server. The interactive mode answers `6` as well, before anything runs, against a server release older than the console protocol.

## Related

* [Command line client](../../references/cli.md): every option, variable and exit code.
* [Console commands](../../references/console-commands.md): the commands and the structured answer.
* [Audit events](../../references/identity/audit-events.md): `CONSOLE_COMMAND_EXECUTED` records each command the client runs.
