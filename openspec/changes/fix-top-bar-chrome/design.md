# Design

## Context

Measured on `main`:

- `reposilite-frontend/src/components/header/DefaultHeader.vue:41` renders the bar as
  `sticky top-0 z-30 bg-surface-container`. The tone never changes. Nothing in `src/` uses `useWindowScroll`, although
  `@vueuse/core` is already a dependency.
- `DefaultHeader.vue:49` renders the instance name as an `h1`. `components/layout/AdministrationFrame.vue:57-62` renders a
  second `h1` ("Administration").
- `DefaultHeader.vue:62-65` draws `<div class="olf-brand-bar absolute inset-x-0 bottom-0" aria-hidden="true" />` at the bottom
  edge of the bar.
- `src/styles/md3/color.css:338-348` defines `.olf-brand-bar` (4 pixels, `var(--olf-gradient)`) and a forced colours override
  that paints it `Highlight`. `src/theme/runtime.js:166` sets `--olf-gradient` from the instance's primary and tertiary colour.
- `DESIGN.md` "Top app bar" (line 549): "64px, `sticky top-0`, `surface`. Takes `surface-container` once the window has
  scrolled (`useWindowScroll`), with a `short4` colour transition. No shadow change." "Brand gradient" says the band sits at the
  top edge of a section. "Adaptive navigation" says the rail is 80 pixels; `MdNavigationRail.vue` is `w-24` (96 pixels).
- Tests that pin the rule today: `src/components/header/DefaultHeader.test.js:86-95`, `e2e/navigation-chrome.spec.js:120-129`
  (it measures the rule's edges) and `src/styles/md3/color.test.js:255-260` (the class itself).
- `src/anchors/registry.json:518-524`: `default-header`, stability `stable`, part `title`. `.olf-brand-bar` is not an anchor.

## Goals

- No decorative line in the chrome.
- The separation the contract specifies, built as specified.
- One `h1` per page, without moving the anchor.
- `DESIGN.md` describes what the code does.

## Non-goals

- New controls in the top bar, a second search, a header redesign.
- The page header of the Administration area (`rework-administration-information-architecture`).

## Decisions

### D1. Remove the element, keep the class

The `div` is deleted from `DefaultHeader.vue`. `.olf-brand-bar`, its forced colours rule and `--olf-gradient` stay in
`color.css`, so `color.test.js` stays green and an operator seed still drives the gradient where it is used. No source in the
research supports a decorative accent line under an app bar; every separator the design systems describe has a job (see
Evidence).

### D2. Tone change on scroll

`DefaultHeader.vue` reads the scroll position through a small composable, `useScrolledPast(threshold = 0)`, that wraps
`useWindowScroll`. The composable is the test seam: a component test passes a ref instead of scrolling a real window, so the
test needs no waits. The bar binds `bg-surface` while not scrolled and `bg-surface-container` once scrolled, with
`transition-colors ease-md-standard` with the `--md-sys-motion-duration-short4` duration and `motion-reduce:transition-none`. No border, no shadow.

### D3. The title becomes a link

The `h1` is replaced by a `RouterLink` to `/` inside a `span` with `md-title-large`. `data-ingot-component="default-header"` stays on
the `header`, `data-ingot-part="title"` stays on the element that holds the title text. The `banner` landmark identifies the
site; the page's `h1` names the page (WAI-ARIA landmark guidance).

### D4. DESIGN.md

One `docs(ui)` commit:

- "Top app bar": as built in D2, plus "the bar holds identity and the account menu only; no decorative rule".
- "Brand gradient": not drawn in the chrome. The class stays as a utility.
- "Adaptive navigation": rail 96 pixels.
- "Known gaps": replaced with the real list: no tabs, list, dialog or drawer primitive under `src/components/md/`, and no
  information architecture rules for the Administration area until `rework-administration-information-architecture` adds them.

## Wireframes

Before (every page):

```
+------+------------------------------------------------------------------+
| logo | Ingot Repository                                    (T) account  |  surface-container, always
|      |==================================================================|  4 px gradient stripe
| rail |                                                                  |
```

After, at rest and after scrolling:

```
+------+------------------------------------------------------------------+
| logo | Ingot Repository                                    (T) account  |  surface (at rest)
|      |                                                                  |  no line
| rail |  page content                                                    |

+------+------------------------------------------------------------------+
| logo | Ingot Repository                                    (T) account  |  surface-container (scrolled)
|      +------------------------------------------------------------------+  edge comes from the tone only
| rail |  ...content scrolling under the bar...                           |
```

Phone, 320 pixels: the same bar, 64 pixels tall, title truncated on one line, account button with a 48 pixel target.

## Evidence

| Finding | Sources |
| --- | --- |
| The top bar holds product identity and global tools only | Carbon UI shell header, https://carbondesignsystem.com/components/UI-shell-header/usage/ ; GOV.UK header, https://design-system.service.gov.uk/components/header/ ; PatternFly masthead, https://www.patternfly.org/components/masthead |
| Separators have a job; avoid decoration | Primer PageHeader (`hasBorder` optional), https://primer.style/components/page-header ; Apple HIG toolbars (scroll edge effect, avoid custom backgrounds), https://developer.apple.com/design/human-interface-guidelines/toolbars ; NN/g complex applications (remove nonessential decoration), https://www.nngroup.com/articles/complex-application-design/ |
| Tonal elevation on scroll | Ingot `DESIGN.md` "Top app bar"; Android Compose app bars (container colour on scroll), https://developer.android.com/develop/ui/compose/components/app-bars |
| Landmarks and one page heading | WAI-ARIA APG landmark regions, https://www.w3.org/WAI/ARIA/apg/practices/landmark-regions/ ; WCAG 2.2 2.4.6 and 2.4.1, https://www.w3.org/TR/WCAG22/ |
| The stripe reads as odd | The maintainer's own words on the staging screenshot. The idea that it "looks like a progress bar" was voiced by several reviewers but has no source and is not used as evidence. |

Discarded: Material 3 top app bar and divider rules. The m3.material.io pages returned only titles during the research, so
the MD3 basis is Ingot's own `DESIGN.md`.

## Risks

- A plugin stylesheet that selects the title by element (`header h1`) loses its target. The documented contract is the anchor
  attribute, which stays. The release notes name the change.
- The tone change could flicker on a page that scrolls by one pixel during load. The threshold is a strict `y > 0` on the
  window, and the reduced motion rule removes the transition.

## Migration plan

None. A rollback is a revert of the code PR.

## Open questions

1. **Brand band elsewhere.** Default: not drawn in the chrome at all. Alternative: the documented band at the top edge of the
   Browse hero only, as a separate change.
