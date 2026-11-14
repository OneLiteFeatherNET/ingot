---
description: A neutral comparison of Ingot with Reposilite, Sonatype Nexus Repository and JFrog Artifactory on licence model, supported formats, self-hosting, single sign on, high availability and plugin model, including where Ingot is narrower.
---

# How Ingot compares

This page helps you decide whether Ingot fits. It compares Ingot with Reposilite, Sonatype Nexus Repository and JFrog Artifactory on a few facts that change slowly. It does not compare speed, capacity or price, and it does not rank the products.

The statements about Nexus Repository and Artifactory are summaries of the public product descriptions of each vendor. Editions, limits and prices change, so check the vendor documentation for the current terms before you decide. The statements about Ingot and Reposilite are checked against this repository.

## At a glance

| | Ingot | Reposilite | Nexus Repository | Artifactory |
|:--|:--|:--|:--|:--|
| Licence model | Apache 2.0, open source, no paid edition | Apache 2.0, open source | Commercial product with a free community edition that has usage limits | Commercial product with tiers |
| Formats | Maven only | Maven only | Many formats | Many formats |
| Self-hosting | Yes | Yes | Yes | Yes, and a vendor-hosted option |
| Single sign on | OpenID Connect, GitHub and LDAP login | LDAP authentication | Offered, with the scope depending on the edition | Offered, with the scope depending on the tier |
| High availability | Several replicas on a shared database, with tested limits | Single instance | Offered in a commercial edition | Offered in a commercial tier |
| Plugin model | Plugins loaded from a directory, `com.reposilite.*` API | Plugins loaded from a directory | Vendor extension points | Vendor extension points and user plugins |
| Footprint | One JVM process | One JVM process | Larger server, with its own database and blob store | Larger server, with its own database |

Read the Nexus Repository and Artifactory columns as orientation only. Each vendor sells several editions, and what a given edition includes is the vendor's to state.

## Where Ingot is narrower

* **Maven only.** Ingot hosts Maven repositories. It does not host npm, PyPI, container images, Go modules, NuGet or other formats. A repository format interface is proposed on the roadmap, and it is not part of the current release. If you need several formats from one server, Nexus Repository or Artifactory covers more.
* **A smaller ecosystem.** Nexus Repository and Artifactory have vendor support, long track records at large scale and many integrations. Ingot is a young fork, and its scale evidence is the one on [High availability support](high-availability-support.md).
* **No commercial support.** There is no paid support contract. Help comes from the project and its community.
* **Limited high availability.** Several replicas are supported on a shared MariaDB database and S3 storage. Some setups are explicitly not supported, and PostgreSQL with several replicas is not tested. The details are on the high availability page.
* **No vulnerability or licence scanning.** Ingot stores and serves artifacts. It does not analyse their contents the way a commercial platform can.

## Against Reposilite

Ingot is a fork of Reposilite and keeps its image, data directory, configuration names and plugin packages, see [Drop-in compatibility with Reposilite](drop-in-compatibility.md). Reposilite is the smaller and simpler product. Choose it when one instance with token access is enough and you want the upstream project's own releases. Choose Ingot when you need users and teams, external login, an audit log, search across repositories or more than one replica.

## Against Sonatype Nexus Repository

Nexus Repository is a broad repository manager for many formats. Choose it when you need those formats, vendor support or its commercial features. Choose Ingot when your artifacts are Maven only, you want an Apache 2.0 licence with no edition split and you want a lighter process. Ingot can import Maven repositories from Nexus Repository 3, see [Import from Nexus](../how-to/import-from-nexus.md).

## Against JFrog Artifactory

Artifactory is a universal repository platform with many formats and a commercial model. Choose it when you need that breadth or a vendor-backed platform. Choose Ingot when you need Maven only and prefer to run an open source server yourself.

## Related pages

* [What is Ingot](what-is-ingot.md)
* [Architecture overview](architecture-overview.md)
* [Import from other repository managers](../how-to/import-from-other-repository-managers.md)
