---
description: Find an artifact across the repositories of an Ingot instance with the search page, its filters and the cached-copy marking.
---

# Search for artifacts

The search page finds a Maven artifact by name, group, version or file type across every repository you may browse. Results come from the search index, which Ingot keeps current as artifacts are uploaded, deleted and cached, so a new upload appears shortly after it is stored.

## Before you begin

* Know the address of the instance, for example `https://repo.example.org`.
* Sign in to see private and hidden repositories. Anonymous visitors see only public repositories, and the page says that some are hidden without naming them.

## Find an artifact by name

1. Open `/search` on the instance, or select **Search** in the navigation.
2. Type part of the name in the search box and press Enter. Every word you type must match.

Matching works from the start of a value, so `core` finds `core-api` but not `spring-core`.

To search by coordinates, separate the parts with colons: `org.apache:commons-lang3` finds one artifact, `:commons-lang3` finds that artifact name in any group, and `org.apache:` finds everything in the group. The version is optional and must match exactly, as in `org.apache:commons-lang3:3.14.0`.

Press `/` at any time to move the focus into the search box.

## Narrow the results

The filter chips below the search box narrow the results. Each chip changes only its own choice, so the other filters keep working:

* **Repository**: one or more repositories.
* **Extension**: for example `jar` or `pom`.
* **Classifier**: for example `sources`. The value `-` selects files without a classifier.
* **Origin**: hosted files, cached copies, or both.
* **Snapshot**: all artifacts, releases only, or snapshots only.

Use the size and last modified fields to narrow by a range. Use the sort select to order the results by group, artifact, version, last modified or size, in either direction.

The whole search is kept in the address of the page. Copy the address to share it, or use the back button to return to an earlier search.

## Read the results

Each row is one artifact with its group, artifact and version.

* **Newer** appears when a later version exists in a repository you can see.
* Repository chips show where the artifact is stored. A chip that reads **cached from** a name is a copy that a proxy repository fetched from an upstream repository. Read [How the search index stays current](../explanation/how-the-search-index-stays-current.md) for what that marking does and does not tell you.
* Open a row to list its files. Each file and each artifact links to its location in the browser.

Use Tab to move through the filters and the rows, and the arrow keys to move between rows and to open or close a row.

{% hint style="info" %}
A proxy repository only lists the artifacts it has fetched at least once. An artifact that a proxy has never been asked for does not appear in search, even when the upstream repository has it.
{% endhint %}

## When nothing is found

* Check the active filters. The page lists them and offers to reset them.
* If an artifact that exists upstream is not listed, request it once through the proxy repository. The proxy fetches and caches it, and the cached copy then appears in search.
* If the page says the index is being built, results may be partial until the first build ends. An administrator can check the progress in the Maintenance page.
* If the page says search is turned off, the instance has the feature disabled. Ask the administrator.

## Related

* [Search filters and API](../reference/search-filters-and-api.md) lists every parameter.
* [Rebuild the search index](rebuild-search-index.md) explains what an administrator does when the index is out of date.
