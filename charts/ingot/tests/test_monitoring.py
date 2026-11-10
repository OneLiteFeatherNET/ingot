"""Render tests for the opt-in PrometheusRule and the Grafana dashboard ConfigMaps.

The rules and the dashboards may query only metric names that the image exports, listed in
fixtures/exported-metrics.txt. The promtool check runs when a promtool binary is named by the PROMTOOL environment
variable, and is skipped otherwise; the structure of the rules is checked in every run.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import json
import os
import re
import shutil
import subprocess
import tempfile
import unittest

import yaml
from test_chart import CHART_DIR, named, of_kind, render

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
EXPORTED = os.path.join(TESTS_DIR, "fixtures", "exported-metrics.txt")
DASHBOARD_DIR = os.path.join(CHART_DIR, "files", "dashboards")
BASE_ALERTS = {
    "IngotDown",
    "IngotHighErrorRatio",
    "IngotUpstreamUnreachable",
    "IngotDeployRejections",
    "IngotHeapPressure",
    "IngotClusterBusDisconnected",
}
DASHBOARD_LABEL = "grafana_dashboard"
DASHBOARD_NAMES = {"overview", "mirroring", "jvm"}
PROMQL_KEYWORDS = {
    "and", "or", "unless", "bool", "offset", "by", "without", "on", "ignoring", "group_left", "group_right",
    "inf", "nan",
}


def exported_metrics():
    names = set()
    with open(EXPORTED, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line and not line.startswith("#"):
                names.add(line)
    return names


def metric_names(expression):
    """Returns the metric names of a PromQL expression. Label matchers, ranges, strings, function calls and keywords are not
    metric names, and the label lists of by, without, on and ignoring are dropped before the scan."""
    text = re.sub(r'"[^"]*"', '""', expression)
    text = re.sub(r"\{[^{}]*\}", "{}", text)
    text = re.sub(r"\[[^\[\]]*\]", "[]", text)
    text = re.sub(r"\b(by|without|on|ignoring|group_left|group_right)\s*\([^()]*\)", " ", text)
    names = set()
    for match in re.finditer(r"(?<![A-Za-z0-9_:.$])([A-Za-z_:][A-Za-z0-9_:]*)(\s*\()?", text):
        name, call = match.group(1), match.group(2)
        if call or name in PROMQL_KEYWORDS:
            continue
        names.add(name)
    return names


def balanced(expression):
    depth = 0
    for char in expression:
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth < 0:
                return False
    return depth == 0


def prometheus_rule(values=None):
    """Renders the chart with prometheusRule enabled and returns the PrometheusRule of the release."""
    overrides = {"prometheusRule": {"enabled": True}}
    if values:
        overrides["prometheusRule"].update(values)
    return named(render(overrides), "PrometheusRule", "ingot")


def all_rules(rule_object):
    return [rule for group in rule_object["spec"]["groups"] for rule in group["rules"]]


def dashboard_configmaps(manifests):
    return [doc for doc in of_kind(manifests, "ConfigMap") if DASHBOARD_LABEL in doc["metadata"].get("labels", {})]


def panel_expressions(dashboard):
    """Yields every PromQL expression of the panels of a dashboard. The template variables are not checked: their
    label_values queries name label keys, which are not metric names."""
    for panel in dashboard.get("panels", []):
        for target in panel.get("targets", []):
            if "expr" in target:
                yield target["expr"]
        for nested in panel.get("panels", []):
            for target in nested.get("targets", []):
                if "expr" in target:
                    yield target["expr"]


class PrometheusRuleTest(unittest.TestCase):

    def test_default_render_has_no_prometheus_rule(self):
        self.assertEqual([], of_kind(render(), "PrometheusRule"),
                         "the default render must not render a PrometheusRule")

    def test_enabled_render_has_exactly_the_base_alerts(self):
        names = {rule["alert"] for rule in all_rules(prometheus_rule())}
        self.assertEqual(BASE_ALERTS, names, "the base alert set changed")

    def test_every_rule_has_an_expression_a_duration_and_a_severity(self):
        for rule in all_rules(prometheus_rule()):
            self.assertTrue(rule.get("expr"), f"{rule.get('alert')} has no expression")
            self.assertRegex(str(rule.get("for", "")), r"^\d+[smh]$", f"{rule['alert']} needs a 'for' duration")
            self.assertIn(rule.get("labels", {}).get("severity"), {"warning", "critical"},
                          f"{rule['alert']} needs a severity label of warning or critical")

    def test_every_rule_has_a_summary_and_a_description_annotation(self):
        for rule in all_rules(prometheus_rule()):
            annotations = rule.get("annotations", {})
            self.assertTrue(annotations.get("summary"), f"{rule['alert']} needs a summary annotation")
            self.assertTrue(annotations.get("description"), f"{rule['alert']} needs a description annotation")

    def test_every_expression_has_balanced_parentheses(self):
        for rule in all_rules(prometheus_rule()):
            self.assertTrue(balanced(rule["expr"]), f"{rule['alert']} has unbalanced parentheses: {rule['expr']}")

    def test_rule_expressions_name_only_exported_metrics(self):
        exported = exported_metrics()
        for rule in all_rules(prometheus_rule()):
            unknown = metric_names(rule["expr"]) - exported
            self.assertEqual(set(), unknown,
                             f"{rule['alert']} names metrics that the image does not export: {sorted(unknown)}")

    def test_rule_groups_are_named_and_not_empty(self):
        groups = prometheus_rule()["spec"]["groups"]
        self.assertTrue(groups, "the PrometheusRule must have at least one group")
        for group in groups:
            self.assertTrue(group.get("name"), "every group needs a name")
            self.assertTrue(group.get("rules"), f"group {group.get('name')} has no rules")

    def test_extra_labels_are_added_to_the_rule_object(self):
        labels = prometheus_rule({"labels": {"release": "kube-prometheus"}})["metadata"]["labels"]
        self.assertEqual("kube-prometheus", labels.get("release"),
                         "prometheusRule.labels must reach the metadata so the Prometheus instance selects the rule")

    def test_rules_are_checked_with_promtool_when_it_is_available(self):
        promtool = os.environ.get("PROMTOOL") or shutil.which("promtool")
        if not promtool:
            self.skipTest("promtool is not installed; set PROMTOOL to a binary to run the check")
        rule_object = prometheus_rule()
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "rules.yaml")
            with open(path, "w", encoding="utf-8") as handle:
                yaml.safe_dump({"groups": rule_object["spec"]["groups"]}, handle)
            result = subprocess.run([promtool, "check", "rules", path], capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, f"promtool rejected the rules: {result.stdout}{result.stderr}")


class GrafanaDashboardTest(unittest.TestCase):

    def test_default_render_has_no_dashboard_configmap(self):
        self.assertEqual([], dashboard_configmaps(render()),
                         "the default render must not render a dashboard ConfigMap")

    def test_enabled_render_has_one_configmap_per_dashboard(self):
        maps = dashboard_configmaps(render({"grafanaDashboards": {"enabled": True}}))
        self.assertEqual(3, len(maps), "the overview, mirroring and JVM dashboards must each be one ConfigMap")
        suffixes = {doc["metadata"]["name"].rsplit("-grafana-", 1)[-1] for doc in maps}
        self.assertEqual(DASHBOARD_NAMES, suffixes, "the dashboard ConfigMap names changed")

    def test_each_dashboard_configmap_holds_one_valid_json_dashboard(self):
        for doc in dashboard_configmaps(render({"grafanaDashboards": {"enabled": True}})):
            self.assertEqual("1", doc["metadata"]["labels"][DASHBOARD_LABEL],
                             f"{doc['metadata']['name']} must carry the label {DASHBOARD_LABEL}: \"1\"")
            self.assertEqual(1, len(doc["data"]), f"{doc['metadata']['name']} must hold one dashboard file")
            dashboard = json.loads(next(iter(doc["data"].values())))
            self.assertTrue(dashboard.get("title"), f"{doc['metadata']['name']} needs a title")
            self.assertTrue(dashboard.get("panels"), f"{doc['metadata']['name']} needs panels")

    def test_dashboard_queries_name_only_exported_metrics(self):
        exported = exported_metrics()
        for doc in dashboard_configmaps(render({"grafanaDashboards": {"enabled": True}})):
            dashboard = json.loads(next(iter(doc["data"].values())))
            for expression in panel_expressions(dashboard):
                unknown = metric_names(expression) - exported
                self.assertEqual(set(), unknown,
                                 f"{doc['metadata']['name']} queries metrics that the image does not export: "
                                 f"{sorted(unknown)} in {expression}")

    def test_dashboard_files_are_the_ones_the_chart_renders(self):
        files = sorted(os.listdir(DASHBOARD_DIR))
        self.assertEqual(sorted(f"{name}.json" for name in DASHBOARD_NAMES), files,
                         "charts/ingot/files/dashboards must hold exactly the three dashboards")


class MetricNameParserTest(unittest.TestCase):

    def test_plain_selector_names_its_metric(self):
        self.assertEqual({"up"}, metric_names('up{namespace="x"} == 0'))

    def test_label_names_and_function_names_are_not_metrics(self):
        names = metric_names('sum by (le, code) (rate(ingot_http_server_requests_total{code=~"5.."}[5m]))')
        self.assertEqual({"ingot_http_server_requests_total"}, names)

    def test_range_durations_are_not_metrics(self):
        self.assertEqual({"ingot_cluster_connected"}, metric_names("max_over_time(ingot_cluster_connected[5m]) == 0"))


if __name__ == "__main__":
    unittest.main()
