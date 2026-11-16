# Support

Ingot is free and open source software maintained by volunteers. The support described here is community support.
No response time and no fix date is promised.

## Asking questions

Ask questions in [GitHub Discussions](https://github.com/OneLiteFeatherNET/ingot/discussions). Open an issue only for a
bug you can reproduce or a concrete feature request.

## Reporting a vulnerability

Do not report vulnerabilities in public issues, discussions or pull requests. Follow the
[security policy](.github/SECURITY.md) instead.

## Which release lines receive fixes

- The latest minor release line receives every fix.
- The previous minor release line receives security fixes only, for 90 days after the next minor release line ships.
- Older release lines receive no fixes.

| Release line       | Fixes received                                    | Status                                                |
|:-------------------|:--------------------------------------------------|:------------------------------------------------------|
| `1.1.x` (latest)   | Every fix, bugs and security                      | Supported                                             |
| `1.0.x`            | Security fixes only                               | Security window ended on 2026-11-07 (90 days after 1.1.0) |
| Older lines        | None                                              | Not supported                                         |

The upstream [Reposilite](https://github.com/dzikoysk/reposilite) project is not covered by this policy. Report upstream
issues to the upstream project.

## Support never gates a feature

Support terms do not restrict any feature of Ingot. No feature depends on a support arrangement, and this file describes
community support only. There is no paid tier and no service level agreement.

## Plugin tiers

Every plugin of Ingot has one of four tiers. The tier says what the OneLiteFeather maintainers stand behind. It never decides
whether a plugin loads, and no feature depends on it. The support described in this file stays community support: there is
no paid tier, no service level agreement, and no fix date is promised for any tier.

- **Core**: built into the server, or bundled in the image. Core plugins are fixed on the release lines listed above, not only
  on the latest release, on a best effort basis.
- **Official**: maintained and tested by the OneLiteFeather maintainers, fixed on the supported release lines, on a best effort
  community basis. Official plugins are fixed on the release lines listed above, not only on the latest release.
- **Community**: any other plugin, including plugins that are maintained with the server but have no contract test yet. Community
  plugins receive community support only, through [GitHub Discussions](https://github.com/OneLiteFeatherNET/ingot/discussions).
- **Experimental**: a plugin that builds on an API that may still change. Its API carries no promise, and the plugin is fixed on
  the same best effort basis as any other plugin.

The definitions, and the evidence each tier needs, are on the page [Plugin tiers](docs/gitbook/references/plugins/plugin-tiers.md)
in the documentation.
