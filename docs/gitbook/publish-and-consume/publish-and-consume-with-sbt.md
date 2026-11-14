---
description: Publish and consume Ingot artifacts from an sbt build with resolvers, publishTo and a credentials file whose realm matches the Ingot login challenge, for public and private repositories.
---

# Publish and consume with sbt

sbt resolves from the `resolvers` setting and publishes to `publishTo`. Both take the repository URL. The token comes from a credentials file, which sbt matches to the host and the realm of the server. This page covers sbt 1.x. The URL scheme, the tokens and the redeployment rules are in [Publish and consume artifacts](publish-and-consume-artifacts.md).

## Before you begin

* The URL of the instance, for example `https://repo.example.org`.
* A token with the `deploy` permission on the repository to publish to, and for a private repository a token with `read`. The token name is the user name and the token secret is the password. See [Publish and consume artifacts](publish-and-consume-artifacts.md#before-you-begin).

{% hint style="warning" %}
Keep the credentials file in your home directory, outside the project, and do not commit it.
{% endhint %}

## Credentials

Create `~/.sbt/ingot.credentials`:

```properties
realm=Ingot
host=repo.example.org
user=<token-name>
password=<token-secret>
```

The realm is `Ingot`. That is the realm in the `WWW-Authenticate` header that Ingot sends with each `401` answer (`Basic realm="Ingot", charset="UTF-8"`). Use the same string in the file, because the credentials are matched to the realm of the challenge. A different string is the usual reason why sbt asks for a login or fails with `401`.

## Publish

1. Declare the target in `build.sbt`, and load the credentials file.

   ```scala
   ThisBuild / organization := "com.example"
   ThisBuild / version := "1.0.0"
   ThisBuild / publishMavenStyle := true

   ThisBuild / publishTo := {
     val base = "https://repo.example.org"
     if (isSnapshot.value) Some("ingot-snapshots" at s"$base/snapshots")
     else Some("ingot-releases" at s"$base/releases")
   }

   ThisBuild / credentials += Credentials(Path.userHome / ".sbt" / "ingot.credentials")
   ```

   A `-SNAPSHOT` version goes to `snapshots`, and every other version goes to `releases`, which matches the layout in [Publish and consume artifacts](publish-and-consume-artifacts.md#snapshots).

2. Publish.

   ```bash
   sbt publish
   ```

## Consume

Add the repository to `build.sbt`. A public repository needs no credentials.

```scala
resolvers += "Ingot releases" at "https://repo.example.org/releases"

libraryDependencies += "com.example" % "library" % "1.0.0"
```

For a private repository, add the `resolvers` entry for `https://repo.example.org/private` and keep the credentials file from above. The same file covers publishing and consuming.

## Result

After `sbt publish`, the artifact is listed under its path in the repository, and a project with the resolver resolves it. A second publish of a release file returns `409`, as described in [Redeployment rules](publish-and-consume-artifacts.md#redeployment-rules).
