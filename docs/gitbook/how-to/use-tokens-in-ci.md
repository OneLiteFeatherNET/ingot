---
description: Create a token for a machine account in Ingot and use it in CI to publish with Maven, Gradle and GitHub Actions. Covers the Basic credentials, the ingot_ secret format, legacy tokens, verification and rotation.
---

# Use tokens in CI

This page shows how a build publishes to Ingot with a token. The token belongs to a machine account, so the build keeps working when a person leaves the team.

## Prerequisites

* A machine account for the build, such as `ci-deploy`. See [Manage users, teams and machine accounts](manage-users-teams-and-machine-accounts.md).
* Rights for the machine account, given through a team. See [Grant permissions with nodes](grant-permissions-with-nodes.md).
* The right to create tokens: the instance admin role, or the `ingot.admin.tokens` node.
* A secret store in your CI system, such as the Actions secrets of a GitHub repository.

## How the credentials work

Ingot accepts a token in two ways:

* **Basic authentication.** The username is the name of the token, and the password is its secret. This is what Maven and Gradle send.
* **A Bearer header.** `Authorization: Bearer <secret>` works for every token whose secret starts with `ingot_`. Ingot finds the token from the id in the secret.

A new secret looks like `ingot_<id>_<random>`. The id names the row of the token, and the random part protects it. Ingot shows the secret once, when the token is created. It stores only a hash.

Token names are unique across the instance, cannot contain a colon, and can be up to 255 characters long.

## Steps

1. Create the token for the machine account. In the dashboard, open **Access**, then **Machine accounts**, then the account. In the **Tokens** section, select **Create token**. Enter a name such as `github-releases` and an expiry. The dashboard suggests 90 days. Copy the secret from the dialog.

   With the identity API, the call is:

   ```bash
   curl -X POST "$INGOT_URL/api/identity/principals/ci-deploy/tokens" \
     -H "Authorization: Bearer $INGOT_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"name": "github-releases", "description": "Publishes from GitHub Actions", "expiresAt": "2027-01-15T23:59:59Z"}'
   ```

   The answer holds the token and the `secret`. Store the secret at once.

2. Store the token name and the secret as secrets of your CI system. In GitHub, open **Settings**, then **Secrets and variables**, then **Actions**. Create `INGOT_TOKEN_NAME` with the token name and `INGOT_TOKEN_SECRET` with the secret.

3. Point the build at the repository. The URL of a release repository is `https://ingot.example.com/releases`. Use the same URL in the examples below.

4. Configure the build tool. Choose the tab for your tool.

{% tabs %}
{% tab title="Maven" %}

In `pom.xml`, name the target repository. The `id` must match the `id` of the server in `settings.xml`:

```xml
<distributionManagement>
  <repository>
    <id>ingot</id>
    <url>https://ingot.example.com/releases</url>
  </repository>
</distributionManagement>
```

In `~/.m2/settings.xml`, Maven reads the credentials from the environment. Maven resolves `${env.NAME}` in this file, so the secret never sits in the file:

```xml
<settings>
  <servers>
    <server>
      <id>ingot</id>
      <username>${env.INGOT_TOKEN_NAME}</username>
      <password>${env.INGOT_TOKEN_SECRET}</password>
    </server>
  </servers>
</settings>
```

Run `mvn deploy`.

{% endtab %}
{% tab title="Gradle (Kotlin)" %}

In `build.gradle.kts`, add the repository and the credentials. The credentials come from environment variables:

```kotlin
publishing {
    repositories {
        maven {
            name = "ingot"
            url = uri("https://ingot.example.com/releases")
            credentials {
                username = providers.environmentVariable("INGOT_TOKEN_NAME").orNull
                password = providers.environmentVariable("INGOT_TOKEN_SECRET").orNull
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
```

Run `./gradlew publish`.

{% endtab %}
{% tab title="Gradle (Groovy)" %}

In `build.gradle`, add the repository and the credentials. The credentials come from environment variables:

```groovy
publishing {
    repositories {
        maven {
            name = 'ingot'
            url = 'https://ingot.example.com/releases'
            credentials {
                username = providers.environmentVariable('INGOT_TOKEN_NAME').orNull
                password = providers.environmentVariable('INGOT_TOKEN_SECRET').orNull
            }
            authentication {
                basic(BasicAuthentication)
            }
        }
    }
}
```

Run `./gradlew publish`.

{% endtab %}
{% tab title="GitHub Actions" %}

Pass the two secrets to the step that publishes. The step sees them as environment variables, and GitHub hides their values in the log:

```yaml
name: publish
on:
  push:
    branches: [main]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "21"
      - name: Publish
        run: ./gradlew publish
        env:
          INGOT_TOKEN_NAME: ${{ secrets.INGOT_TOKEN_NAME }}
          INGOT_TOKEN_SECRET: ${{ secrets.INGOT_TOKEN_SECRET }}
```

For Maven, use the same two variables with the `settings.xml` from the Maven tab. Write that file in a step before `mvn deploy` runs.

{% endtab %}
{% endtabs %}

## Legacy tokens

Tokens from Reposilite 3.x, and tokens created before the `ingot_` format, have no prefix. They keep their name and their secret, and they work over Basic authentication only. A Bearer header does not accept them. Keep their name and secret in the CI secrets, and change nothing else in the build. For the migration, see [Token migration](../explanation/token-migration.md).

## Verify the token

The check endpoint answers for the token itself, so a build can test its rights before it publishes. Replace the path with a file that the build really publishes:

```bash
curl -u "$INGOT_TOKEN_NAME:$INGOT_TOKEN_SECRET" \
  "https://ingot.example.com/api/identity/check?principal=ci-deploy&token=github-releases&repository=releases&path=net/onelitefeather/mycelium-bom/1.0.0/mycelium-bom-1.0.0.pom&action=deploy"
```

The answer has `"allowed": true` when the token may deploy that path. The check does not publish anything. For more about the answer, see [Check what a principal can do](check-access.md).

## Rotate and revoke

A machine account's token cannot be rotated in place. To change the secret, create a new token, update the CI secrets, run a build, and then delete the old token:

```bash
curl -X DELETE "$INGOT_URL/api/identity/principals/ci-deploy/tokens/github-releases" \
  -H "Authorization: Bearer $INGOT_TOKEN"
```

Rotation in place exists only for the tokens a signed-in user owns, in **My account**. It gives the token a new secret, and the old secret stops working at once on the instance that handles the request. With several replicas, another replica can accept the old secret for up to one minute. See [Access tokens and routes](../reference/access-tokens-and-routes.md#when-a-change-takes-effect).

## Result

After the steps, the build publishes with the token, and the check call answers `"allowed": true` for the path it deploys. A wrong or revoked secret makes the request fail with `401 Unauthorized`.

{% hint style="warning" %}
A token without an expiry stays valid until someone revokes it. Set an expiry when you create the token, and rotate it before it runs out. The API sets no expiry when you omit `expiresAt`.
{% endhint %}

{% hint style="warning" %}
A token can only narrow the rights of its machine account. To give a build fewer rights than the account has, limit the token itself. See [Restrict a token to artifacts](restrict-token-to-artifacts.md).
{% endhint %}
