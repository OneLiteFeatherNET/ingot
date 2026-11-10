"""Render tests for the opt-in NetworkPolicy of the server and the dashboard.

The policy is `networkPolicy`: off by default, an ingress allow-list by label, and a separate egress switch. Each test
renders the chart with `helm template` through the helpers of test_chart and names one behaviour. Nothing runs on a
cluster here, so enforcement is not part of these tests.

Run from the repository root:

    python3 -m unittest discover -s charts/ingot/tests -t charts/ingot/tests -v
"""

import unittest

from test_chart import CHART_DIR, DASHBOARD, RELEASE, SERVER, helm, named, of_kind, render, split_values

NAMESPACE_LABEL = "kubernetes.io/metadata.name"
GATEWAY = {NAMESPACE_LABEL: "gateway-system"}
MONITORING = {NAMESPACE_LABEL: "monitoring"}
PRIVATE_RANGES = ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"]
DNS_PORTS = [{"port": 53, "protocol": "UDP"}, {"port": 53, "protocol": "TCP"}]
SERVER_LABELS = {"app.kubernetes.io/name": "ingot", "app.kubernetes.io/instance": RELEASE}
DASHBOARD_LABELS = {"app.kubernetes.io/name": "ingot", "app.kubernetes.io/instance": f"{RELEASE}-dashboard"}


def enabled(**network):
    values = {"networkPolicy": {"enabled": True}}
    values["networkPolicy"].update(network)
    return values


def policy(values, name=SERVER):
    return named(render(values), "NetworkPolicy", name)


def service_port(manifests, name):
    return named(manifests, "Service", name)["spec"]["ports"][0]["port"]


def peers_of(rule):
    return rule.get("from", [])


class DefaultRenderTest(unittest.TestCase):

    def test_default_render_has_no_network_policy(self):
        self.assertEqual([], of_kind(render(), "NetworkPolicy"),
                         "networkPolicy.enabled defaults to false, so no NetworkPolicy may render")

    def test_split_default_render_has_no_network_policy(self):
        self.assertEqual([], of_kind(render(split_values()), "NetworkPolicy"),
                         "split mode must render no NetworkPolicy unless networkPolicy.enabled is true")


class ServerPolicyTest(unittest.TestCase):

    def test_enabled_renders_one_policy_named_after_the_server(self):
        policies = of_kind(render(enabled()), "NetworkPolicy")
        self.assertEqual([SERVER], [p["metadata"]["name"] for p in policies],
                         "an enabled allInOne render must contain exactly the server policy")

    def test_server_policy_selects_the_server_pods_by_selector_labels(self):
        self.assertEqual(SERVER_LABELS, policy(enabled())["spec"]["podSelector"]["matchLabels"],
                         "the server policy must select the pods of the server selector labels")

    def test_policy_types_are_ingress_only_when_egress_is_off(self):
        self.assertEqual(["Ingress"], policy(enabled())["spec"]["policyTypes"],
                         "without the egress switch the policy must not list Egress")

    def test_ingress_allows_only_the_listed_namespace_on_the_server_port(self):
        values = enabled(ingress={"fromNamespaces": [GATEWAY]})
        manifests = render(values)
        port = service_port(manifests, SERVER)
        rules = named(manifests, "NetworkPolicy", SERVER)["spec"]["ingress"]
        self.assertEqual(1, len(rules), "one listed namespace must render one ingress rule")
        self.assertEqual([{"port": port, "protocol": "TCP"}], rules[0]["ports"],
                         "the ingress rule must allow only the server port")
        self.assertEqual([{"namespaceSelector": {"matchLabels": GATEWAY}}], peers_of(rules[0]),
                         "the ingress rule must allow only the listed namespace")

    def test_ingress_allows_a_listed_pod_selector_in_the_release_namespace(self):
        values = enabled(ingress={"fromPods": [{"app.kubernetes.io/name": "prometheus"}]})
        rules = policy(values)["spec"]["ingress"]
        self.assertEqual([{"podSelector": {"matchLabels": {"app.kubernetes.io/name": "prometheus"}}}],
                         peers_of(rules[0]), "a listed pod selector must render as a pod peer")

    def test_without_listed_peers_no_ingress_rule_renders(self):
        spec = policy(enabled())["spec"]
        self.assertNotIn("ingress", spec,
                         "with no listed peer the policy must not render an ingress rule, because an empty "
                         "from list admits every source")

    def test_empty_namespace_selector_fails_the_render(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], enabled(ingress={"fromNamespaces": [{}]}))
        self.assertNotEqual(0, code, "an empty namespace selector would match every namespace")
        self.assertIn("networkPolicy.ingress.fromNamespaces", stderr,
                      "the message must name networkPolicy.ingress.fromNamespaces")

    def test_allinone_renders_no_dashboard_policy(self):
        self.assertEqual([], [p for p in of_kind(render(enabled()), "NetworkPolicy")
                              if p["metadata"]["name"] == DASHBOARD],
                         "allInOne has no dashboard workload, so it must render no dashboard policy")


class SplitPolicyTest(unittest.TestCase):

    def test_split_renders_a_dashboard_policy(self):
        names = sorted(p["metadata"]["name"] for p in of_kind(render(dict(split_values(), **enabled())),
                                                              "NetworkPolicy"))
        self.assertEqual(sorted([SERVER, DASHBOARD]), names, "split mode must render the server and dashboard policies")

    def test_dashboard_policy_selects_the_dashboard_pods(self):
        values = dict(split_values(), **enabled())
        self.assertEqual(DASHBOARD_LABELS, policy(values, DASHBOARD)["spec"]["podSelector"]["matchLabels"],
                         "the dashboard policy must select the dashboard selector labels")

    def test_dashboard_policy_allows_the_listed_namespace_on_the_dashboard_port(self):
        values = dict(split_values(), **enabled(ingress={"fromNamespaces": [GATEWAY]}))
        manifests = render(values)
        port = service_port(manifests, DASHBOARD)
        rules = named(manifests, "NetworkPolicy", DASHBOARD)["spec"]["ingress"]
        self.assertEqual([{"port": port, "protocol": "TCP"}], rules[0]["ports"],
                         "the dashboard policy must allow the dashboard port")
        self.assertEqual([{"namespaceSelector": {"matchLabels": GATEWAY}}], peers_of(rules[0]),
                         "the gateway namespace must reach the dashboard")

    def test_server_policy_admits_the_dashboard_pods_on_the_server_port(self):
        values = dict(split_values(), **enabled())
        manifests = render(values)
        port = service_port(manifests, SERVER)
        rules = named(manifests, "NetworkPolicy", SERVER)["spec"]["ingress"]
        dashboard_rules = [r for r in rules if {"podSelector": {"matchLabels": DASHBOARD_LABELS}} in peers_of(r)]
        self.assertEqual(1, len(dashboard_rules), "the server policy must admit the dashboard pods exactly once")
        self.assertEqual([{"port": port, "protocol": "TCP"}], dashboard_rules[0]["ports"],
                         "the dashboard pods must reach the server port only")

    def test_server_policy_does_not_open_the_dashboard_port(self):
        values = dict(split_values(), dashboard={"service": {"port": 9090}},
                      **enabled(ingress={"fromNamespaces": [GATEWAY]}))
        dashboard_port = service_port(render(values), DASHBOARD)
        self.assertNotEqual(service_port(render(values), SERVER), dashboard_port,
                            "the ports must differ, or the test cannot tell the two policies apart")
        for rule in policy(values)["spec"]["ingress"]:
            self.assertNotIn({"port": dashboard_port, "protocol": "TCP"}, rule["ports"],
                             "the server policy must not open the dashboard port")


class EgressTest(unittest.TestCase):

    def egress_rules(self, values, name=SERVER):
        return policy(values, name)["spec"]["egress"]

    def test_egress_policy_type_is_added_when_egress_is_on(self):
        values = enabled(egress={"enabled": True})
        self.assertEqual(["Ingress", "Egress"], policy(values)["spec"]["policyTypes"],
                         "the egress switch must add Egress to policyTypes")

    def test_egress_allows_dns_to_any_destination(self):
        rules = self.egress_rules(enabled(egress={"enabled": True}))
        dns = [r for r in rules if r.get("ports") == DNS_PORTS]
        self.assertEqual(1, len(dns), "exactly one egress rule must allow DNS on UDP and TCP port 53")
        self.assertNotIn("to", dns[0], "the DNS rule must not limit the destination")

    def test_public_ranges_are_allowed_with_the_private_ranges_excepted(self):
        rules = self.egress_rules(enabled(egress={"enabled": True, "allowInternet": True}))
        ipv4 = [b["ipBlock"] for r in rules for b in r.get("to", []) if b.get("ipBlock", {}).get("cidr") == "0.0.0.0/0"]
        self.assertEqual([{"cidr": "0.0.0.0/0", "except": PRIVATE_RANGES}], ipv4,
                         "allowInternet must allow 0.0.0.0/0 except the three RFC 1918 ranges")

    def test_public_ipv6_is_allowed_with_the_unique_local_range_excepted(self):
        rules = self.egress_rules(enabled(egress={"enabled": True, "allowInternet": True}))
        ipv6 = [b["ipBlock"] for r in rules for b in r.get("to", []) if b.get("ipBlock", {}).get("cidr") == "::/0"]
        self.assertEqual([{"cidr": "::/0", "except": ["fc00::/7"]}], ipv6,
                         "allowInternet must allow ::/0 except fc00::/7")

    def test_without_internet_only_dns_and_extra_rules_remain(self):
        extra = [{"to": [{"ipBlock": {"cidr": "10.0.4.0/24"}}], "ports": [{"port": 5432, "protocol": "TCP"}]}]
        rules = self.egress_rules(enabled(egress={"enabled": True, "allowInternet": False, "extra": extra}))
        self.assertEqual(2, len(rules), "without allowInternet the policy must hold DNS and the extra rules only")
        self.assertIn({"ports": DNS_PORTS}, rules, "DNS must remain allowed")
        self.assertIn(extra[0], rules, "the extra rule must be rendered unchanged")

    def test_without_internet_no_public_range_is_allowed(self):
        rules = self.egress_rules(enabled(egress={"enabled": True, "allowInternet": False}))
        cidrs = [b.get("ipBlock", {}).get("cidr") for r in rules for b in r.get("to", [])]
        self.assertNotIn("0.0.0.0/0", cidrs, "allowInternet false must not allow the public IPv4 range")

    def test_split_dashboard_keeps_its_path_to_the_server_when_egress_is_restricted(self):
        values = dict(split_values(), **enabled(egress={"enabled": True, "allowInternet": False}))
        port = service_port(render(values), SERVER)
        rules = self.egress_rules(values, DASHBOARD)
        reaches_server = [r for r in rules
                          if {"podSelector": {"matchLabels": SERVER_LABELS}} in r.get("to", [])
                          and r.get("ports") == [{"port": port, "protocol": "TCP"}]]
        self.assertEqual(1, len(reaches_server),
                         "the dashboard must keep egress to the server pods on the server port")

    def test_extra_rules_render_on_the_server_policy_only(self):
        extra = [{"to": [{"ipBlock": {"cidr": "10.0.4.0/24"}}]}]
        values = dict(split_values(), **enabled(egress={"enabled": True, "allowInternet": False, "extra": extra}))
        self.assertNotIn(extra[0], self.egress_rules(values, DASHBOARD),
                         "the extra rules are the server's own destinations and must not reach the dashboard")


class SchemaTest(unittest.TestCase):

    def test_string_enabled_is_rejected(self):
        code, _, stderr = helm(["template", RELEASE, CHART_DIR], {"networkPolicy": {"enabled": "yes"}})
        self.assertNotEqual(0, code, "networkPolicy.enabled must be a boolean")
        self.assertIn("enabled", stderr, "the schema error must name the enabled key")


if __name__ == "__main__":
    unittest.main()
