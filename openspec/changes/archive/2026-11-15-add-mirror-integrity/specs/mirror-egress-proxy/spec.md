# Spec Delta

## Purpose

Mirror clients in a cluster can leave through the egress proxy that the environment names, without a setting per mirror.

## ADDED Requirements

### Requirement: Environment proxy switch

The switch SHALL be `INGOT_HTTP_USE_ENV_PROXY`, read with `REPOSILITE_HTTP_USE_ENV_PROXY` as fallback, and SHALL default to
`false`. While the switch is off, mirror clients SHALL use only the per-mirror `httpProxy` and the JVM defaults, exactly as
today.

#### Scenario: Switch off

- **WHEN** `HTTPS_PROXY` is set, the switch is off, and a mirror has no `httpProxy`
- **THEN** the mirror request does not go through that proxy

#### Scenario: Switch on for an https mirror

- **WHEN** the switch is on, `HTTPS_PROXY` names a proxy, and a mirror with an https URL has no `httpProxy`
- **THEN** the request goes through the proxy named by `HTTPS_PROXY`

#### Scenario: Host in NO_PROXY goes direct

- **WHEN** the switch is on, a proxy is set, and `NO_PROXY` lists the mirror host
- **THEN** the request goes directly to the mirror

#### Scenario: Per-mirror proxy wins

- **WHEN** the switch is on and a mirror has an `httpProxy` value
- **THEN** the request goes through the per-mirror proxy and the environment is not consulted

### Requirement: Proxy values with credentials are refused

A proxy value from the environment that carries a user name or a password SHALL be refused, with a message that names the
variable and not the value. Proxy values and credentials SHALL NOT appear in any log line.

#### Scenario: Credentials in the environment

- **WHEN** the switch is on and `HTTPS_PROXY` contains a user name and a password
- **THEN** the mirror request fails with a message that names `HTTPS_PROXY`, and the log holds neither the user name nor the password

#### Scenario: Logs during a proxied request

- **WHEN** a mirror request goes through a proxy from the environment
- **THEN** no log line contains the proxy address
