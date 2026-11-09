# Spec Delta

## Purpose

One place decides the client address of a request, and an operator can tell Ingot which proxies to believe.

## ADDED Requirements

### Requirement: Trusted proxy list

The server SHALL accept an optional list of trusted proxies as addresses and CIDR ranges, IPv4 and IPv6, from the system
property `ingot.trustedProxies` and the environment variable `INGOT_TRUSTED_PROXIES`, comma separated. A value that does not
parse SHALL stop the server at start with a message naming the offending entry.

#### Scenario: Invalid entry

- **WHEN** the server starts with `INGOT_TRUSTED_PROXIES=10.0.0.0/8,nonsense`
- **THEN** the start is refused and the message names `nonsense`

### Requirement: Behaviour without a list is unchanged

When no trusted proxy list is set, the server SHALL resolve the client address as before: the value of the configured
forwarded header when present, else the peer address. The server SHALL log one warning at start when the forwarded header is
in use and the list is empty.

#### Scenario: Unset list

- **WHEN** no list is set and a request arrives with `X-Forwarded-For: 203.0.113.9`
- **THEN** the client address is `203.0.113.9`, and the start logged one warning

### Requirement: Forwarded header only from trusted peers

When a list is set, the server SHALL honour the forwarded header only when the direct peer is in the list, and SHALL use the
rightmost entry of the chain that is not itself trusted. An entry that is not an address SHALL end the walk and be treated
as untrusted.

#### Scenario: Untrusted peer sends the header

- **WHEN** the list is `10.0.0.0/8` and a request from peer `198.51.100.7` carries `X-Forwarded-For: 203.0.113.9`
- **THEN** the client address is `198.51.100.7`

#### Scenario: Spoofed prefix behind a trusted proxy

- **WHEN** the list is `10.0.0.0/8` and a request from peer `10.0.0.2` carries `X-Forwarded-For: 1.1.1.1, 203.0.113.9, 10.0.0.5`
- **THEN** the client address is `203.0.113.9`

#### Scenario: Every entry trusted

- **WHEN** the list is `10.0.0.0/8` and a request from peer `10.0.0.2` carries `X-Forwarded-For: 10.0.0.8, 10.0.0.5`
- **THEN** the client address is `10.0.0.8`

### Requirement: One resolver for every consumer

The login limiter, the authentication brute-force ban, the audit actor, the access log and the console handlers SHALL take the
client address from the same resolver.

#### Scenario: Lockout cannot be reset by a header

- **WHEN** the list is set, an untrusted peer fails to log in repeatedly and sends a different `X-Forwarded-For` value each time
- **THEN** the attempts count against one key and the account is locked at the configured limit
