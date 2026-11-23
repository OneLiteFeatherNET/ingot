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

Matching works from the start of a value, so `core` finds `core-api`. A word also matches a part of a name that starts after a dot, a dash or another separator, so `core` finds `spring-core` too. Every word has to match, so `spring core` finds `spring-core` but not `spring-boot`. Upper and lower case do not matter. Version one has limits: quotes do not make a phrase, there are no wildcards, and `OR` and `NOT` are read as plain words. The results are not ranked by relevance; the sort order is the one you pick.

To search by coordinates, separate the parts with colons. `org.apache:commons-lang3` matches artifacts whose group starts with `org.apache` and whose artifact starts with `commons-lang3`. `:commons-lang3` matches that artifact name in any group, and `org.apache:` matches everything in the group. The version is optional and must match exactly, as in `org.apache:commons-lang3:3.14.0`.

Press `/` to move the focus into the search box. The shortcut does not work while you are typing in a field.

## Narrow the results

The filters sit beside the results. Each filter shows the values that match the current search, with a count of files. Choosing a value changes only that filter, so the other filters keep working. The filters are:

* **Repository**: one or more repositories.
* **File type**: the file extension, for example `jar` or `pom`.
* **Classifier**: for example `sources`. The value `-` is shown as **none** and selects files without a classifier.
* **Origin**: **Any origin**, **Hosted** or **Cached copies**.
* **Release or snapshot**: **Any version**, **Releases** or **Snapshots**.
* **Coordinates**: **Group id starts with**, **Artifact id starts with** and **Version**.
* **Last modified**: **On or after** and **Before**.
* **File size**: **At least (bytes)** and **At most (bytes)**.

Plugins can add filters of their own. When an installed plugin declares attributes that the search may count, such as the platforms or game versions of a plugin file, the page shows one more filter for each of them, titled with the name the plugin gave it. Choosing several values of one filter shows files that match any of them, and choosing values of different filters shows files that match all of them. A chosen value stays in the list with a count of zero when no file matches it any more, so you can clear it. The choice is kept in the address as `attribute=<key>:<value>`. If the instance cannot count attributes, for example because the search backend does not support it, the filters are left out and the search works as before. Read [Search attribute filters and facets](../references/plugins/search-attribute-facets.md) for how a plugin declares them.

The text, date and size fields apply when you leave them or press Enter. **Reset filters** clears every filter.

Use the **Sort by** select to order the results by group id, artifact id, version, last modified or size. Use the **Order** select to choose ascending or descending.

The whole search is kept in the address of the page. Copy the address to share it, or use the back button to return to an earlier search.

## Read the results

Each row is one artifact with its group, artifact and version.

* **Newer** followed by a version appears when the latest version you can see differs from the version in the row.
* **Snapshot** marks a snapshot version.
* Repository chips show where the artifact is stored. A chip that reads **cached from** a name is a copy that a proxy repository fetched from an upstream repository. Read [How the search index stays current](../references/architecture/how-the-search-index-stays-current.md) for what that marking does and does not tell you.
* Open a row to list its files. Each file and each artifact links to its location in the browser.

With the keyboard, Tab moves through the filters and the rows. Arrow Down and Arrow Up move between rows. Arrow Right opens the selected row, and Arrow Left closes it.

{% hint style="info" %}
A proxy repository only lists the artifacts it has fetched at least once. An artifact that a proxy has never been asked for does not appear in search, even when the upstream repository has it.
{% endhint %}

## When nothing is found

* Check the active filters, or select **Reset filters**.
* If an artifact that exists upstream is not listed, request it once through the proxy repository. The proxy fetches and caches it, and the cached copy then appears in search.
* The page shows the state of the index above the results when the index needs attention:
  * **Indexing. Results may be partial.** The first build is still running. Wait for it to finish. An administrator can see the progress of each repository in the Maintenance page.
  * **Nothing is indexed yet, so a search finds nothing.** The index has not been built. An administrator can open the Maintenance page from the link on the page.
  * **Results may be out of date.** or **Results may lag by N minutes.** The index is behind the storage. The results are correct for what is indexed.
* If the page says **Search is turned off on this instance.**, the instance has the feature disabled. Ask the administrator.

## Related

* [Search attribute filters and facets](../references/plugins/search-attribute-facets.md) describes what a plugin declares for the plugin filters.
* [Search filters and API](../references/http-api/search-filters-and-api.md) lists every parameter.
* [Rebuild the search index](../configure/search/rebuild-search-index.md) explains what an administrator does when the index is out of date.
