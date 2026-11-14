---
description: Publish and consume Ingot artifacts from a Gradle build written in the Kotlin DSL, with maven-publish, token credentials from gradle.properties or the environment, and a private repository.
---

# Publish and consume with Gradle (Kotlin DSL)

Gradle declares the repositories in the build script and reads the token from properties named after the repository. This page covers the Kotlin DSL. The Groovy DSL is in [Publish and consume with Gradle (Groovy DSL)](publish-and-consume-with-gradle-groovy.md). The URL scheme, the tokens and the redeployment rules are in [Publish and consume artifacts](publish-and-consume-artifacts.md).

## Before you begin

* The URL of the instance, for example `https://repo.example.org`.
* A token with the `deploy` permission on the repository to publish to, and for a private repository a token with `read`. The token name is the user name and the token secret is the password. See [Publish and consume artifacts](publish-and-consume-artifacts.md#before-you-begin).

{% hint style="warning" %}
Put the token in `~/.gradle/gradle.properties`, outside the project, or in the environment of the CI job. Do not commit it.
{% endhint %}

## Publish

1. Add the token to `~/.gradle/gradle.properties`. Gradle reads the user name and password from properties named after the repository. The repository name in the next step is `ingotReleases`, so the properties are `ingotReleasesUsername` and `ingotReleasesPassword`.

   ```properties
   ingotReleasesUsername=<token-name>
   ingotReleasesPassword=<token-secret>
   ```

   In CI, Gradle also reads the same names from environment variables with the `ORG_GRADLE_PROJECT_` prefix, for example `ORG_GRADLE_PROJECT_ingotReleasesPassword`.

2. Declare the target in the build script.

   ```kotlin
   plugins {
     `java-library`
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

## Consume

A public repository needs no credentials.

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

For a private repository, give it a name, add the credentials and the basic authentication block, and set the properties for that name in `~/.gradle/gradle.properties`. The name below is `ingotPrivate`, so the properties are `ingotPrivateUsername` and `ingotPrivatePassword`.

```kotlin
repositories {
  maven {
    name = "ingotPrivate"
    url = uri("https://repo.example.org/private")
    credentials(PasswordCredentials::class)
    authentication {
      create<BasicAuthentication>("basic")
    }
  }
}
```

## Result

After `./gradlew publish`, the artifact is listed under its path in the repository, and a build that declares the repository resolves it. A second publish of a release file returns `409`, as described in [Redeployment rules](publish-and-consume-artifacts.md#redeployment-rules).
