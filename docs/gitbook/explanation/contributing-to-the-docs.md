---
description: How to write and change the Ingot documentation. Covers the four Diataxis modes, the description front matter, the rules for SUMMARY.md and links, the house style with no dashes, how to verify a claim in the code, and how to handle screenshots.
---

# Contributing to the docs

The documentation lives in `docs/gitbook/`. It is published as a GitBook site, and every page is a Markdown file in the repository. This page sets out the rules that a change to the docs has to follow. The repository's general rules for contributions are in [CONTRIBUTING.md](https://github.com/OneLiteFeatherNET/ingot/blob/main/.github/CONTRIBUTING.md).

## Pick the mode first

The docs follow the Diataxis framework. Each page belongs to exactly one of four modes, and the mode decides what the page may contain.

| Mode | Folder | Purpose | Test for a page of this mode |
|---|---|---|---|
| Tutorial | `tutorials/` | A guided lesson with a known result, for a reader who is new to Ingot. | Every step is done in order, and the reader ends with a working result. |
| How-to guide | `how-to/` | Finishes one task for a reader who already runs Ingot. | The title is a task, such as "Enable TLS", and the page does not explain why. |
| Reference | `reference/` | Lists settings, routes, commands and tables to look up. | A reader can scan it without reading the rest of the page. |
| Explanation | `explanation/` | Explains why things work the way they do, and what follows from it. | The page has no steps to follow. It gives reasons, trade-offs and links to the how-to pages. |

A page that drifts into a second mode is split. A how-to that explains its design moves the design to an explanation page and links to it. A reference that tells a story moves the story out. The [README](../README.md) of the docs lists the modes in the same words.

## Front matter

Every page starts with a front matter block that holds one `description:` line:

```
---
description: One sentence that says what the page covers.
---
```

The description is a sentence, not a title or a list of keywords. Keep it to one or two sentences, and name the main things the page covers, so that a reader can decide from it whether the page is the one they need.

## SUMMARY.md

[SUMMARY.md](../SUMMARY.md) is the table of contents, and it decides which pages a reader can reach from the site navigation.

* Add every new page to `SUMMARY.md` in the section of its mode. The sections are Tutorials, How-to guides, Reference and Explanation.
* Put the entry under the subheading that matches the topic, and keep the order of the entries sensible for a reader.
* Use the page title as the link text, and the path relative to `SUMMARY.md`.
* A page that is not in `SUMMARY.md` cannot be reached from the navigation. It is still in the repository, so give it a link from another page.

## Links

* Use relative links for pages in `docs/gitbook/`, for example `../reference/faq.md`. Add an anchor when you link to a section, for example `configure-database.md#custom-jdbc-urls`.
* Check each relative link before you open the pull request. There is no link checker in the continuous integration of the docs yet, so the check is manual: open the target and confirm that the anchor exists.
* Link to files outside `docs/gitbook/` with the full URL of the file on the `main` branch.
* Do not link to a page that does not exist yet. Add the page in the same change, or leave the sentence out.

## House style

* Write in English, in plain and precise sentences. Use the same word for the same thing on every page.
* Do not use an em dash or an en dash. Use a colon, a comma, parentheses or two sentences. Check this with the command below before you commit.
* Do not leave unfinished markers in a page. A section that is not ready is left out until it is.
* Show a command in a code block, and the result in a second block or a table when the result matters.
* Use the callout syntax that the other pages use, `{% hint style="info" %}` or `{% hint style="warning" %}`, and keep one idea in each hint. The `danger` style exists, but only one page uses it, so prefer `warning`.

To find dashes in the docs:

```
grep -rnP '[\x{2013}\x{2014}]' docs/gitbook
```

The command must print nothing.

## Verify every claim in the code

A claim in the docs is a statement about the behaviour of Ingot, so check it in the code of the branch that the docs describe.

* Read the code path that produces a behaviour, such as the message of an error, the default of a setting or the name of an environment variable. Compare the spelling exactly.
* Do not document a feature that exists only in an open pull request. Document it after it is merged.
* Do not invent numbers, users, benchmarks or comparisons. A figure needs a source, which is the code, a test or a public document.
* A comparison with another product is limited to facts that are public and stable, and it is worded neutrally.
* Name the variable with its `INGOT_` spelling, and mention the `REPOSILITE_` spelling as the fallback when it is still read. The `INGOT_` name takes precedence.

The sources of a claim belong in the pull request description, not in the page. A reviewer should be able to follow a claim to the code without a page full of file paths.

## Screenshots

The pages of the docs have no screenshots at the moment. Describe a screen in text, with the exact name of each label, button and page. The text stays correct when the layout changes, and a reviewer can check it against the code.

If a screenshot is really needed, keep it next to the page in the repository, give it alt text that says what the picture shows, and do not rely on the picture for a step that the text must also explain. A screenshot that shows a secret, a host name of a real instance or a personal account is not allowed, and must be replaced with an example.

## Related pages

* [Drop-in compatibility with Reposilite](drop-in-compatibility.md) explains the promise that the docs must keep.
* [Frequently asked questions](../reference/faq.md) shows how an answer links to the pages that give the details.
