---
description: What a plugin can change on the Ingot dashboard, which stable names it can target, how it ships a stylesheet, and what a stylesheet cannot do.
---

# Extend the dashboard from a plugin

A plugin can change the dashboard in four ways today: it declares pages, it fills placeholders, it ships a stylesheet, and it aims at the stable names of the dashboard from that stylesheet. This page says which is which and what each one may not do.

## What a plugin can do today

- **Declare pages.** A plugin adds its own pages to the dashboard with the `plugin-ui` facade. The text, tables, forms, actions and status blocks are drawn by the dashboard's own components, so the plugin chooses the words and the order. See [Dashboard pages](../reference/plugin-api.md#dashboard-pages).
- **Fill placeholders.** A plugin can substitute a value into the bundled `index.html` and `.js` files with `FrontendFacade.registerPlaceholder`. A placeholder carries a value, not a rule. See [Frontend placeholders](../reference/plugin-api.md#frontend-placeholders).
- **Ship a stylesheet.** A plugin delivers one CSS file that the dashboard loads for signed in callers. The file is checked when the server starts, and the rules sit in a layer of their own. See [Ship a stylesheet](#ship-a-stylesheet) below.
- **Set the appearance.** Colours, the logo, the favicon and the footer text are configuration, see [Customize branding](customize-branding.md). They are settings, not CSS.

## What a plugin can aim at

Every component of the dashboard carries a stable name in the DOM. The names are listed in [Dashboard anchors](../reference/dashboard-anchors.md), with the parts and states that each component exposes:

- `data-ingot-component="<name>"` on the element that renders a component,
- `data-ingot-part="<part>"` on a named region inside it,
- `data-ingot-state="<state>"` on an element while it is loading, in error, empty, open or current.

A name marked `stable` is part of the contract. It keeps working across minor releases, and when it is renamed the old name stays for one minor release as `data-ingot-component-alias`. A name marked `experimental` may change in any release. Build against `stable` names first.

The names do not change how anything looks or behaves. They are there so that a stylesheet, a test or a tool can find a part of the dashboard without depending on its utility classes, which change with every restyle.

## Ship a stylesheet

Put the stylesheet in the resources of the plugin jar, for example as `dashboard.css` at the root, and register it on the `plugin-ui` registrar of the plugin:

```kotlin
@Plugin(name = "example", dependencies = ["plugin-ui"])
class ExamplePlugin : ReposilitePlugin() {
    override fun initialize(): Facade? {
        facade<PluginUiFacade>().forPlugin(this).registerStylesheet("dashboard.css")
        return null
    }
}
```

The sheet below restyles the header of the plugin's pages and the status blocks, and it uses only stable names. It is the example that the test suite of the server checks against the rules on this page:

```css
/* dashboard.css in the root of the plugin jar. Each rule starts with a stable name of the dashboard. */
[data-ingot-component="plugin-page"] [data-ingot-part="header"] {
  border-bottom: 2px solid currentColor;
}

[data-ingot-component="plugin-block"][data-ingot-block="status"] {
  border-left: 4px solid currentColor;
  padding-left: 0.75rem;
}
```

### The rules a sheet must keep

The server reads the sheet once, when the plugin registers it. A sheet that breaks a rule is dropped, and the log names the plugin and the rule. The other pages and plugins are not affected.

- **One sheet per plugin.** A plugin makes one `registerStylesheet` call. A second call is dropped, and so is a first call whose sheet failed.
- **Size and encoding.** At most 64 KiB, UTF-8, without a byte order mark. Do not write an `@charset` rule: the server writes the charset.
- **No network.** A sheet cannot load anything. `url()` is allowed only with a `data:image/` argument (png, gif, jpeg, webp or svg+xml). `src()`, `image-set()`, `-webkit-image-set()`, `@import`, `@font-face` and `@namespace` are refused.
- **No escapes outside strings.** A backslash in a selector or a property is refused, because an escape can spell a forbidden keyword. A backslash inside a quoted string is fine.
- **No script.** `expression(`, `behavior:` and `-moz-binding` are refused.
- **Braces.** Every block is closed, and no brace closes more than it opens. A sheet with a stray brace would end the layer early, so it is refused.
- **Start with a stable name.** This is a convention and is not enforced by the server. A rule that starts with `[data-ingot-component="..."]` changes only the part of the dashboard it names. A rule on `body` or on a bare element reaches everything, so avoid it.

### What a stylesheet cannot change

- **Its order.** The server puts the rules in the layer `ingot-plugins`, which the dashboard declares after its utility layers. A plugin rule therefore beats a utility class of the dashboard, whatever the specificity. A plugin cannot choose another layer or reorder the layers.
- **The colour tokens and the unlayered styles.** The dashboard keeps some of its CSS outside any layer: the colour tokens and the `<style>` blocks of several components. An unlayered rule beats a layered one, so a plugin rule does not override them. A theming API for the tokens is not part of this channel.
- **The sign-in page.** The sheet is loaded only after a signed in caller has read the manifest. The sign-in page has no plugin styles.
- **Behaviour.** A sheet can hide, move or restyle a control. It cannot read the session, call the API, or run code, so a hidden control is still refused by the server if it is used. Only instance administrators install plugins, so the sheet of a plugin is trusted in the same way as its code, but an operator who does not trust a plugin's appearance can switch the styles off.

### Switch the plugin styles off

The operator switches all plugin stylesheets off with `INGOT_LOCAL_PLUGINSTYLES=false`. The stylesheet list of the manifest is then empty and the stylesheet address answers `404`. The pages and settings sections of the plugins keep working. See [Environment variables](../reference/environment-variables.md).

## What is still missing

- **No insertion points.** A plugin cannot yet add content at a named place on an existing dashboard page. This is planned as `add-ui-insertion-points`.
- **No theming API.** A plugin cannot yet override the colour tokens of the dashboard from its stylesheet.
- **No plugin script in the browser.** Plugin code does not run in the dashboard, and this is not planned for now. See [Security headers](../explanation/security-headers.md#plugin-pages).

## Select on names in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The `data-ingot-*` names are the right choice for structural checks, and for the rare control that has no role.

## Related

- [Dashboard anchors](../reference/dashboard-anchors.md) lists every name, its parts and states, and its stability.
- [Dashboard pages](../reference/plugin-api.md#dashboard-pages) describes the pages a plugin declares.
- [Write a plugin](write-a-plugin.md) builds a first plugin.
