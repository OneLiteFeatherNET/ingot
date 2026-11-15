# Spec Delta

## Purpose

An operator can restrict which pods reach Ingot through the chart, and nothing is restricted unless the operator asks for it.

## ADDED Requirements

### Requirement: Network policy is off by default

The chart SHALL render no NetworkPolicy unless `networkPolicy.enabled` is `true`. With the value unset, the rendered manifests
SHALL be the same as before this change.

#### Scenario: Default render

- **WHEN** the chart is rendered with the default values
- **THEN** no manifest of kind `NetworkPolicy` is rendered and the allInOne manifests match the render of the previous chart

### Requirement: Server ingress is limited to the listed namespaces

When enabled, the chart SHALL render a NetworkPolicy that selects the server pods by the server selector labels and allows
ingress to the server port only from the namespace selectors in `networkPolicy.ingress.fromNamespaces` and the pod selectors
in `networkPolicy.ingress.fromPods`. Traffic from other pods SHALL be refused.

#### Scenario: Listed namespace

- **WHEN** `networkPolicy.enabled=true` and `fromNamespaces` lists a namespace labelled `kubernetes.io/metadata.name: gateway-system`
- **THEN** the server policy allows ingress on the server port from that namespace selector

#### Scenario: Unlisted pod

- **WHEN** a pod in a namespace that no entry of `fromNamespaces` selects connects to the server port on an enforcing CNI
- **THEN** the connection is refused

### Requirement: Split mode protects the dashboard and admits its traffic to the server

In split mode the chart SHALL render a second NetworkPolicy for the dashboard pods, selected by the dashboard selector labels,
that applies the same ingress allow-list to the dashboard port. The server policy SHALL also allow ingress on the server port
from the dashboard pods.

#### Scenario: Dashboard reaches the server

- **WHEN** `mode=split` and `networkPolicy.enabled=true`
- **THEN** the dashboard can reach the server Service on the server port, and the server policy allows that traffic

#### Scenario: Gateway reaches the dashboard

- **WHEN** `mode=split`, `networkPolicy.enabled=true` and the gateway namespace is listed in `fromNamespaces`
- **THEN** the dashboard policy allows ingress from the gateway namespace on the dashboard port

### Requirement: Egress restriction is a separate opt-in

The chart SHALL restrict egress only when `networkPolicy.egress.enabled` is `true`. When restricted, the policy SHALL allow DNS
and the rules listed under `networkPolicy.egress.extra`. When `networkPolicy.egress.allowInternet` is `true`, the policy SHALL
also allow the public address ranges, excluding `10.0.0.0/8`, `172.16.0.0/12` and `192.168.0.0/16` for IPv4 and `fc00::/7` for
IPv6. Without the egress switch the policy SHALL NOT list `Egress` in `policyTypes`.

#### Scenario: Egress not restricted

- **WHEN** `networkPolicy.enabled=true` and `networkPolicy.egress.enabled` is `false`
- **THEN** the policy's `policyTypes` contains `Ingress` only

#### Scenario: Mirror proxying keeps internet access

- **WHEN** `networkPolicy.egress.enabled=true` and `allowInternet` is `true`
- **THEN** the policy allows egress to public addresses and does not allow egress to the excluded private ranges unless an `extra` rule names them

#### Scenario: Internal services need an explicit rule

- **WHEN** `networkPolicy.egress.enabled=true` and `allowInternet` is `false`
- **THEN** the policy allows only DNS and the `extra` rules

### Requirement: Probes and metrics keep working under enforcement

With enforcement on, the readiness and liveness probes of the server and the dashboard MUST succeed, and the metrics endpoint MUST
be reachable from a namespace listed in `fromNamespaces`.

#### Scenario: Probes under enforcement

- **WHEN** the chart runs on kind with an enforcing CNI and the policy is enabled
- **THEN** the readiness probe of the server reports ready and the monitoring namespace scrapes `/metrics` successfully
