# Security Policy

## Supported Versions

Security fixes are made on the latest minor release line of Ingot, which is `1.1.x` at the time of writing, and on
the `main` branch. No published statement covers older Ingot release lines yet.

| Version                    | Supported                                                     |
|:---------------------------|:--------------------------------------------------------------|
| Ingot `1.1.x` (latest)     | Security fixes                                                |
| Ingot `main`               | Security fixes                                                |
| Ingot releases before 1.1  | Not covered by a published statement yet                      |
| Upstream Reposilite 3.x    | Not supported here, report to the upstream project            |
| Upstream Reposilite < 3.x  | Not supported                                                 |

## Reporting a Vulnerability

Do not report security vulnerabilities through public issues, pull requests or community chat.

If you have spotted a security vulnerability, or something that could be treated as a security issue, use the
[Security Advisory](https://github.com/OneLiteFeatherNET/ingot/security/advisories/new) form on this
repository. Alternatively you can reach the maintainers privately at:

TODO(onelitefeather): security contact address

Please include the affected version or commit, a description of the issue, and steps to reproduce it if possible.

We acknowledge a report within 14 days and will keep you updated while we investigate. Because this is
a free and open source project maintained by volunteers, we cannot guarantee a fixed timeline for a patch.

If the vulnerability also affects the upstream [Reposilite](https://github.com/dzikoysk/reposilite) project,
please report it there as well so upstream users are protected too.

## Disclosure

We ask that you give us a reasonable opportunity to fix the issue before any public disclosure. The coordinated
disclosure window is 90 days from the report. We extend it only by agreement with the reporter.

Once a fix is available, we will publish a security advisory and credit the reporter unless anonymity is requested.
A CVE identifier is requested through the GitHub Security Advisory opened for the report. Not every advisory
receives one.
