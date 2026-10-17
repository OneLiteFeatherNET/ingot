---
description: Change the title, description, logos, colours, favicon and footer of the Ingot dashboard through the frontend settings, with the API or the dashboard.
---

# Customize branding

The dashboard takes its title, description, logos, colours, favicon and footer from the shared settings domain `frontend`. You change them in the dashboard or with the settings API. You do not edit files or rebuild the image. Visitors see a change when they load the dashboard again.

## Before you begin

* An instance admin account for the dashboard, or an instance admin token for the API.
* Logos and favicons are links. Host each image at a URL that visitors can reach. Ingot does not store uploaded images.

## Change the settings with the API

1. Read the current settings. Keep the `ETag` header from the answer.

   ```
   curl -i -u "$TOKEN_ID:$TOKEN_SECRET" https://repo.example.org/api/settings/domain/frontend
   ```

2. Write the complete settings object back. The body replaces the whole domain, and a field you leave out takes its default value. Start from the answer of step 1 and change only the fields you need, keeping the `id`.

   ```json
   {
     "id": "ingot-repository",
     "title": "Acme Maven Repository",
     "description": "Public Maven repository of Acme",
     "organizationWebsite": "https://acme.example.org",
     "organizationLogo": "https://acme.example.org/logo.png",
     "privacyPolicy": "https://acme.example.org/privacy",
     "icpLicense": "",
     "appearance": {
       "seedColor": "#006a6a",
       "schemeVariant": "TONAL_SPOT",
       "primaryColor": "",
       "secondaryColor": "",
       "tertiaryColor": "",
       "errorColor": "",
       "logoDark": "https://acme.example.org/logo-dark.png",
       "favicon": "https://acme.example.org/favicon.png",
       "footerText": "Acme Games"
     }
   }
   ```

   ```
   curl -X PUT -u "$TOKEN_ID:$TOKEN_SECRET" \
     -H "Content-Type: application/json" \
     -H "If-Match: <etag from step 1>" \
     --data @frontend.json \
     https://repo.example.org/api/settings/domain/frontend
   ```

3. Read the result. The `If-Match` header is optional. When you send it, the write is refused if the stored settings changed after you read them, so you do not overwrite someone else's change. A value that is not valid is refused with a message that names the field.

## Fields

| Field | Default | Meaning |
|-------|---------|---------|
| `id` | `ingot-repository` | The repository id. Keep the value you read. |
| `title` | `Ingot Repository` | The title of the homepage. It is also the title of the browser tab. |
| `description` | `Public Maven repository hosted through Ingot` | The description of the homepage and the page description for search engines. HTML is not rendered. |
| `organizationWebsite` | `https://github.com/OneLiteFeatherNET` | Link to the website of the organisation. |
| `organizationLogo` | The OneLiteFeatherNET avatar on GitHub | Logo of the organisation on the homepage, and the logo in the dark theme when `logoDark` is empty. |
| `privacyPolicy` | empty | Link to a privacy policy, shown in the footer. |
| `icpLicense` | empty | ICP licence number, for services in China. |
| `appearance.seedColor` | empty | Colour the palette is generated from. Empty keeps the built-in Ingot theme. |
| `appearance.schemeVariant` | `VIBRANT` | How the palette is generated from the seed colour. Only used with a seed colour. |
| `appearance.primaryColor`, `secondaryColor`, `tertiaryColor`, `errorColor` | empty | Colours that replace the generated key colours. Only used with a seed colour. |
| `appearance.logoDark` | empty | Logo for the dark theme. |
| `appearance.favicon` | empty | Icon of the browser tab. Empty uses the bundled icon. |
| `appearance.footerText` | empty | Plain text shown in the footer. |

Colours are written as `#RRGGBB`, with six hexadecimal digits. The server refuses any other value.

## Choose the colours

1. Set `appearance.seedColor` to the main colour of your brand. The dashboard generates the palette from it.
2. Optionally change `appearance.schemeVariant`. The values are `TONAL_SPOT`, `VIBRANT`, `EXPRESSIVE`, `NEUTRAL`, `FIDELITY`, `CONTENT` and `MONOCHROME`.
3. Only if the generated colours do not fit, set one or more of `primaryColor`, `secondaryColor`, `tertiaryColor` and `errorColor`. Without a seed colour, these four fields have no effect.

To return to the built-in theme, clear `seedColor` and the four key colours.

## Set the logos

* `organizationLogo` is the logo that the homepage shows.
* `appearance.logoDark` is the logo for the dark theme. When it is empty, the dark theme shows `organizationLogo`.

## Set the favicon

Set `appearance.favicon` to a link to an image. When it is empty, the dashboard uses the bundled `favicon.png`, which is a placeholder mark.

To replace the bundled icon without a link, put your own `favicon.png` into the `static` directory of the working directory before the first start. In the container image, that directory is `/app/data/static`. Ingot copies the bundled file there only when no file exists yet, so a file you place there stays. Files in `static` are served from the root of the instance, so the icon is then available at `/favicon.png`.

## Set the footer

* `appearance.footerText` is plain text. HTML is not rendered.
* `privacyPolicy` adds a link to the footer.

## Check the result

1. Reload the dashboard in a browser. The title of the tab, the homepage title and the colours should match your settings.
2. Read the public settings without credentials:

   ```
   curl https://repo.example.org/api/frontend/settings
   ```

   The answer holds the values in plain fields. It is the same data that the dashboard reads.

## Related

* [Monitor status and failures](monitor-status-and-failures.md) describes the health and status endpoints.
