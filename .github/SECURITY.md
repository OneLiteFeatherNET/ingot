# Security Policy

## Supported Versions

Ingot is a fork of Reposilite and has not published its own release yet. Until the first Ingot release,
security fixes land on the `main` branch only.

| Version                  | Supported                                                     |
|:-------------------------|:--------------------------------------------------------------|
| Ingot `main` (unreleased) | Supported                                                     |
| Upstream Reposilite 3.x  | Not supported here, report to the upstream project            |
| Upstream Reposilite < 3.x | Not supported                                                 |

Once the first Ingot release is published, this table will list the release lines that receive security fixes.

## Reporting a Vulnerability

Do not report security vulnerabilities through public issues, pull requests or community chat.

If you have spotted a security vulnerability, or something that could be treated as a security issue, use the
[Security Advisory](https://github.com/OneLiteFeatherNET/reposilite/security/advisories/new) form on this
repository. Alternatively you can reach the maintainers privately at:

TODO(onelitefeather): security contact address

Please include the affected version or commit, a description of the issue, and steps to reproduce it if possible.

We aim to acknowledge a report within a few days and will keep you updated while we investigate. Because this is
a free and open source project maintained by volunteers, we cannot guarantee a fixed timeline for a patch.

If the vulnerability also affects the upstream [Reposilite](https://github.com/dzikoysk/reposilite) project,
please report it there as well so upstream users are protected too.

## Disclosure

We ask that you give us a reasonable opportunity to fix the issue before any public disclosure. Once a fix is
available, we will publish a security advisory and credit the reporter unless anonymity is requested.
