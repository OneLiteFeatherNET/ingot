---
description: Deploy artifacts to an Ingot repository with Maven and Gradle, consume them from the same repository, and follow the redeployment, snapshot and checksum rules.
---

# Publish and consume artifacts

Publish a build to an Ingot repository, then resolve it from Maven or Gradle. This page covers the Maven and Gradle clients. The examples use the repositories of a new instance: `releases` and `snapshots`, which are public, and `private`, which needs a token to read.

## Before you begin

* The URL of the instance, for example `https://repo.example.org`. A repository is at `https://repo.example.org/<repository>`.
* An access token with the `deploy` permission on the repository to publish to. That is the node `ingot.repository.<repository>.deploy`. Ingot checks the token with HTTP Basic authentication: the token name is the user name, and the token secret is the password.
* For a private repository, a token with the `read` permission. A public repository needs no token to read.

{% hint style="warning" %}
Put the token secret in a file outside the project, for example `~/.m2/settings.xml` or `~/.gradle/gradle.properties`. Do not commit it.
{% endhint %}

## Publish

{% tabs %}
{% tab title="Maven" %}
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
{% endtab %}

{% tab title="Gradle" %}
1. Add the token to `~/.gradle/gradle.properties`. Gradle reads the user name and password from properties named after the repository.

   ```properties
   ingotReleasesUsername=<token-name>
   ingotReleasesPassword=<token-secret>
   ```

2. Declare the target in the build script. This is the Kotlin DSL.

   ```kotlin
   plugins {
     `maven-publish`
   }

   publishing {
     repositories {
       maven {
         name = "ingotReleases"
         url = uri("https://repo.example.org/releases")
         credentials(PasswordCredentials::class)
         authentication {
           create<BasicAuthentication>("basic")
         }
       }
     }
     publications {
       create<MavenPublication>("maven") {
         groupId = "com.example"
         artifactId = "library"
         version = "1.0.0"
         from(components["java"])
       }
     }
   }
   ```

3. Publish.

   ```bash
   ./gradlew publish
   ```
{% endtab %}
{% endtabs %}

To publish a file without a build tool, send it with `PUT`. The path is the group path, the artifact id, the version and the file name.

```bash
curl -u "<token-name>:<token-secret>" -T lib-1.0.0.jar \
  -H "X-Generate-Checksums: true" \
  https://repo.example.org/releases/com/example/library/1.0.0/library-1.0.0.jar
```

## Consume

{% tabs %}
{% tab title="Maven" %}
Add the repository to the `pom.xml`. Add a `server` entry with the token to `~/.m2/settings.xml` only when the repository is private.

```xml
<repositories>
  <repository>
    <id>ingot-releases</id>
    <url>https://repo.example.org/releases</url>
  </repository>
</repositories>
```
{% endtab %}

{% tab title="Gradle" %}
Add the repository to the build script. A public repository needs no credentials. For a private repository, add `credentials(PasswordCredentials::class)` and the basic authentication block from the publish example, and set the properties for that repository name in `~/.gradle/gradle.properties`.

```kotlin
repositories {
  maven {
    url = uri("https://repo.example.org/releases")
  }
}

dependencies {
  implementation("com.example:library:1.0.0")
}
```
{% endtab %}
{% endtabs %}

Run the build. Every artifact is a plain `GET` on its path, so you can check one with `curl -I https://repo.example.org/releases/com/example/library/1.0.0/library-1.0.0.jar`.

## Redeployment rules

Ingot decides for each file, not for each version.

* A file that does not exist yet is accepted. Publishing a new classifier, such as `library-1.0.0-sources.jar`, into an existing version works.
* A file that exists is refused with `409 Redeployment is not allowed`, unless the repository has `redeployment` turned on.
* Maven metadata files, such as `maven-metadata.xml`, are always accepted, because they change with every build.
* The default for `redeployment` is off. Keep it off for `releases`, because a replaced file changes what every consumer resolves.

A failed upload can leave a file behind. If the jar was stored and the upload stopped before the checksums, the retry for the jar gets `409`. Remove the file with a `DELETE` request, which needs the `delete` permission, and publish again.

```bash
curl -u "<token-name>:<token-secret>" -X DELETE \
  https://repo.example.org/releases/com/example/library/1.0.0/library-1.0.0.jar
```

Turn `redeployment` on only for a repository where you need to replace files, and turn it off again afterwards.

## Snapshots

Publish snapshots to a repository such as `snapshots`. The directory of a `-SNAPSHOT` version holds the builds and a `maven-metadata.xml` that names the newest build. Clients resolve a snapshot through that metadata.

By default, Ingot does not keep older builds. When the metadata of a new build is deployed, Ingot removes the files of the older builds of the same snapshot version. Turn on `preserveSnapshots` on the repository to keep them.

Maven and Gradle upload snapshot builds under timestamped names by default. This page did not check that against the client versions. If a client uploads a snapshot under a fixed name, the second upload is refused unless `redeployment` is on.

## Checksums

Maven and Gradle upload their own checksum files next to each artifact. Ingot stores them as sent.

When a client does not upload checksums, send the header `X-Generate-Checksums: true` with the `PUT` request, as in the publish example above. Ingot then writes four files next to the artifact: `.md5`, `.sha1`, `.sha256` and `.sha512`. It writes them even when files with those names already exist.

Ingot does not compare an uploaded checksum with the artifact. To check a download, compare the hash of the file with the stored checksum.

```bash
sha1sum library-1.0.0.jar
curl https://repo.example.org/releases/com/example/library/1.0.0/library-1.0.0.jar.sha1
```

The two values must be equal.

## Result

After `mvn deploy` or `./gradlew publish`, the artifact is listed under its path in the repository, and a search for its name finds it (see [Search for artifacts](search-for-artifacts.md)). A second publish of a release file returns `409`. The checksum files next to the artifact match the artifact.
