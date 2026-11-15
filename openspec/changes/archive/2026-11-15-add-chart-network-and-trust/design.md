# Design

## Decisions

### Ingress is an allow-list by label, and egress restriction stays off

The policy names the namespaces and pods that may reach the Ingot port by label selector. Operators know their gateway and
monitoring namespaces, and the chart cannot know them. Egress restriction is a second switch, because a restricted egress
breaks mirror proxying and the database, bus and storage connections unless the operator lists them. `allowInternet` excludes
the RFC 1918 ranges, so with it on, in-cluster services stay unreachable until they are listed in `extra`. That is the reason
the switch is separate: an operator who turns on only the ingress policy gets no egress surprise.

### Split mode gets its own policy for the dashboard

The dashboard and the server are separate workloads with separate selector labels (`ingot.dashboardSelectorLabels` and
`ingot.selectorLabels`). One shared policy would have to admit the gateway to the server port, which the split design avoids.
Two policies keep each workload's allow-list explicit, and the server admits only the dashboard pods on top of the operator's
list.

### The CA bundle is a mount contract between chart and image

The chart owns the volumes. It mounts the bundle read-only at `/etc/ingot/extra-ca`, sets `INGOT_EXTRA_CA_CERTS` to that path,
and gives the container a writable `emptyDir` at `/tmp`. The image owns the merge: it copies the JDK `cacerts` into the writable
path, imports the PEM files with `keytool` and passes the trust store flag. The read-only root filesystem and the non-root
`user:` path both work, because nothing is written outside `/tmp`. The mount path is part of this change's spec, so the image
change must read the same path.

### One type per change

Artifact Hub metadata is `chore(chart)` and the CA merge is `feat(image)`. Both are split out, so this change ships only
`feat(chart)`. This follows the rule that a change that mixes types is two changes.

### No CI job in this change

Enforcement on kind needs a CNI that enforces policies. The check runs locally through `heavy` before the pull request.
A committed CI job would be a `ci(chart)` change of its own.
