---
description: What a plugin can change on the Ingot dashboard today, which stable names it can target, and what is still missing.
---

# Extend the dashboard from a plugin

A plugin can change the dashboard in three ways today. The dashboard also names its components in a stable way, which is the groundwork for a stylesheet that a plugin could ship later, but that channel is not open to plugins yet. This page says which is which.

## What a plugin can do today

- **Declare pages.** A plugin adds its own pages to the dashboard with the `plugin-ui` facade. The text, tables, forms, actions and status blocks are drawn by the dashboard's own components, so the plugin chooses the words and the order, not the look. See [Dashboard pages](../reference/plugin-api.md#dashboard-pages).
- **Fill placeholders.** A plugin can substitute a value into the bundled `index.html` and `.js` files with `FrontendFacade.registerPlaceholder`. A placeholder carries a value, not a rule, so it cannot change how the dashboard looks. See [Frontend placeholders](../reference/plugin-api.md#frontend-placeholders).
- **Set the appearance.** Colours, the logo, the favicon and the footer text are configuration, see [Customize branding](customize-branding.md). They are settings, not CSS.

## What a plugin can aim at

Every component of the dashboard carries a stable name in the DOM. The names are listed in [Dashboard anchors](../reference/dashboard-anchors.md), with the parts and states that each component exposes:

- `data-ingot-component="<name>"` on the element that renders a component,
- `data-ingot-part="<part>"` on a named region inside it,
- `data-ingot-state="<state>"` on an element while it is loading, in error, empty, open or current.

A name marked `stable` is part of the contract. It keeps working across minor releases, and when it is renamed the old name stays for one minor release as `data-ingot-component-alias`. A name marked `experimental` may change in any release. Build against `stable` names first.

The names do not change how anything looks or behaves. They are there so that a stylesheet, a test or a tool can find a part of the dashboard without depending on its utility classes, which change with every restyle.

## What is still missing

- **No way to deliver a stylesheet into the dashboard.** The names are reachable only from a dashboard build that the operator controls. A plugin cannot yet ship CSS into the bundled dashboard. A follow-up change, `add-plugin-stylesheet-channel`, is planned for this and will define what such a stylesheet may do.
- **No insertion points.** A plugin cannot yet add content at a named place on an existing dashboard page. This is planned as `add-ui-insertion-points`.
- **No plugin script in the browser.** Plugin code does not run in the dashboard, and this is not planned for now. See [Security headers](../explanation/security-headers.md#plugin-pages).

Until the stylesheet channel exists, the example below works only in a dashboard build that the operator controls, for example a fork that is built from source with the same files. It shows the form a rule takes once a channel exists. A plugin cannot load it today.

```css
/* Only for a dashboard build the operator controls. No plugin can load this file yet. */
[data-ingot-component="instance-status-panel"] [data-ingot-part="read-only-banner"] {
  outline: 2px solid currentColor;
}
```

## Select on names in tests

End-to-end and component tests select by role and accessible name first, by `data-ingot-*` second, and by `data-testid` only where neither works. A role is what a user perceives, so a test that breaks because a role changed has found a real defect. The `data-ingot-*` names are the right choice for structural checks, and for the rare control that has no role.

## Related

- [Dashboard anchors](../reference/dashboard-anchors.md) lists every name, its parts and states, and its stability.
- [Dashboard pages](../reference/plugin-api.md#dashboard-pages) describes the pages a plugin declares.
- [Write a plugin](write-a-plugin.md) builds a first plugin.
