---
id: badges
title: Badges
---

Ingot supports generation of SVG badges out of the box. 
It might be especially useful for open-source developers that may want to include well-known GitHub badges in the their fancy README files.

* `/api/badge/latest/{repository}/{gav}`

Supported query parameters:

| Parameter | Example value | Description |
| :-------: | :-----------: | :---------: |
| `name` | `Ingot` | The name on the badge |
| `color` | `40c14a` | HEX color code of badge |
| `prefix` | `v` | Text included before the version matched by Ingot |
| `filter` | `1.0-` | Ingot will resolve only versions that match given filter. To learn more about filters, take a look at [filters](#filters) section. |

Example usage of the badges endpoint:

```bash
/api/badge/latest/releases/net/onelitefeather/ingot/ingot?color=40c14a&name=Ingot&prefix=v&filter=3
```

Which renders a badge with the label `Ingot` and the latest matching version.

<br/>

### Filters

Ingot supports filtering through `filter` query parameter.
Filters are used to match only specific versions of artifacts, for example, you may want to match only versions that start with `1.0` or `1.1`. Here's the list of supported filters:

| Filter | Pattern | Example | Description |
| :----: | :-----: | :-----: | :---------: |
| Default | `{value}` | `1.0-` | Matches all versions that start with `1.0-` |
| Contains | `has:{value})` | `has:SNAPSHOT` | Matches all versions that contain `SNAPSHOT` value |
| Not contains | `none:{value}` | `none:SNAPSHOT` | Matches all versions that do not contain `SNAPSHOT` value |

The same filters are also supported in Ingot's REST API.