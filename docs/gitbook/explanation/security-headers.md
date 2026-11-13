---
description: The security headers Ingot adds to its responses, and what each one protects.
---

# Security headers

Ingot adds headers that tell a browser how much to trust what it receives. They need no setup. Existing clients, plugins and the dashboard keep working, because the headers only affect how a browser handles a response.

## Every response

| Header | Value |
| --- | --- |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

## API responses (`/api`)

| Header | Value |
| --- | --- |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | `frame-ancestors 'none'` |

## The dashboard document

The page that Ingot serves for the dashboard carries a policy that the built dashboard satisfies. The build has no inline script or style, so nothing needs a hash or a nonce.

| Header | Value |
| --- | --- |
| `X-Frame-Options` | `DENY` |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: http:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'` |
| `Permissions-Policy` | `accelerometer=(), camera=(), geolocation=(), gyroscope=(), microphone=(), payment=(), usb=()` |

Two entries are looser than they could be, on purpose. `'unsafe-eval'` is there because the settings forms compile JSON schemas with a library that builds validators with `Function`. Inline scripts stay blocked. `img-src` allows any host because the organization logo and the favicon are addresses an operator configures.

A custom frontend served from the `static` directory does not get this policy, because Ingot cannot know what it loads.

## Repository files

Repository files and the generated directory index are served with `Content-Security-Policy: sandbox`, as in Reposilite. A browser runs an uploaded HTML or SVG page in an opaque origin, so it cannot read the session of the person who opens it. `INGOT_REPOSITORY_SANDBOX=false` removes the header. Hosted Javadoc keeps the value `sandbox allow-scripts` that it always had.

## Strict transport security

`Strict-Transport-Security: max-age=<seconds>` is off by default. It is sent only when `INGOT_HSTS_MAX_AGE` is set and the request arrived over HTTPS, directly or through a proxy that sends `X-Forwarded-Proto: https`.

## Related

* [Environment variables](../reference/environment-variables.md#security-header-variables)
* [Run behind a reverse proxy](../how-to/run-behind-reverse-proxy.md)

## Plugin pages

A plugin that adds a dashboard page does not add a source to this policy, and the dashboard does not load plugin JavaScript or frames. A page is declared as data and drawn with the dashboard's own components, see [Plugin API, Dashboard pages](../reference/plugin-api.md#dashboard-pages).

A plugin may ship one stylesheet. It is served from the same origin, so the existing `style-src 'self'` allows it, and the policy is unchanged for it. The server checks the sheet when the plugin registers it: it refuses every construct that can reach the network (`url()` except `data:image/`, `src()`, `image-set()`, `@import`, `@font-face`), every CSS escape outside a string, and the legacy script hooks. A sheet therefore cannot send a value out of the page or run code. See [Stylesheet](../reference/plugin-api.md#stylesheet).

The risk that remains is visual. Only instance administrators install plugins, and a plugin is trusted with its code already, but a stylesheet can hide or restyle a control, a confirmation text or the read-only banner, so what the administrator sees can differ from what they click. The server still checks every request, so a hidden control is refused when it is used. An operator who does not trust a plugin's appearance sets `INGOT_LOCAL_PLUGINSTYLES=false`, or does not install the plugin.

The policy is kept for three reasons:

- **The dashboard origin is the administrator.** The session cookies are HttpOnly, but the CSRF cookie is readable on purpose, and the dashboard calls every administrative endpoint with it. A script that runs in that origin can do what the administrator can do, so a plugin script in it would hold that power.
- **The policy cannot tell a plugin script from the dashboard.** A script served for a plugin comes from the same host, so `script-src 'self'` allows it. Allowing plugin script would cost no policy change and gain no protection.
- **The split image stays small.** The dashboard image serves the build and proxies the rest. Plugin script served from it would make the image as trusted as the plugin.

A later version that loads plugin code would need a separate origin, a stated policy for it, an operator switch that is off by default and a signed or pinned bundle. That is not part of the current design.
