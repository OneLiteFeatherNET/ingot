"""Render tests for the opt-in JVM options, OpenTelemetry agent and shared settings wiring.

Each test renders the chart with `helm template` through the helpers of test_chart and names one behaviour. Every value
is opt-in: a render that sets none of them is covered by the golden renders in test_golden.py, and the tests below
assert the defaults again where the feature could leak into them.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import os
import unittest

import yaml
from test_chart import CHART_DIR, DASHBOARD, SERVER, container, helm, named, notes, pod_spec, render

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
STAGING_VALUES = os.path.join(TESTS_DIR, "fixtures", "staging-values.yaml")
AGENT_IMAGE = "ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:2.30.0"
AGENT_FLAG = "-javaagent:/otel/javaagent.jar"
SHARED_MOUNT = "/app/shared-config"
SHARED_FILE = "/app/shared-config/configuration.shared.json"
SHARED_VOLUME = "ingot-shared-configuration"
SHARED_CONFIGMAP = "ingot-shared-configuration"
SHARED_CONTENT = '{"settings": {"example": true}}\n'
DEFAULT_ENV = [{"name": "JAVA_OPTS", "value": "-Xmx64M"}]


def env_entries(deployment, name):
    return [entry for entry in container(deployment).get("env", []) if entry.get("name") == name]


def single_env(deployment, name):
    """Returns the one entry of the variable. Fails when the variable is set twice, because the last one wins silently."""
    entries = env_entries(deployment, name)
    if len(entries) != 1:
        raise AssertionError(f"the variable {name} is set {len(entries)} times, expected exactly once")
    return entries[0]


def volume_named(deployment, name):
    matches = [volume for volume in pod_spec(deployment).get("volumes", []) if volume.get("name") == name]
    return matches[0] if matches else None


def mount_named(deployment, name):
    matches = [mount for mount in container(deployment).get("volumeMounts", []) if mount.get("name") == name]
    return matches[0] if matches else None


def init_named(deployment, name):
    matches = [item for item in pod_spec(deployment).get("initContainers", []) if item.get("name") == name]
    return matches[0] if matches else None


def configmaps(manifests):
    return [doc for doc in manifests if doc.get("kind") == "ConfigMap"]


def opentelemetry_values(**overrides):
    values = {"enabled": True, "agentImage": AGENT_IMAGE}
    values.update(overrides)
    return {"opentelemetry": values}


def jvm_values(**overrides):
    return {"jvm": overrides}


def shared_values(**overrides):
    values = {"enabled": True}
    values.update(overrides)
    return {"sharedConfiguration": values}


class JvmOptionsTest(unittest.TestCase):

    def test_default_render_keeps_java_opts_and_renders_no_jdk_java_options(self):
        server = named(render(), "Deployment", SERVER)
        self.assertEqual([], env_entries(server, "JDK_JAVA_OPTIONS"),
                         "the default render must not set JDK_JAVA_OPTIONS")
        self.assertEqual("-Xmx64M", single_env(server, "JAVA_OPTS")["value"],
                         "the default JAVA_OPTS must stay unchanged")

    def test_max_ram_percentage_renders_the_flag_once_in_jdk_java_options(self):
        server = named(render(jvm_values(maxRAMPercentage=70)), "Deployment", SERVER)
        value = single_env(server, "JDK_JAVA_OPTIONS")["value"]
        tokens = value.split()
        self.assertEqual(1, tokens.count("-XX:MaxRAMPercentage=70"),
                         f"-XX:MaxRAMPercentage=70 must appear once in JDK_JAVA_OPTIONS, found: {value!r}")

    def test_jvm_flags_leave_java_opts_unchanged(self):
        server = named(render(jvm_values(maxRAMPercentage=70)), "Deployment", SERVER)
        self.assertEqual("-Xmx64M", single_env(server, "JAVA_OPTS")["value"],
                         "the jvm block must not edit JAVA_OPTS, the operator decides what to remove")

    def test_operator_jdk_java_options_with_the_same_flag_fails_and_names_it(self):
        values = {"env": [{"name": "JDK_JAVA_OPTIONS", "value": "-XX:MaxRAMPercentage=50"}]}
        values.update(jvm_values(maxRAMPercentage=70))
        code, _, stderr = helm(["template", "ingot", CHART_DIR, "--namespace", "reposilite-staging"], values)
        self.assertNotEqual(0, code, "a flag set twice must fail the render")
        self.assertIn("-XX:MaxRAMPercentage", stderr, "the failure must name the flag")

    def test_java_opts_with_xmx_renders_a_notes_warning_without_failing(self):
        text = notes(jvm_values(maxRAMPercentage=70))
        self.assertIn("-Xmx", text, "NOTES must warn that an explicit -Xmx overrides the percentage")
        self.assertIn("MaxRAMPercentage", text, "the warning must name the percentage flag")

    def test_no_jvm_warning_without_a_percentage(self):
        self.assertNotIn("overrides the percentage", notes(), "the default install must print no JVM warning")


class OpenTelemetryAgentTest(unittest.TestCase):

    def test_default_render_has_no_agent_init_container_volume_or_flag(self):
        manifests = render()
        server = named(manifests, "Deployment", SERVER)
        self.assertIsNone(init_named(server, "otel-agent"), "the default render must not add an agent init container")
        self.assertIsNone(volume_named(server, "otel-agent"), "the default render must not add an agent volume")
        for entry in container(server).get("env", []):
            self.assertNotIn("-javaagent", str(entry.get("value", "")), "the default render must not add the agent flag")

    def test_enabled_render_adds_the_init_container_with_the_configured_image(self):
        server = named(render(opentelemetry_values()), "Deployment", SERVER)
        init = init_named(server, "otel-agent")
        self.assertIsNotNone(init, "the enabled agent needs an init container named otel-agent")
        self.assertEqual(AGENT_IMAGE, init["image"], "the init container must run the configured agent image")

    def test_enabled_render_mounts_an_emptydir_read_only_at_otel_in_the_server(self):
        server = named(render(opentelemetry_values()), "Deployment", SERVER)
        volume = volume_named(server, "otel-agent")
        self.assertIsNotNone(volume, "the agent needs a volume named otel-agent")
        self.assertEqual({}, volume.get("emptyDir"), "the agent volume must be an emptyDir")
        mount = mount_named(server, "otel-agent")
        self.assertIsNotNone(mount, "the server container must mount the agent volume")
        self.assertEqual("/otel", mount["mountPath"], "the agent volume must be mounted at /otel")
        self.assertTrue(mount.get("readOnly"), "the server must mount the agent volume read-only")

    def test_enabled_render_puts_the_agent_flag_once_in_jdk_java_options(self):
        server = named(render(opentelemetry_values()), "Deployment", SERVER)
        value = single_env(server, "JDK_JAVA_OPTIONS")["value"]
        self.assertEqual(1, value.split().count(AGENT_FLAG), f"the agent flag must appear once, found: {value!r}")

    def test_operator_jdk_java_options_is_kept_and_extended(self):
        values = opentelemetry_values()
        values["env"] = DEFAULT_ENV + [{"name": "JDK_JAVA_OPTIONS", "value": "-Dfoo=bar"}]
        server = named(render(values), "Deployment", SERVER)
        entries = env_entries(server, "JDK_JAVA_OPTIONS")
        self.assertEqual(1, len(entries), "exactly one JDK_JAVA_OPTIONS entry must be rendered")
        self.assertTrue(entries[0]["value"].startswith("-Dfoo=bar"), "the operator text must come first, unchanged")
        self.assertIn(AGENT_FLAG, entries[0]["value"], "the agent flag must be appended")

    def test_otel_variables_come_from_values_with_logs_off_by_default(self):
        values = opentelemetry_values(
            serviceName="ingot-test", endpoint="http://collector.example.svc:4317", protocol="grpc",
        )
        server = named(render(values), "Deployment", SERVER)
        self.assertEqual("ingot-test", single_env(server, "OTEL_SERVICE_NAME")["value"])
        self.assertEqual("http://collector.example.svc:4317", single_env(server, "OTEL_EXPORTER_OTLP_ENDPOINT")["value"])
        self.assertEqual("grpc", single_env(server, "OTEL_EXPORTER_OTLP_PROTOCOL")["value"])
        self.assertEqual("none", single_env(server, "OTEL_LOGS_EXPORTER")["value"],
                         "the log exporter must default to none")

    def test_split_mode_puts_the_agent_on_the_server_only(self):
        values = opentelemetry_values()
        values["mode"] = "split"
        manifests = render(values)
        server = named(manifests, "Deployment", SERVER)
        dashboard = named(manifests, "Deployment", DASHBOARD)
        self.assertIsNotNone(init_named(server, "otel-agent"), "the server must carry the agent")
        self.assertIsNone(init_named(dashboard, "otel-agent"), "the dashboard must not carry the agent")
        self.assertIsNone(volume_named(dashboard, "otel-agent"), "the dashboard must not mount the agent volume")
        self.assertEqual([], env_entries(dashboard, "JDK_JAVA_OPTIONS"), "the dashboard must not get the agent flag")

    def test_enabled_values_render_the_staging_agent_wiring(self):
        with open(STAGING_VALUES, encoding="utf-8") as handle:
            staging = yaml.safe_load(handle)
        staging_server = staging["deployment"]["initContainers"][0]
        staging_env = {entry["name"]: entry.get("value") for entry in staging["env"] if "value" in entry}
        values = opentelemetry_values(
            serviceName=staging_env["OTEL_SERVICE_NAME"],
            endpoint=staging_env["OTEL_EXPORTER_OTLP_ENDPOINT"],
            protocol=staging_env["OTEL_EXPORTER_OTLP_PROTOCOL"],
            tracesExporter=staging_env["OTEL_TRACES_EXPORTER"],
            metricsExporter=staging_env["OTEL_METRICS_EXPORTER"],
            logsExporter=staging_env["OTEL_LOGS_EXPORTER"],
        )
        values["opentelemetry"]["agentImage"] = staging_server["image"]
        server = named(render(values), "Deployment", SERVER)
        init = init_named(server, "otel-agent")
        for key in ("image", "command", "securityContext", "resources", "volumeMounts"):
            self.assertEqual(staging_server[key], init[key],
                             f"the agent init container field {key} must match the staging fixture")
        for name in ("OTEL_SERVICE_NAME", "OTEL_TRACES_EXPORTER", "OTEL_METRICS_EXPORTER", "OTEL_LOGS_EXPORTER",
                     "OTEL_EXPORTER_OTLP_ENDPOINT", "OTEL_EXPORTER_OTLP_PROTOCOL"):
            self.assertEqual(staging_env[name], single_env(server, name)["value"],
                             f"{name} must match the staging fixture")


class SharedConfigurationTest(unittest.TestCase):

    def test_default_render_has_no_shared_configuration_object(self):
        manifests = render()
        server = named(manifests, "Deployment", SERVER)
        self.assertEqual([], [cm for cm in configmaps(manifests) if cm["metadata"]["name"] == SHARED_CONFIGMAP],
                         "the default render must not render the shared configuration ConfigMap")
        self.assertIsNone(volume_named(server, SHARED_VOLUME), "the default render must not mount the shared file")
        self.assertEqual([], env_entries(server, "INGOT_SHARED_CONFIGURATION_RELOAD"),
                         "the default render must not set the reload switch")

    def test_content_renders_one_configmap_with_the_shared_file_key(self):
        manifests = render(shared_values(content=SHARED_CONTENT))
        matches = [cm for cm in configmaps(manifests) if cm["metadata"]["name"] == SHARED_CONFIGMAP]
        self.assertEqual(1, len(matches), "content must render one ConfigMap for the shared file")
        self.assertEqual(SHARED_CONTENT, matches[0]["data"]["configuration.shared.json"],
                         "the ConfigMap must hold the content under configuration.shared.json")

    def test_existing_configmap_renders_no_configmap_and_names_the_volume_source(self):
        manifests = render(shared_values(existingConfigMap="shared-settings"))
        self.assertEqual([], [cm for cm in configmaps(manifests) if cm["metadata"]["name"] == SHARED_CONFIGMAP],
                         "an existing ConfigMap must not be duplicated by the chart")
        volume = volume_named(named(manifests, "Deployment", SERVER), SHARED_VOLUME)
        self.assertEqual("shared-settings", volume["configMap"]["name"],
                         "the volume must name the existing ConfigMap")

    def test_enabled_without_content_or_existing_configmap_fails_and_names_both_keys(self):
        code, _, stderr = helm(["template", "ingot", CHART_DIR, "--namespace", "reposilite-staging"],
                               shared_values())
        self.assertNotEqual(0, code, "enabled shared settings need one source")
        self.assertIn("sharedConfiguration.content", stderr, "the failure must name content")
        self.assertIn("sharedConfiguration.existingConfigMap", stderr, "the failure must name existingConfigMap")

    def test_shared_file_is_mounted_read_only_as_a_directory_without_subpath(self):
        server = named(render(shared_values(content=SHARED_CONTENT)), "Deployment", SERVER)
        mount = mount_named(server, SHARED_VOLUME)
        self.assertEqual(SHARED_MOUNT, mount["mountPath"], "the shared file must be mounted at /app/shared-config")
        self.assertTrue(mount.get("readOnly"), "the shared mount must be read-only")
        self.assertNotIn("subPath", mount, "the shared mount must not use subPath, which never receives updates")

    def test_data_volume_keeps_its_mount_when_the_shared_file_is_enabled(self):
        server = named(render(shared_values(content=SHARED_CONTENT)), "Deployment", SERVER)
        data = mount_named(server, "data")
        self.assertEqual("/app/data", data["mountPath"], "the data volume must stay at /app/data")
        self.assertFalse(data.get("readOnly", False), "the data volume must stay writable")

    def test_enabled_render_sets_the_reload_switch_and_the_shared_file_argument(self):
        server = named(render(shared_values(content=SHARED_CONTENT)), "Deployment", SERVER)
        self.assertEqual("true", single_env(server, "INGOT_SHARED_CONFIGURATION_RELOAD")["value"],
                         "the server must get the reload switch")
        options = single_env(server, "INGOT_OPTS")["value"].split()
        self.assertEqual(1, options.count("--shared-configuration"),
                         "INGOT_OPTS must carry --shared-configuration once")
        self.assertEqual(SHARED_FILE, options[options.index("--shared-configuration") + 1],
                         "the argument must point at the mounted file")

    def test_operator_opts_are_kept_next_to_the_shared_file_argument(self):
        values = shared_values(content=SHARED_CONTENT)
        values["env"] = DEFAULT_ENV + [{"name": "INGOT_OPTS", "value": "--port 9090"}]
        server = named(render(values), "Deployment", SERVER)
        options = single_env(server, "INGOT_OPTS")["value"].split()
        self.assertEqual(["--port", "9090"], options[:2], "the operator arguments must come first, unchanged")
        self.assertIn("--shared-configuration", options, "the shared file argument must be added")

    def test_reposilite_opts_are_kept_when_the_chart_writes_ingot_opts(self):
        values = shared_values(content=SHARED_CONTENT)
        values["env"] = DEFAULT_ENV + [{"name": "REPOSILITE_OPTS", "value": "--port 9090"}]
        server = named(render(values), "Deployment", SERVER)
        options = single_env(server, "INGOT_OPTS")["value"].split()
        self.assertEqual(["--port", "9090"], options[:2],
                         "the entrypoint reads REPOSILITE_OPTS only when INGOT_OPTS is unset, so its arguments move over")

    def test_shared_file_argument_set_by_the_operator_fails_the_render(self):
        values = shared_values(content=SHARED_CONTENT)
        values["env"] = DEFAULT_ENV + [{"name": "INGOT_OPTS", "value": "--shared-configuration /other.json"}]
        code, _, stderr = helm(["template", "ingot", CHART_DIR, "--namespace", "reposilite-staging"], values)
        self.assertNotEqual(0, code, "a second shared file argument must fail the render")
        self.assertIn("--shared-configuration", stderr, "the failure must name the argument")

    def test_checksum_annotation_changes_with_the_content(self):
        first = named(render(shared_values(content=SHARED_CONTENT)), "Deployment", SERVER)
        second = named(render(shared_values(content='{"settings": {"example": false}}\n')), "Deployment", SERVER)
        key = "checksum/shared-configuration"
        self.assertIn(key, first["spec"]["template"]["metadata"]["annotations"], "the checksum must be set")
        self.assertNotEqual(first["spec"]["template"]["metadata"]["annotations"][key],
                            second["spec"]["template"]["metadata"]["annotations"][key],
                            "a changed content must change the checksum and restart the pods")

    def test_existing_configmap_sets_no_checksum_annotation(self):
        server = named(render(shared_values(existingConfigMap="shared-settings")), "Deployment", SERVER)
        annotations = server["spec"]["template"]["metadata"].get("annotations") or {}
        self.assertNotIn("checksum/shared-configuration", annotations,
                         "an existing ConfigMap must not restart the pods, the reload applies the change")

    def test_dashboard_gets_no_shared_configuration_wiring(self):
        values = shared_values(content=SHARED_CONTENT)
        values["mode"] = "split"
        dashboard = named(render(values), "Deployment", DASHBOARD)
        self.assertIsNone(volume_named(dashboard, SHARED_VOLUME), "the dashboard must not mount the shared file")
        self.assertEqual([], env_entries(dashboard, "INGOT_SHARED_CONFIGURATION_RELOAD"),
                         "the dashboard must not get the reload switch")


if __name__ == "__main__":
    unittest.main()
