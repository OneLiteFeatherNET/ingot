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
