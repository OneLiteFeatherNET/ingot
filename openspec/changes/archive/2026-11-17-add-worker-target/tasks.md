# Tasks

Ordered. Each test is written first and seen failing. Task checkboxes stay unchecked in this proposal pull request and are
ticked only in a follow-up that the maintainer asks for.

## 1. Worker target (backend)

- [x] 1.1 Test: `DeployTargetResolverTest` accepts `worker` from the option, the property and the variable, keeps the error text
  for unknown values and lists `all, server, worker` as valid values. Then add `WORKER("worker")` to `DeployTarget`.
  Verify: the existing resolver tests still pass unchanged.
- [x] 1.2 Test: a `WorkerRoutingTest` on an in-process server with target `worker` asserts `/api/status/health`,
  `/api/status/ready` and `/api/status/instance` answer, and that `GET /releases/...`, `GET /api/settings`, a `PUT` of an
  artifact and `GET /` answer `404`. Then restrict route registration for the worker to the status routes and `/metrics`.
  Verify: with target `all` and `server` the registered routes are identical to main (snapshot of the route list).
- [x] 1.3 Test: `BackgroundJobsSettingTest` for the three sources, the default `true`, a value that is neither `true` nor
  `false`, and the refusal on `all` and `worker`. Then add the setting and its resolution next to `DeployTargetResolver`.
- [x] 1.4 Test: the job gate. A fake scheduler records which jobs are submitted for each combination of target and switch. The
  singleton jobs of design.md F2 start on `all`, on `server` with the default and on `worker`, and not on `server` with
  `INGOT_BACKGROUND_JOBS=false`. The per-process duties of F2 start in all cases. Then wire the gate into the cleanup, search
  and audit purge plugins.
- [x] 1.5 Test: `ClusterRequirementsTest` for target `worker` and for the switch set to `false`, with a table of embedded
  database, missing bus URL and file system repository, each alone and all together. The refusal is one line that names every
  missing requirement and ends with exit code 1. Then add the check after the shared configuration is loaded and before the
  port binds, reusing `StartupRefusal`. Verify: target `all` and `server` with defaults never run the check.
- [x] 1.6 Integration test with two instances on one database (`:reposilite-backend:integration --tests '<pattern>'`): a worker
  and a server with `INGOT_BACKGROUND_JOBS=false`; a cleanup policy due now runs on the worker only and the server never
  claims the day key. Use the injected clock and tick the scheduler explicitly, no sleeps.
- [x] 1.7 Docs: `docs/gitbook/reference/deploy-targets.md` (targets, the switch, what the worker serves, the refusal lines) and
  the environment variable reference. Verify: every refusal line quoted on the page is asserted by a test of 1.5.

## 2. Scalable mode (chart)

- [x] 2.1 Test (`charts/ingot/tests/test_chart.py`): the default render and the `allInOne` and `split` golden fixtures are
  unchanged. Then add `scalable` to `mode` in `values.yaml` and `values.schema.json`.
- [x] 2.2 Test: `scalable` renders the Deployments `<name>-read`, `<name>-write`, `<name>-worker` and the dashboard, each with
  the expected `INGOT_TARGET`, `INGOT_BACKGROUND_JOBS=false` on read and write, replica values, probes and
  PodDisruptionBudgets. The worker has no HPA and no route. Then add the templates.
- [x] 2.3 Test: Services `<name>-read`, `<name>-write` and `<name>`, which selects the read and write pods together. Pod
  selectors of the Deployments are disjoint.
- [x] 2.4 Test: with `httpRoute.enabled`, the rules are the dashboard paths to the dashboard, `GET` and `HEAD` on the `/` prefix
  to read, and a last `/` prefix rule without a method to write. The test asserts the rule order and that no write verb
  is matched by a read rule. Then add the template.
- [x] 2.5 Test: with `ingress.enabled`, every non-dashboard path goes to `<name>`, and `NOTES.txt` prints the limitation. Then
  add the template and the note.
- [x] 2.6 Test: render refusals with the messages of design.md D8 for `scalable` without `database.existingSecret`, without
  `objectStorage.existingSecret`, without `clusterBus.url`, with `persistence.enabled` true, and with both routing forms.
- [x] 2.7 CI: add a `scalable` values file under `charts/ingot/ci` so that chart-testing installs it on kind. Verify: lint and
  install pass for the three modes, and the staging-migration equivalence test is unchanged.
  Done differently: the values file is `charts/ingot/tests/scalable/values.yaml`. chart-testing installs into a random
  namespace where the Secrets of the database, object storage and cluster bus do not exist, so the chart job installs the
  scalable profile with its own `helm install` step and lints the file with helm (#503).
- [x] 2.8 Docs: `docs/gitbook/how-to/run-scalable-with-helm.md` (values, routing table, the Ingress limitation with the
  ingress-nginx and Traefik notes, scaling advice) and `docs/gitbook/reference/helm-values.md`.

## 3. Pull requests

- [x] 3.1 Open the first code pull request with the title `feat(deploy): add the worker target and the background jobs switch`
  from a branch cut from the remote main that contains this proposal. Fill `.github/PULL_REQUEST_TEMPLATE.md`, tell it as the
  operator story (jobs competing with downloads), no session link, no em or en dashes, no file under `openspec/`. Verify with
  `gh pr view --json title,body,files`.
  Done differently: the first part shipped as small pull requests instead of one: #459, #479, #487, #490, #492, #496 and
  #498.
- [x] 3.2 Open the second code pull request with the title `feat(chart): add the scalable mode`, after 3.1 is merged and an
  image with the worker target exists. Same rules. Verify the same way, and that the chart CI job installs all three modes.
