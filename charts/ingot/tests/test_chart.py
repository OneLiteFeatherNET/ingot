"""Render tests for the Ingot Helm chart.

Every test renders the chart with `helm template` and asserts on the parsed manifests. No cluster, no network,
no clock: each test writes its own values file into a temporary directory and names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import itertools
import json
import os
import subprocess
import tempfile
import unittest

import yaml

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
CHART_DIR = os.path.dirname(TESTS_DIR)
HELM = os.environ.get("HELM", "helm")
RELEASE = "ingot"
NAMESPACE = "reposilite-staging"
SERVER = "ingot"
DASHBOARD = "ingot-dashboard"
SEGMENTS_FILE = os.path.join(CHART_DIR, "files", "dashboard-segments.json")


def helm(args, values=None):
    """Runs helm with an optional values file. Returns (exit code, stdout, stderr)."""
    with tempfile.TemporaryDirectory() as directory:
        command = [HELM]
        command.extend(args)
        if values is not None:
            values_path = os.path.join(directory, "values.yaml")
            with open(values_path, "w", encoding="utf-8") as handle:
                yaml.safe_dump(values, handle)
            command.extend(["-f", values_path])
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        return result.returncode, result.stdout, result.stderr


def render(values=None):
    """Renders the chart and returns the manifests as a list of dicts. Fails the test when helm fails."""
    code, stdout, stderr = helm(
        ["template", RELEASE, CHART_DIR, "--namespace", NAMESPACE], values
    )
    if code != 0:
        raise AssertionError(f"helm template failed with exit code {code}: {stderr}")
    return [doc for doc in yaml.safe_load_all(stdout) if doc]


def notes(values=None):
    """Returns the NOTES of a client-side dry-run install. `helm template` does not render NOTES.txt."""
    code, stdout, stderr = helm(
        ["install", RELEASE, CHART_DIR, "--namespace", NAMESPACE, "--dry-run=client"], values
    )
    if code != 0:
        raise AssertionError(f"helm install --dry-run=client failed with exit code {code}: {stderr}")
    marker = "\nNOTES:\n"
    if marker not in stdout:
        raise AssertionError("the dry-run output has no NOTES section")
    return stdout.partition(marker)[2]


def of_kind(manifests, kind):
    return [doc for doc in manifests if doc.get("kind") == kind]


def named(manifests, kind, name):
    matches = [doc for doc in of_kind(manifests, kind) if doc["metadata"]["name"] == name]
    if len(matches) != 1:
        raise AssertionError(f"expected one {kind} named {name}, found {len(matches)}")
    return matches[0]


def pod_spec(deployment):
    return deployment["spec"]["template"]["spec"]


def container(deployment):
    return pod_spec(deployment)["containers"][0]


def env_value(deployment, name):
    for entry in container(deployment).get("env", []):
        if entry.get("name") == name:
            return entry.get("value")
    return None


def segments():
    with open(SEGMENTS_FILE, encoding="utf-8") as handle:
        return json.load(handle)


def split_values():
    return {"mode": "split"}


def split_routing_values(pathtype="ingress"):
    if pathtype == "ingress":
        return {"mode": "split", "ingress": {"enabled": True, "hosts": [{"host": "ingot.example.org", "paths": []}]}}
    return {"mode": "split", "httpRoute": {"enabled": True, "parentRefs": [{"name": "gateway"}]}}


def ingress_paths(manifests):
    ingress = of_kind(manifests, "Ingress")
    if len(ingress) != 1:
        raise AssertionError(f"expected one Ingress, found {len(ingress)}")
    rules = ingress[0]["spec"]["rules"]
    return [(p["path"], p["pathType"], p["backend"]["service"]["name"]) for p in rules[0]["http"]["paths"]]


# Substrings of the replica warnings in templates/NOTES.txt. Each test checks one of them.
CLUSTER_WARNING = "INGOT_CLUSTER_URL is not set"
CLAIM_WARNING = "persistence.accessMode is ReadWriteOnce without persistence.existingClaim"
NO_PERSISTENCE_WARNING = "persistence.enabled is false"
DATABASE_WARNING = "neither INGOT_LOCAL_DATABASE nor REPOSILITE_LOCAL_DATABASE is set in env"
MEMORY_WARNING = "the autoscaler scales on the memory metric"
REPLICA_WARNINGS = (CLAIM_WARNING, NO_PERSISTENCE_WARNING, DATABASE_WARNING, MEMORY_WARNING)


def http_route_rules(manifests):
    routes = of_kind(manifests, "HTTPRoute")
    if len(routes) != 1:
        raise AssertionError(f"expected one HTTPRoute, found {len(routes)}")
    return routes[0]["spec"]["rules"]


class ChartRenderTest(unittest.TestCase):

    def test_default_mode_renders_one_deployment(self):
        deployments = of_kind(render(), "Deployment")
        self.assertEqual(1, len(deployments), "the default mode must render exactly one Deployment")

    def test_default_mode_renders_one_service(self):
        services = of_kind(render(), "Service")
        self.assertEqual(1, len(services), "the default mode must render exactly one Service")

    def test_default_mode_uses_the_all_in_one_image(self):
        image = container(named(render(), "Deployment", SERVER))["image"]
        self.assertTrue(
            image.startswith("ghcr.io/onelitefeathernet/ingot:"),
            f"the all-in-one workload must run the Ingot server image, found {image}",
        )

    def test_default_mode_does_not_set_the_target(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertIsNone(
            env_value(deployment, "INGOT_TARGET"),
            "the all-in-one workload must not set INGOT_TARGET, so the server keeps its default behaviour",
        )

    def test_split_mode_renders_two_deployments(self):
        deployments = of_kind(render(split_values()), "Deployment")
        self.assertEqual(2, len(deployments), "split mode must render a server and a dashboard Deployment")

    def test_split_mode_renders_two_services(self):
        services = of_kind(render(split_values()), "Service")
        self.assertEqual(2, len(services), "split mode must render a server and a dashboard Service")

    def test_split_server_keeps_the_all_in_one_selector(self):
        all_in_one = named(render(), "Deployment", SERVER)["spec"]["selector"]["matchLabels"]
        split = named(render(split_values()), "Deployment", SERVER)["spec"]["selector"]["matchLabels"]
        self.assertEqual(
            all_in_one,
            split,
            "the server selector is immutable, so moving an all-in-one release to split must keep it",
        )

    def test_split_server_service_does_not_select_dashboard_pods(self):
        manifests = render(split_values())
        selector = named(manifests, "Service", SERVER)["spec"]["selector"]
        dashboard_labels = named(manifests, "Deployment", DASHBOARD)["spec"]["template"]["metadata"]["labels"]
        selects_dashboard = all(dashboard_labels.get(key) == value for key, value in selector.items())
        self.assertFalse(selects_dashboard, "the server Service must not route traffic to dashboard pods")

    def test_split_dashboard_service_does_not_select_server_pods(self):
        manifests = render(split_values())
        selector = named(manifests, "Service", DASHBOARD)["spec"]["selector"]
        server_labels = named(manifests, "Deployment", SERVER)["spec"]["template"]["metadata"]["labels"]
        selects_server = all(server_labels.get(key) == value for key, value in selector.items())
        self.assertFalse(selects_server, "the dashboard Service must not route traffic to server pods")

    def test_split_server_sets_ingot_target_to_server(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        self.assertEqual(
            "server", env_value(deployment, "INGOT_TARGET"), "the server workload must set INGOT_TARGET=server"
        )

    def test_split_server_sets_the_cluster_instance_id_from_the_pod_name(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        entries = [entry for entry in container(deployment)["env"] if entry["name"] == "INGOT_CLUSTER_INSTANCE_ID"]
        self.assertEqual(1, len(entries), "the server must set INGOT_CLUSTER_INSTANCE_ID exactly once")
        self.assertEqual(
            {"fieldRef": {"fieldPath": "metadata.name"}},
            entries[0]["valueFrom"],
            "the instance id must be the pod name, so each replica identifies itself on the bus",
        )

    def test_split_server_keeps_a_cluster_instance_id_set_in_env(self):
        values = split_values()
        values["env"] = [{"name": "INGOT_CLUSTER_INSTANCE_ID", "value": "fixed-id"}]
        deployment = named(render(values), "Deployment", SERVER)
        entries = [entry for entry in container(deployment)["env"] if entry["name"] == "INGOT_CLUSTER_INSTANCE_ID"]
        self.assertEqual(
            [{"name": "INGOT_CLUSTER_INSTANCE_ID", "value": "fixed-id"}], entries,
            "an instance id the operator set must not be overridden or duplicated",
        )

    def test_all_in_one_does_not_set_the_cluster_instance_id(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertIsNone(
            env_value(deployment, "INGOT_CLUSTER_INSTANCE_ID"),
            "the all-in-one workload keeps the server default, the host name",
        )

    def test_split_dashboard_sets_ingot_backend_to_the_server_service(self):
        deployment = named(render(split_values()), "Deployment", DASHBOARD)
        self.assertEqual(
            f"http://{SERVER}:8080",
            env_value(deployment, "INGOT_BACKEND"),
            "the dashboard must proxy to the server Service on its port",
        )

    def test_split_dashboard_runs_the_dashboard_image(self):
        image = container(named(render(split_values()), "Deployment", DASHBOARD))["image"]
        self.assertTrue(
            image.startswith("ghcr.io/onelitefeathernet/ingot-dashboard:"),
            f"the dashboard workload must run the dashboard image, found {image}",
        )

    def test_unknown_mode_fails_schema_validation(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], {"mode": "both"})
        self.assertNotEqual(0, code, "mode: both must not render")
        self.assertIn("mode", stderr, "the schema error must name the mode key")

    def test_dashboard_container_is_hardened_by_default(self):
        security = container(named(render(split_values()), "Deployment", DASHBOARD))["securityContext"]
        self.assertEqual(65532, security["runAsUser"], "the dashboard must run as UID 65532 by default")
        self.assertTrue(security["readOnlyRootFilesystem"], "the dashboard root filesystem must be read-only")
        self.assertEqual(["ALL"], security["capabilities"]["drop"], "the dashboard must drop all capabilities")
        self.assertFalse(security["allowPrivilegeEscalation"], "privilege escalation must be disabled")

    def test_dashboard_pod_requires_non_root_and_runtime_default_seccomp(self):
        spec = pod_spec(named(render(split_values()), "Deployment", DASHBOARD))
        self.assertTrue(spec["securityContext"]["runAsNonRoot"], "the dashboard pod must require a non-root user")
        self.assertEqual(
            "RuntimeDefault",
            spec["securityContext"]["seccompProfile"]["type"],
            "the dashboard pod must use the RuntimeDefault seccomp profile",
        )

    def test_server_has_no_security_context_by_default(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        self.assertNotIn("securityContext", container(deployment), "the server container must keep upstream defaults")
        self.assertNotIn("securityContext", pod_spec(deployment), "the server pod must keep upstream defaults")

    def test_server_does_not_set_run_as_user_in_all_in_one_mode(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertNotIn("runAsUser", container(deployment).get("securityContext", {}),
                         "runAsUser must stay unset so that PUID and PGID keep working")

    # The root path answers 503 while a replica drains or is at its request limit, so a liveness probe on it restarts
    # the pod. The health endpoint bypasses the drain and the request limit, and it never checks the database.
    def test_all_in_one_liveness_uses_the_health_endpoint(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/health", container(deployment)["livenessProbe"]["httpGet"]["path"],
            "the all-in-one liveness must use the health endpoint, which stays 200 while draining or saturated",
        )

    def test_all_in_one_readiness_uses_the_readiness_endpoint(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
            "the all-in-one readiness must use /api/status/ready, which answers 503 while draining",
        )

    def test_all_in_one_probes_path_override_moves_liveness_only(self):
        deployment = named(render({"probes": {"path": "/custom-live"}}), "Deployment", SERVER)
        self.assertEqual(
            "/custom-live", container(deployment)["livenessProbe"]["httpGet"]["path"],
            "probes.path must still override the all-in-one liveness path",
        )
        self.assertEqual(
            "/api/status/ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
            "probes.path must not change the all-in-one readiness default",
        )

    # Replaces test_startup_probe_is_absent_by_default for split: the startup probe is on by default in split, so a
    # first start that takes longer than the liveness budget is not restarted. All-in-one keeps it off.
    def test_startup_probe_is_absent_by_default_in_all_in_one(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertNotIn("startupProbe", container(deployment), "no startup probe in all-in-one unless it is enabled")

    def test_startup_probe_is_rendered_by_default_in_split(self):
        startup = container(named(render(split_values()), "Deployment", SERVER))["startupProbe"]
        self.assertEqual(5, startup["periodSeconds"], "the split startup probe period must be 5 seconds")
        self.assertEqual(30, startup["failureThreshold"], "the split startup budget must be 30 failures of 5 seconds")
        self.assertEqual(
            "/api/status/health", startup["httpGet"]["path"], "the split startup probe must use the health endpoint",
        )

    def test_startup_probe_can_be_turned_off_in_split(self):
        values = split_values()
        values["probes"] = {"startup": {"enabled": False}}
        deployment = named(render(values), "Deployment", SERVER)
        self.assertNotIn("startupProbe", container(deployment), "an explicit enabled: false must win over the split default")

    def test_enabled_startup_probe_uses_the_health_endpoint_in_all_in_one_mode(self):
        deployment = named(render({"probes": {"startup": {"enabled": True}}}), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/health", container(deployment)["startupProbe"]["httpGet"]["path"],
            "the all-in-one startup probe must use the health endpoint, like the liveness probe",
        )

    def test_enabled_startup_probe_uses_the_api_health_endpoint_in_split_mode(self):
        values = split_values()
        values["probes"] = {"startup": {"enabled": True}}
        deployment = named(render(values), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/health", container(deployment)["startupProbe"]["httpGet"]["path"],
            "the split server startup probe must avoid the dashboard path",
        )

    def test_dashboard_probes_use_the_healthz_endpoint(self):
        deployment = named(render(split_values()), "Deployment", DASHBOARD)
        for probe in ("readinessProbe", "livenessProbe"):
            self.assertEqual("/healthz", container(deployment)[probe]["httpGet"]["path"],
                             f"the dashboard {probe} must use GET /healthz")

    # Replaces test_split_server_probes_use_the_api_health_endpoint. Readiness moved to /api/status/ready, because the
    # health endpoint answers UP while the database is down. Liveness stays on the health endpoint, so an outage never
    # restarts a pod.
    def test_split_server_liveness_uses_the_api_health_endpoint(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/health", container(deployment)["livenessProbe"]["httpGet"]["path"],
            "the split server liveness must stay on the health endpoint, so a database outage never restarts a pod",
        )

    def test_split_server_readiness_uses_the_readiness_endpoint(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
            "the split server readiness must use /api/status/ready, which fails while the database or storage is down",
        )

    def test_split_server_readiness_tolerates_one_slow_answer(self):
        readiness = container(named(render(split_values()), "Deployment", SERVER))["readinessProbe"]
        self.assertEqual(3, readiness["timeoutSeconds"], "the split readiness check runs two bounded checks, so it needs 3 seconds")
        self.assertEqual(2, readiness["failureThreshold"], "one slow answer must not take the split pod out of rotation")

    # The all-in-one readiness now runs the same database and storage checks as the split server, so it gets the same
    # budget: one slow answer must not take the replica out of rotation.
    def test_all_in_one_readiness_tolerates_one_slow_answer(self):
        readiness = container(named(render(), "Deployment", SERVER))["readinessProbe"]
        self.assertEqual(3, readiness["timeoutSeconds"], "the all-in-one readiness timeout must be 3 seconds, as in split mode")
        self.assertEqual(2, readiness["failureThreshold"], "the all-in-one readiness must tolerate one failed check, as in split mode")

    def test_explicit_readiness_path_wins_in_all_in_one_mode(self):
        deployment = named(render({"probes": {"readinessPath": "/api/status/ready"}}), "Deployment", SERVER)
        self.assertEqual(
            "/api/status/ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
            "an explicit probes.readinessPath must win over the all-in-one default",
        )
        self.assertEqual(
            "/api/status/health", container(deployment)["livenessProbe"]["httpGet"]["path"],
            "probes.readinessPath must not change the liveness probe",
        )

    def test_explicit_readiness_path_wins_in_split_mode(self):
        values = split_values()
        values["probes"] = {"readinessPath": "/custom-ready"}
        deployment = named(render(values), "Deployment", SERVER)
        self.assertEqual(
            "/custom-ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
            "an explicit probes.readinessPath must win over the split default",
        )
        self.assertEqual(
            "/api/status/health", container(deployment)["livenessProbe"]["httpGet"]["path"],
            "probes.readinessPath must not change the split liveness probe",
        )

    # The server listens on the container port, which is service.port. A probe on another port fails every time and
    # restarts the pod, so the probes follow the container port unless probes.port is set explicitly.
    def test_probes_use_the_service_port_by_default(self):
        deployment = named(render({"service": {"port": 9000}}), "Deployment", SERVER)
        for probe in ("readinessProbe", "livenessProbe"):
            self.assertEqual(9000, container(deployment)[probe]["httpGet"]["port"],
                             f"the {probe} must probe the container port, which is service.port")

    def test_startup_probe_uses_the_service_port_by_default_in_split_mode(self):
        deployment = named(render(dict(split_values(), service={"port": 9000})), "Deployment", SERVER)
        self.assertEqual(9000, container(deployment)["startupProbe"]["httpGet"]["port"],
                         "the split startup probe must probe the container port, which is service.port")

    def test_explicit_probes_port_wins_over_the_service_port(self):
        values = {"service": {"port": 9000}, "probes": {"port": 8081}}
        deployment = named(render(values), "Deployment", SERVER)
        for probe in ("readinessProbe", "livenessProbe"):
            self.assertEqual(8081, container(deployment)[probe]["httpGet"]["port"],
                             f"an explicit probes.port must win over service.port for the {probe}")

    def test_pod_disruption_budget_is_absent_by_default(self):
        self.assertEqual([], of_kind(render(), "PodDisruptionBudget"), "no PodDisruptionBudget by default")

    def test_service_monitor_is_absent_by_default(self):
        self.assertEqual([], of_kind(render(), "ServiceMonitor"), "no ServiceMonitor by default")

    def test_service_monitor_has_no_basic_auth_by_default(self):
        manifests = render({"serviceMonitor": {"enabled": True}})
        endpoint = named(manifests, "ServiceMonitor", SERVER)["spec"]["endpoints"][0]
        self.assertNotIn("basicAuth", endpoint, "the scrape must not send credentials unless basicAuth is enabled")

    def test_service_monitor_basic_auth_reads_the_user_secret(self):
        manifests = render({
            "serviceMonitor": {
                "enabled": True,
                "basicAuth": {"enabled": True, "secretName": "metrics-scrape", "usernameKey": "user", "passwordKey": "pass"},
            },
        })
        basic_auth = named(manifests, "ServiceMonitor", SERVER)["spec"]["endpoints"][0]["basicAuth"]
        self.assertEqual(
            {"name": "metrics-scrape", "key": "user"}, basic_auth["username"],
            "the username must come from the named key of the user Secret",
        )
        self.assertEqual(
            {"name": "metrics-scrape", "key": "pass"}, basic_auth["password"],
            "the password must come from the named key of the user Secret",
        )

    def test_http_route_is_absent_by_default(self):
        self.assertEqual([], of_kind(render(), "HTTPRoute"), "no HTTPRoute by default")

    def test_enabled_pod_disruption_budget_sets_max_unavailable_one(self):
        manifests = render({"podDisruptionBudget": {"enabled": True}})
        budgets = of_kind(manifests, "PodDisruptionBudget")
        self.assertEqual(1, len(budgets), "an enabled PodDisruptionBudget must be rendered")
        self.assertEqual(1, budgets[0]["spec"]["maxUnavailable"], "maxUnavailable must be 1")
        self.assertNotIn("minAvailable", budgets[0]["spec"], "minAvailable blocks node drains on one replica")

    def test_ingress_and_http_route_together_fail_naming_both_keys(self):
        code, _, stderr = helm(
            ["template", RELEASE, CHART_DIR],
            {"ingress": {"enabled": True}, "httpRoute": {"enabled": True}},
        )
        self.assertNotEqual(0, code, "enabling both routing forms must fail")
        self.assertIn("ingress.enabled", stderr, "the message must name ingress.enabled")
        self.assertIn("httpRoute.enabled", stderr, "the message must name httpRoute.enabled")

    def test_split_ingress_routes_the_overview_exactly_to_the_dashboard(self):
        paths = ingress_paths(render(split_routing_values("ingress")))
        self.assertIn(("/", "Exact", DASHBOARD), paths, "the path / must be an Exact match to the dashboard")

    def test_split_ingress_routes_the_root_prefix_to_the_server(self):
        paths = ingress_paths(render(split_routing_values("ingress")))
        self.assertIn(("/", "Prefix", SERVER), paths, "every other path must be a Prefix / to the server")

    def test_split_ingress_routes_every_dashboard_segment_to_the_dashboard(self):
        paths = ingress_paths(render(split_routing_values("ingress")))
        for segment in segments():
            self.assertIn(
                (f"/{segment}", "Prefix", DASHBOARD),
                paths,
                f"the dashboard segment /{segment} must be routed to the dashboard Service",
            )

    def test_split_ingress_routes_assets_and_favicon_to_the_dashboard(self):
        paths = ingress_paths(render(split_routing_values("ingress")))
        self.assertIn(("/assets", "Prefix", DASHBOARD), paths, "the assets must go to the dashboard")
        self.assertIn(("/favicon.png", "Exact", DASHBOARD), paths, "the favicon must go to the dashboard")

    def test_split_http_route_routes_the_overview_exactly_to_the_dashboard(self):
        rules = http_route_rules(render(split_routing_values("httpRoute")))
        matches = [(m["path"]["type"], m["path"]["value"], r["backendRefs"][0]["name"])
                   for r in rules for m in r["matches"]]
        self.assertIn(("Exact", "/", DASHBOARD), matches, "the HTTPRoute must send / exactly to the dashboard")
        self.assertIn(("PathPrefix", "/", SERVER), matches, "the HTTPRoute must send the rest to the server")

    def test_segment_list_contains_analytics(self):
        self.assertIn("analytics", segments(), "the dashboard segment list must include analytics from PR #69")

    def test_segment_list_contains_no_duplicates(self):
        names = segments()
        self.assertEqual(len(names), len(set(names)), "the segment list must not repeat a segment")

    def test_notes_warn_about_several_replicas_without_the_cluster_bus(self):
        self.assertIn("WARNING", notes({"deployment": {"replicas": 2}}),
                      "two replicas without INGOT_CLUSTER_URL must produce the warning")

    def test_notes_warn_about_autoscaling_without_the_cluster_bus(self):
        self.assertIn("WARNING", notes({"autoscaling": {"enabled": True}}),
                      "autoscaling without INGOT_CLUSTER_URL must produce the warning")

    def test_notes_do_not_warn_about_one_replica(self):
        self.assertNotIn("WARNING", notes(), "a single replica needs no cluster bus, so no warning")

    def test_notes_do_not_warn_when_the_cluster_bus_is_configured(self):
        values = {
            "deployment": {"replicas": 2},
            "env": [{"name": "INGOT_CLUSTER_URL", "value": "nats://broker.example.org:4222"}],
        }
        # Only the cluster bus message is checked: with two replicas the replica warnings of the chart still apply.
        self.assertNotIn(CLUSTER_WARNING, notes(values), "a configured INGOT_CLUSTER_URL must silence the cluster bus warning")

    def test_notes_warn_about_a_read_write_once_claim_with_several_replicas(self):
        self.assertIn(CLAIM_WARNING, notes({"deployment": {"replicas": 2}}),
                      "two replicas on a ReadWriteOnce claim must produce the claim warning")

    def test_notes_do_not_warn_about_the_claim_when_an_existing_claim_is_set(self):
        values = {"deployment": {"replicas": 2}, "persistence": {"existingClaim": "shared-data"}}
        self.assertNotIn(CLAIM_WARNING, notes(values), "an existingClaim is the shared volume the operator chose")

    def test_notes_do_not_warn_about_the_claim_with_a_read_write_many_claim(self):
        values = {"deployment": {"replicas": 2}, "persistence": {"accessMode": "ReadWriteMany"}}
        self.assertNotIn(CLAIM_WARNING, notes(values), "a ReadWriteMany claim can be shared by several pods")

    def test_notes_warn_about_disabled_persistence_with_several_replicas(self):
        values = {"deployment": {"replicas": 2}, "persistence": {"enabled": False}}
        self.assertIn(NO_PERSISTENCE_WARNING, notes(values),
                      "without persistence each pod keeps its own data, so the warning must appear")

    def test_notes_warn_about_the_database_with_several_replicas(self):
        self.assertIn(DATABASE_WARNING, notes({"deployment": {"replicas": 2}}),
                      "several replicas without INGOT_LOCAL_DATABASE must produce the database warning")

    def test_notes_do_not_warn_about_the_database_when_the_local_database_is_set(self):
        values = {"deployment": {"replicas": 2}, "env": [{"name": "INGOT_LOCAL_DATABASE", "value": "postgresql"}]}
        self.assertNotIn(DATABASE_WARNING, notes(values), "INGOT_LOCAL_DATABASE in env must silence the database warning")

    def test_notes_accept_the_legacy_local_database_name_in_env(self):
        values = {"deployment": {"replicas": 2}, "env": [{"name": "REPOSILITE_LOCAL_DATABASE", "value": "postgresql"}]}
        self.assertNotIn(DATABASE_WARNING, notes(values), "the legacy REPOSILITE_LOCAL_DATABASE must silence the warning")

    def test_notes_do_not_warn_about_the_database_when_the_existing_secret_is_set(self):
        values = {"deployment": {"replicas": 2}, "database": {"existingSecret": "ingot-database"}}
        self.assertNotIn(DATABASE_WARNING, notes(values),
                         "database.existingSecret sets INGOT_LOCAL_DATABASE, so the database warning must not appear")

    def test_notes_do_not_warn_about_the_cluster_bus_when_its_url_is_set(self):
        values = {"deployment": {"replicas": 2}, "clusterBus": {"url": "nats://broker.example.org:4222"}}
        self.assertNotIn(CLUSTER_WARNING, notes(values),
                         "clusterBus.url sets INGOT_CLUSTER_URL, so the cluster bus warning must not appear")

    def test_notes_still_warn_about_the_database_when_only_the_cluster_bus_is_set(self):
        values = {"deployment": {"replicas": 2}, "clusterBus": {"url": "nats://broker.example.org:4222"}}
        self.assertIn(DATABASE_WARNING, notes(values),
                      "the cluster bus must not silence the database warning, which is a different setting")

    def test_autoscaler_is_absent_by_default(self):
        self.assertEqual([], of_kind(render(), "HorizontalPodAutoscaler"), "no HorizontalPodAutoscaler by default")

    def test_autoscaler_renders_its_bounds_and_targets_the_server_deployment(self):
        values = {"autoscaling": {"enabled": True, "minReplicas": 2, "maxReplicas": 5}}
        autoscalers = of_kind(render(values), "HorizontalPodAutoscaler")
        self.assertEqual(1, len(autoscalers), "autoscaling.enabled must render exactly one HorizontalPodAutoscaler")
        spec = autoscalers[0]["spec"]
        self.assertEqual(2, spec["minReplicas"], "minReplicas must come from autoscaling.minReplicas")
        self.assertEqual(5, spec["maxReplicas"], "maxReplicas must come from autoscaling.maxReplicas")
        self.assertEqual(
            {"apiVersion": "apps/v1", "kind": "Deployment", "name": SERVER},
            spec["scaleTargetRef"],
            "the autoscaler must target the server Deployment, not the dashboard",
        )

    def test_autoscaler_renders_the_cpu_and_memory_metrics_by_default(self):
        autoscaler = named(render({"autoscaling": {"enabled": True}}), "HorizontalPodAutoscaler", SERVER)
        names = [metric["resource"]["name"] for metric in autoscaler["spec"]["metrics"]]
        self.assertEqual(["cpu", "memory"], names, "the default autoscaler scales on cpu and memory")

    def test_notes_warn_about_the_memory_metric_only_with_the_autoscaler_on(self):
        self.assertIn(MEMORY_WARNING, notes({"autoscaling": {"enabled": True}}),
                      "the default autoscaler lists the memory metric, so the warning must appear")
        self.assertNotIn(MEMORY_WARNING, notes({"deployment": {"replicas": 2}}),
                         "without the autoscaler the memory metric is not in use, so no warning")

    def test_notes_do_not_warn_about_the_memory_metric_when_only_cpu_is_scaled(self):
        cpu_only = [{"type": "Resource", "resource": {"name": "cpu", "target": {"type": "Utilization", "averageUtilization": 60}}}]
        values = {"autoscaling": {"enabled": True, "metrics": cpu_only}}
        self.assertNotIn(MEMORY_WARNING, notes(values), "a CPU-only autoscaler must not produce the memory warning")

    def test_notes_add_no_replica_warning_for_a_single_replica_with_defaults(self):
        text = notes()
        for warning in (CLUSTER_WARNING,) + REPLICA_WARNINGS:
            self.assertNotIn(warning, text, "a single replica with defaults must get no replica warning")

    def test_notes_never_fail_the_install_for_any_replica_combination(self):
        persistence_variants = (
            {"enabled": True},
            {"enabled": True, "existingClaim": "shared-data"},
            {"enabled": False},
        )
        for replicas in (1, 2):
            for autoscaling in (False, True):
                for persistence in persistence_variants:
                    for with_database in (False, True):
                        values = {
                            "deployment": {"replicas": replicas},
                            "autoscaling": {"enabled": autoscaling},
                            "persistence": persistence,
                        }
                        if with_database:
                            values["env"] = [{"name": "INGOT_LOCAL_DATABASE", "value": "postgresql"}]
                        with self.subTest(values=values):
                            notes(values)  # raises when the dry run exits with a failure status

    def test_helm_lint_is_clean_in_all_in_one_mode(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict"], {"mode": "allInOne"})
        self.assertEqual(0, code, f"helm lint must pass in allInOne mode: {stdout}{stderr}")

    def test_helm_lint_is_clean_in_split_mode(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict"], split_values())
        self.assertEqual(0, code, f"helm lint must pass in split mode: {stdout}{stderr}")


def shutdown_values(**shutdown):
    return {"deployment": {"shutdown": shutdown}}


def timeout_entries(deployment):
    return [entry for entry in container(deployment).get("env", []) if entry.get("name") == "INGOT_SHUTDOWN_TIMEOUT"]


class ShutdownBudgetTest(unittest.TestCase):
    """The opt-in shutdown budget: a preStop sleep, a drain timeout and the grace period that holds both."""

    def test_no_shutdown_value_keeps_the_sixty_second_grace_period(self):
        deployment = named(render(), "Deployment", SERVER)
        self.assertEqual(60, pod_spec(deployment)["terminationGracePeriodSeconds"],
                         "without a shutdown value the grace period must stay at 60 seconds")
        self.assertNotIn("lifecycle", container(deployment), "without a shutdown value there is no preStop action")
        self.assertEqual([], timeout_entries(deployment), "without a shutdown value INGOT_SHUTDOWN_TIMEOUT is not set")

    def test_pre_stop_seconds_render_a_sleep_in_the_pre_stop_hook(self):
        deployment = named(render(shutdown_values(preStopSeconds=15)), "Deployment", SERVER)
        self.assertEqual(
            {"preStop": {"exec": {"command": ["sleep", "15"]}}},
            container(deployment).get("lifecycle"),
            "preStopSeconds 15 must render an exec preStop action that sleeps 15 seconds",
        )

    def test_pre_stop_seconds_alone_derive_the_grace_period(self):
        deployment = named(render(shutdown_values(preStopSeconds=15)), "Deployment", SERVER)
        self.assertEqual(25, pod_spec(deployment)["terminationGracePeriodSeconds"],
                         "the grace period must be preStopSeconds + drainSeconds + 10, which is 25 here")

    def test_drain_seconds_render_the_shutdown_timeout_variable(self):
        deployment = named(render(shutdown_values(drainSeconds=30)), "Deployment", SERVER)
        self.assertEqual(
            [{"name": "INGOT_SHUTDOWN_TIMEOUT", "value": "30"}],
            timeout_entries(deployment),
            "drainSeconds 30 must render INGOT_SHUTDOWN_TIMEOUT with the value 30",
        )
        self.assertEqual(40, pod_spec(deployment)["terminationGracePeriodSeconds"],
                         "drainSeconds alone must derive the grace period as drainSeconds + 10")

    def test_both_values_derive_the_grace_period_of_their_sum_plus_ten(self):
        deployment = named(render(shutdown_values(preStopSeconds=15, drainSeconds=30)), "Deployment", SERVER)
        self.assertEqual(55, pod_spec(deployment)["terminationGracePeriodSeconds"],
                         "the grace period must be 15 + 30 + 10 = 55 seconds")
        self.assertEqual(
            {"preStop": {"exec": {"command": ["sleep", "15"]}}}, container(deployment).get("lifecycle"),
            "both values together must still render the preStop sleep",
        )
        self.assertEqual(1, len(timeout_entries(deployment)), "INGOT_SHUTDOWN_TIMEOUT must be named exactly once")

    def test_shutdown_timeout_set_in_env_is_kept_once_with_its_own_value(self):
        values = shutdown_values(drainSeconds=30)
        values["env"] = [{"name": "INGOT_SHUTDOWN_TIMEOUT", "value": "10"}]
        deployment = named(render(values), "Deployment", SERVER)
        self.assertEqual(
            [{"name": "INGOT_SHUTDOWN_TIMEOUT", "value": "10"}],
            timeout_entries(deployment),
            "an INGOT_SHUTDOWN_TIMEOUT the operator set in env must win and must not be duplicated",
        )

    def test_operator_lifecycle_wins_over_pre_stop_seconds(self):
        operator_hook = {"preStop": {"exec": {"command": ["/bin/sh", "-c", "sleep 40"]}}}
        values = shutdown_values(preStopSeconds=15)
        values["deployment"]["lifecycle"] = operator_hook
        deployment = named(render(values), "Deployment", SERVER)
        self.assertEqual(
            operator_hook, container(deployment).get("lifecycle"),
            "a deployment.lifecycle set by the operator must be kept, and no sleep of the chart must replace it",
        )


READ = "ingot-read"
WRITE = "ingot-write"
WORKER = "ingot-worker"


def scalable_values(**overrides):
    """Scalable values with the typed requirements set. The refusals of missing values come in a later change, so the
    tests pass them here and leave persistence off, which scalable mode requires."""
    values = {
        "mode": "scalable",
        "database": {"existingSecret": "ingot-database"},
        "objectStorage": {"existingSecret": "ingot-object-storage"},
        "clusterBus": {"url": "nats://ingot-nats:4222"},
        "persistence": {"enabled": False},
    }
    values.update(overrides)
    return values


def pod_labels(deployment):
    return deployment["spec"]["template"]["metadata"]["labels"]


def pod_selector(deployment):
    return deployment["spec"]["selector"]["matchLabels"]


def selects(selector, labels):
    """True when every pair of an equality selector is found in the labels, as Kubernetes matches a selector."""
    return all(labels.get(key) == value for key, value in selector.items())


def disjoint(first, second):
    """Two equality selectors select no common pod when they name one key with two different values."""
    return any(key in second and second[key] != value for key, value in first.items())


def mount_names(deployment):
    return [mount["name"] for mount in container(deployment).get("volumeMounts", [])]


class ScalableValuesTest(unittest.TestCase):

    def test_schema_lists_scalable_as_a_mode(self):
        with open(os.path.join(CHART_DIR, "values.schema.json"), encoding="utf-8") as handle:
            schema = json.load(handle)
        self.assertIn("scalable", schema["properties"]["mode"]["enum"], "the mode enum must accept scalable")

    def test_schema_accepts_scalable_with_the_typed_values(self):
        manifests = render(scalable_values())
        self.assertTrue(manifests, "mode scalable with the typed values must render")


class ScalableWorkloadTest(unittest.TestCase):

    def test_scalable_renders_read_write_worker_and_dashboard_deployments(self):
        names = sorted(doc["metadata"]["name"] for doc in of_kind(render(scalable_values()), "Deployment"))
        self.assertEqual(
            sorted([READ, WRITE, WORKER, DASHBOARD]), names,
            "scalable mode must render the read, write and worker Deployments and the dashboard",
        )

    def test_read_and_write_set_ingot_target_server(self):
        manifests = render(scalable_values())
        for name in (READ, WRITE):
            self.assertEqual(
                "server", env_value(named(manifests, "Deployment", name), "INGOT_TARGET"),
                f"{name} must set INGOT_TARGET=server",
            )

    def test_worker_sets_ingot_target_worker(self):
        worker = named(render(scalable_values()), "Deployment", WORKER)
        self.assertEqual("worker", env_value(worker, "INGOT_TARGET"), "the worker must set INGOT_TARGET=worker")

    def test_read_and_write_switch_background_jobs_off(self):
        manifests = render(scalable_values())
        for name in (READ, WRITE):
            self.assertEqual(
                "false", env_value(named(manifests, "Deployment", name), "INGOT_BACKGROUND_JOBS"),
                f"{name} must set INGOT_BACKGROUND_JOBS=false",
            )

    def test_worker_leaves_background_jobs_on(self):
        worker = named(render(scalable_values()), "Deployment", WORKER)
        self.assertIsNone(env_value(worker, "INGOT_BACKGROUND_JOBS"), "the worker must keep the default of the jobs")

    def test_replicas_default_to_two_read_two_write_and_one_worker(self):
        manifests = render(scalable_values())
        self.assertEqual(2, named(manifests, "Deployment", READ)["spec"]["replicas"], "read defaults to 2 replicas")
        self.assertEqual(2, named(manifests, "Deployment", WRITE)["spec"]["replicas"], "write defaults to 2 replicas")
        self.assertEqual(1, named(manifests, "Deployment", WORKER)["spec"]["replicas"], "the worker runs one replica")

    def test_read_replicas_follow_scalable_read_replicas(self):
        read = named(render(scalable_values(scalable={"read": {"replicas": 3}})), "Deployment", READ)
        self.assertEqual(3, read["spec"]["replicas"], "scalable.read.replicas must set the read replicas")

    def test_read_hpa_targets_the_read_deployment(self):
        values = scalable_values(scalable={"read": {"autoscaling": {"enabled": True, "minReplicas": 2, "maxReplicas": 4}}})
        manifests = render(values)
        targets = [(hpa["metadata"]["name"], hpa["spec"]["scaleTargetRef"]["name"])
                   for hpa in of_kind(manifests, "HorizontalPodAutoscaler")]
        self.assertEqual([(READ, READ)], targets, "only the read workload may have an HPA, and it must target read")
        self.assertNotIn("replicas", named(manifests, "Deployment", READ)["spec"], "the HPA owns the read replicas")

    def test_worker_has_no_hpa_even_when_read_autoscales(self):
        values = scalable_values(scalable={"read": {"autoscaling": {"enabled": True}}})
        targets = [hpa["spec"]["scaleTargetRef"]["name"] for hpa in of_kind(render(values), "HorizontalPodAutoscaler")]
        self.assertNotIn(WORKER, targets, "the worker must never be autoscaled")

    def test_server_workloads_use_the_split_health_endpoints(self):
        manifests = render(scalable_values())
        for name in (READ, WRITE, WORKER):
            deployment = named(manifests, "Deployment", name)
            self.assertEqual(
                "/api/status/ready", container(deployment)["readinessProbe"]["httpGet"]["path"],
                f"{name} must use the readiness endpoint",
            )
            self.assertEqual(
                "/api/status/health", container(deployment)["livenessProbe"]["httpGet"]["path"],
                f"{name} must use the health endpoint for liveness",
            )
            self.assertIn("startupProbe", container(deployment), f"{name} must render the split startup probe")

    def test_pod_disruption_budget_is_rendered_for_an_enabled_workload_only(self):
        values = scalable_values(scalable={"write": {"podDisruptionBudget": {"enabled": True}}})
        budgets = of_kind(render(values), "PodDisruptionBudget")
        self.assertEqual([WRITE], [budget["metadata"]["name"] for budget in budgets],
                         "only the workload with podDisruptionBudget.enabled gets a budget")
        self.assertTrue(selects(budgets[0]["spec"]["selector"]["matchLabels"], pod_labels(
            named(render(values), "Deployment", WRITE))), "the budget must select the write pods")

    def test_no_pod_disruption_budget_by_default(self):
        self.assertEqual([], of_kind(render(scalable_values()), "PodDisruptionBudget"),
                         "scalable mode renders no budget unless one is enabled")

    def test_top_level_pod_disruption_budget_covers_only_the_dashboard(self):
        values = scalable_values(podDisruptionBudget={"enabled": True})
        names = sorted(budget["metadata"]["name"] for budget in of_kind(render(values), "PodDisruptionBudget"))
        self.assertEqual([DASHBOARD], names, "the top-level budget must not select the server pods in scalable mode")

    def test_every_scalable_workload_reaches_the_cluster_bus(self):
        manifests = render(scalable_values())
        for name in (READ, WRITE, WORKER):
            env_names = [entry["name"] for entry in container(named(manifests, "Deployment", name))["env"]]
            self.assertIn("INGOT_CLUSTER_URL", env_names, f"{name} must reach the cluster bus")

    def test_data_volume_is_not_mounted_in_scalable_mode(self):
        manifests = render(scalable_values())
        for name in (READ, WRITE, WORKER):
            self.assertNotIn("data", mount_names(named(manifests, "Deployment", name)),
                             f"{name} must not mount the data volume, which a PVC cannot share")


class ScalableServiceTest(unittest.TestCase):

    def test_scalable_renders_read_write_and_combined_services(self):
        names = sorted(doc["metadata"]["name"] for doc in of_kind(render(scalable_values()), "Service"))
        self.assertEqual(
            sorted([READ, WRITE, SERVER, DASHBOARD]), names,
            "scalable mode must render the read and write Services, the combined Service and the dashboard Service",
        )

    def test_read_and_write_services_select_only_their_pods(self):
        manifests = render(scalable_values())
        read_labels = pod_labels(named(manifests, "Deployment", READ))
        write_labels = pod_labels(named(manifests, "Deployment", WRITE))
        self.assertTrue(selects(named(manifests, "Service", READ)["spec"]["selector"], read_labels),
                        "the read Service must select the read pods")
        self.assertFalse(selects(named(manifests, "Service", READ)["spec"]["selector"], write_labels),
                         "the read Service must not select the write pods")
        self.assertTrue(selects(named(manifests, "Service", WRITE)["spec"]["selector"], write_labels),
                        "the write Service must select the write pods")
        self.assertFalse(selects(named(manifests, "Service", WRITE)["spec"]["selector"], read_labels),
                         "the write Service must not select the read pods")

    def test_combined_service_selects_read_and_write_pods_together(self):
        manifests = render(scalable_values())
        selector = named(manifests, "Service", SERVER)["spec"]["selector"]
        for name in (READ, WRITE):
            self.assertTrue(selects(selector, pod_labels(named(manifests, "Deployment", name))),
                            f"the combined Service must select the {name} pods")

    def test_no_service_selects_the_worker_pods(self):
        manifests = render(scalable_values())
        worker_labels = pod_labels(named(manifests, "Deployment", WORKER))
        for service in of_kind(manifests, "Service"):
            self.assertFalse(selects(service["spec"]["selector"], worker_labels),
                             f"Service {service['metadata']['name']} must not route to the worker")

    def test_pod_selectors_of_all_deployments_are_pairwise_disjoint(self):
        manifests = render(scalable_values())
        selectors = {name: pod_selector(named(manifests, "Deployment", name))
                     for name in (READ, WRITE, WORKER, DASHBOARD)}
        for first, second in itertools.combinations(selectors, 2):
            self.assertTrue(disjoint(selectors[first], selectors[second]),
                            f"the pod selectors of {first} and {second} must not select the same pods")

    def test_combined_service_selects_no_dashboard_pods(self):
        manifests = render(scalable_values())
        selector = named(manifests, "Service", SERVER)["spec"]["selector"]
        self.assertFalse(selects(selector, pod_labels(named(manifests, "Deployment", DASHBOARD))),
                         "the combined Service must not select the dashboard pods")


if __name__ == "__main__":
    unittest.main()
