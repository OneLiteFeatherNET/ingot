---
description: Publish and consume Ingot artifacts from a Gradle build written in the Groovy DSL, with maven-publish, token credentials from gradle.properties, environment variables or explicit properties, and a private repository.
---

# Publish and consume with Gradle (Groovy DSL)

The Groovy DSL resolves the same repositories and credentials as the Kotlin DSL. Only the syntax of the build script differs. The Kotlin version is in [Publish and consume with Gradle (Kotlin DSL)](publish-and-consume-with-gradle-kotlin.md). The URL scheme, the tokens and the redeployment rules are in [Publish and consume artifacts](publish-and-consume-artifacts.md).

## Before you begin

* The URL of the instance, for example `https://repo.example.org`.
* A token with the `deploy` permission on the repository to publish to, and for a private repository a token with `read`. The token name is the user name and the token secret is the password. See [Publish and consume artifacts](publish-and-consume-artifacts.md#before-you-begin).

{% hint style="warning" %}
Put the token in `~/.gradle/gradle.properties`, outside the project, or in the environment of the CI job. Do not commit it.
{% endhint %}

## Publish

1. Add the token to `~/.gradle/gradle.properties`. With `credentials(PasswordCredentials)`, Gradle reads the properties named after the repository, here `ingotReleases`.

   ```properties
   ingotReleasesUsername=<token-name>
   ingotReleasesPassword=<token-secret>
   ```

   In CI, the same names work as environment variables with the `ORG_GRADLE_PROJECT_` prefix, for example `ORG_GRADLE_PROJECT_ingotReleasesPassword`.

2. Declare the target in `build.gradle`.

   ```groovy
   plugins {
     id 'java-library'
     id 'maven-publish'
   }

   publishing {
     repositories {
       maven {
         name = 'ingotReleases'
         url = uri('https://repo.example.org/releases')
         credentials(PasswordCredentials)
         authentication {
           basic(BasicAuthentication)
         }
       }
     }
     publications {
       maven(MavenPublication) {
         groupId = 'com.example'
         artifactId = 'library'
         version = '1.0.0'
         from components.java
       }
     }
   }
   ```

   If the properties must have other names, read the values explicitly instead of using `PasswordCredentials`. Inside the `maven` block, replace the `credentials(PasswordCredentials)` line with this block. The build then takes the values from any source you choose, for example a property set in a CI job.

   ```groovy
   credentials {
     username = project.findProperty('ingotUsername')
     password = project.findProperty('ingotPassword')
   }
   ```

3. Publish.

   ```bash
   ./gradlew publish
   ```

## Consume

A public repository needs no credentials.

```groovy
repositories {
  maven {
    url = uri('https://repo.example.org/releases')
  }
}

dependencies {
  implementation 'com.example:library:1.0.0'
}
```

For a private repository, name it, add `credentials(PasswordCredentials)` and the basic authentication block, and set the properties for that name. The name below is `ingotPrivate`, so the properties are `ingotPrivateUsername` and `ingotPrivatePassword`.

```groovy
repositories {
  maven {
    name = 'ingotPrivate'
    url = uri('https://repo.example.org/private')
    credentials(PasswordCredentials)
    authentication {
      basic(BasicAuthentication)
    }
  }
}
```

## Result

After `./gradlew publish`, the artifact is listed under its path in the repository, and a build that declares the repository resolves it. A second publish of a release file returns `409`, as described in [Redeployment rules](publish-and-consume-artifacts.md#redeployment-rules).
