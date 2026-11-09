# Design

## Decision: replace runuser with setpriv in the root branch

The root branch needs three things from the process that runs the server: the uid and gid from PUID and PGID, the
supplementary groups of that account, and the environment `runuser` used to set. Both `runuser` and `setpriv` do the first
two. Only `setpriv` ends by replacing the process, which is what lets the JVM receive the signal.

## Alternatives considered

- **tini or dumb-init as PID 1.** Forwards signals correctly and reaps children. It adds a binary and a package to every
  image, and it changes what the container's process tree looks like for every deployment. Ingot starts no child processes
  that would need reaping, so the cost is not justified for this defect.
- **A shell wrapper that traps and forwards `SIGTERM`.** More code in the entrypoint, a second process that must be kept in
  step with the JVM, and a race at start-up. It fixes the symptom in the wrapper and leaves the wrapper as a permanent
  part of the process tree.
- **Keeping `runuser` and adding `--pty` or other flags.** Does not change which process holds the signal.

## Why PID 1 is safe for the JVM

The Linux kernel ignores signals sent to PID 1 unless the process has installed a handler. The JVM installs a handler for
`SIGTERM` and `SIGINT` and runs its shutdown hooks when one arrives, so the server stops cleanly as PID 1. The container test
checks this with exit code 143 instead of relying on the reasoning alone.
