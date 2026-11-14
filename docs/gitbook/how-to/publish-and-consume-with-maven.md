---
description: Deploy a Maven project to an Ingot repository with distributionManagement and settings.xml, consume its artifacts, and route Maven Central requests through an Ingot proxy with a mirror.
---

# Publish and consume with Maven

Maven takes the deploy target from the `distributionManagement` section of the POM, and the token from `~/.m2/settings.xml`. Both refer to the same repository id. This page covers the Maven side. The URL scheme, the tokens and the redeployment rules are in [Publish and consume artifacts](publish-and-consume-artifacts.md).

## Before you begin

* The URL of the instance, for example `https://repo.example.org`.
* A token with the `deploy` permission on the repository to publish to. The token name is the user name and the token secret is the password. See [Publish and consume artifacts](publish-and-consume-artifacts.md#before-you-begin).

{% hint style="warning" %}
Keep the token secret in `~/.m2/settings.xml`, which is outside the project, or pass it in from the environment with `${env.NAME}`. Do not commit it.
{% endhint %}

## Publish

1. Add the token to `~/.m2/settings.xml`. The `id` must match the repository id in the POM.

   ```xml
   <settings>
     <servers>
       <server>
         <id>ingot-releases</id>
         <username><token-name></username>
         <password><token-secret></password>
       </server>
       <server>
         <id>ingot-snapshots</id>
         <username><token-name></username>
         <password><token-secret></password>
       </server>
     </servers>
   </settings>
   ```

   Maven also expands `${env.NAME}` in `settings.xml`. In a CI job, `<password>${env.INGOT_TOKEN_SECRET}</password>` keeps the secret out of the file.

2. Declare the target repositories in the `pom.xml` of the project.

   ```xml
   <distributionManagement>
     <repository>
       <id>ingot-releases</id>
       <url>https://repo.example.org/releases</url>
     </repository>
     <snapshotRepository>
       <id>ingot-snapshots</id>
       <url>https://repo.example.org/snapshots</url>
     </snapshotRepository>
   </distributionManagement>
   ```

3. Deploy.

   ```bash
   mvn deploy
   ```

## Consume

Add the repository to the `pom.xml`:

```xml
<repositories>
  <repository>
    <id>ingot-releases</id>
    <url>https://repo.example.org/releases</url>
  </repository>
</repositories>
```

A public repository needs nothing else. For a private repository, add a `server` entry with the token to `~/.m2/settings.xml`, with the same `id` as the repository, as in the publish example above.

## Route Maven Central through Ingot

A mirror sends the requests of one repository id through another URL. To fetch Maven Central through an Ingot proxy, first create the proxy with [Create a proxy repository](create-proxy-repositories.md). The example below uses a proxy named `central`.

```xml
<settings>
  <mirrors>
    <mirror>
      <id>ingot-central</id>
      <mirrorOf>central</mirrorOf>
      <url>https://repo.example.org/central</url>
    </mirror>
  </mirrors>
</settings>
```

`mirrorOf` decides which requests go to the mirror. `central` covers only the Maven Central requests. `*` covers every repository in the build, so Ingot then answers for all of them. If the proxy or the group needs a token, add a `server` entry whose `id` is `ingot-central`.

## Result

After `mvn deploy`, the artifact is listed under its path in the repository, and it is resolvable with the `ingot-releases` repository in the POM. A second deploy of the same release file returns `409`, as described in [Redeployment rules](publish-and-consume-artifacts.md#redeployment-rules).
