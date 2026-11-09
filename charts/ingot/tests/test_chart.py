"""Render tests for the Ingot Helm chart.

Every test renders the chart with `helm template` and asserts on the parsed manifests. No cluster, no network,
no clock: each test writes its own values file into a temporary directory and names one behaviour.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

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

    def test_all_in_one_probes_use_the_root_path(self):
        deployment = named(render(), "Deployment", SERVER)
        for probe in ("readinessProbe", "livenessProbe"):
            self.assertEqual("/", container(deployment)[probe]["httpGet"]["path"],
                             f"the all-in-one {probe} must use the root path as upstream does")

    def test_startup_probe_is_absent_by_default(self):
        for values in (None, split_values()):
            deployment = named(render(values), "Deployment", SERVER)
            self.assertNotIn("startupProbe", container(deployment), "no startup probe unless it is enabled")

    def test_enabled_startup_probe_uses_the_root_path_in_all_in_one_mode(self):
        deployment = named(render({"probes": {"startup": {"enabled": True}}}), "Deployment", SERVER)
        self.assertEqual(
            "/", container(deployment)["startupProbe"]["httpGet"]["path"],
            "the all-in-one startup probe must use the root path, like the other server probes",
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

    def test_split_server_probes_use_the_api_health_endpoint(self):
        deployment = named(render(split_values()), "Deployment", SERVER)
        for probe in ("readinessProbe", "livenessProbe"):
            self.assertEqual("/api/status/health", container(deployment)[probe]["httpGet"]["path"],
                             f"the split server {probe} must avoid the dashboard path")

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
        self.assertNotIn("WARNING", notes(values), "a configured INGOT_CLUSTER_URL must silence the warning")

    def test_helm_lint_is_clean_in_all_in_one_mode(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict"], {"mode": "allInOne"})
        self.assertEqual(0, code, f"helm lint must pass in allInOne mode: {stdout}{stderr}")

    def test_helm_lint_is_clean_in_split_mode(self):
        code, stdout, stderr = helm(["lint", CHART_DIR, "--strict"], split_values())
        self.assertEqual(0, code, f"helm lint must pass in split mode: {stdout}{stderr}")


if __name__ == "__main__":
    unittest.main()
