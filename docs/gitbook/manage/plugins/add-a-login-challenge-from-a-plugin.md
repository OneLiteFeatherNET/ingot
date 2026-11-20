---
description: Write a plugin that asks a delayed dashboard login for a challenge, such as a CAPTCHA or a proof of work, with the provider interface, its registration, the wire format of the login request and the failure rules.
---

# Add a login challenge from a plugin

A login challenge provider lets a plugin ask a dashboard login for an extra answer, for example a CAPTCHA or a proof of work, while the [account throttle](../../references/settings/login-providers.md#account-throttle) delays its user name. Ingot ships no provider and calls no third-party service: without a plugin that registers one, a delayed login gets the plain `429` with `Retry-After`, as before. Whatever service a provider talks to, and whatever data it sends there, is the choice and the responsibility of the plugin. This page builds on [Write a plugin](write-a-plugin.md), which covers the jar, the plugin class and the service file.

The interface is experimental. Its types carry `@ExperimentalIngotPluginApi`, which is an opt-in marker. Code that uses them has to opt in with `@OptIn(ExperimentalIngotPluginApi::class)`, or the compiler warns. The members may change before the API is declared stable.

## When the provider is asked

The provider is asked only when all of these hold:

* The account throttle is on (`INGOT_LOGIN_ACCOUNT_THROTTLE=true`). With the switch off, nothing is ever delayed, so no provider is asked.
* The request is a dashboard login, `POST /api/auth/login`. Basic, Bearer and console clients are never asked for a challenge. They keep the plain `429`.
* The throttle delays the name of that login at that moment.

A login that is not delayed never reaches the provider, and a `challenge` it sends is ignored.

## The provider

A provider implements `LoginChallengeProvider` from `net.onelitefeather.ingot.identity.login.challenge`:

| Member | Meaning |
| --- | --- |
| `id` | The id of the provider. It must not be blank and must stay the same across restarts. Clients send it back in `challenge.provider`. |
| `parameters(context)` | The public values a client needs to present or solve a new challenge, such as a site key, a seed or a difficulty. They are sent to the client unchanged and must never hold a secret. The default is an empty object. |
| `verify(context, answer)` | Whether `answer`, the `challenge.answer` object of the login request, solves a challenge of this provider. The default verifies nothing and returns `false`. |

Every method except `id` has a default, so a method added in a later release does not break a plugin built against this one.

The `context` is a `LoginChallengeContext` with the `username` the login signs in with and the client `address`. It never carries the password.

A provider that issues single-use challenges, such as a seed for a proof of work, is responsible for refusing a replayed answer. Ingot does not remember answers.

```kotlin
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeContext
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeProvider
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

@OptIn(ExperimentalIngotPluginApi::class)
class ExampleChallengeProvider(private val puzzles: ExamplePuzzles) : LoginChallengeProvider {

    override val id = "example-puzzle"

    override fun parameters(context: LoginChallengeContext): Map<String, Any?> =
        mapOf("seed" to puzzles.issue(context.username), "difficulty" to 18)

    override fun verify(context: LoginChallengeContext, answer: Map<String, Any?>): Boolean {
        val seed = answer["seed"] as? String ?: return false
        val nonce = answer["nonce"] as? String ?: return false
        return puzzles.redeem(context.username, seed, nonce)
    }

}
```

## The registration event

A provider is registered in the plugin event `LoginChallengeRegistrationEvent`, which carries `register(provider)`. The login plugin emits the event once at start, after every plugin has initialized. Register the listener in `initialize` or in `load`, as for any other event:

```kotlin
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import net.onelitefeather.ingot.identity.login.challenge.LoginChallengeRegistrationEvent
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi

@OptIn(ExperimentalIngotPluginApi::class)
@Plugin(name = "example-challenge")
class ExampleChallengePlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        event { event: LoginChallengeRegistrationEvent ->
            event.register(ExampleChallengeProvider(ExamplePuzzles()))
        }

        return null
    }

}
```

At most one provider is used:

* The first registered provider wins. Registration follows the plugin load order.
* A later provider is ignored. The warning names the ignored provider and the one already registered, with their ids and classes.
* A provider with a blank id is ignored with a warning.
* `register` never throws.

When a provider is in use, the start log says so in one line that names its id.

## The wire format

A delayed dashboard login with a registered provider is answered `429` with `Retry-After`, as without one, and a body of the shape `ChallengeRequest`. It is the error body of the `429` with two more members:

```json
{
  "status": 429,
  "title": "Too Many Requests",
  "type": "about:blank",
  "message": "challenge_required",
  "detail": "challenge_required",
  "instance": "/api/auth/login",
  "provider": "example-puzzle",
  "parameters": { "seed": "c2VlZA", "difficulty": 18 }
}
```

`message` and `detail` carry the code `challenge_required`. `provider` is the id of the registered provider, and `parameters` is the object that `parameters(context)` returned.

The client solves the challenge and sends the same login again, with the same `provider`, `username` and `password`, plus the optional member `challenge` of the shape `ChallengeResponse`:

```json
{
  "provider": "local",
  "username": "alice",
  "password": "...",
  "challenge": {
    "provider": "example-puzzle",
    "answer": { "seed": "c2VlZA", "nonce": "48213" }
  }
}
```

`challenge.provider` names the provider, and `challenge.answer` is handed to `verify` unchanged. Both members are required.

* A verified answer lets exactly one password check through. It does not clear the delay: a wrong password counts as a failure and keeps the name delayed, and a right password signs in as usual. A user with a second factor then gets the second factor step, as without a challenge.
* A missing, malformed or wrong answer, or an answer for another provider, gets a fresh `ChallengeRequest` and no password check.
* Waiting out `Retry-After` stays a way to the next password check. A challenge is an additional way through, never the only one.

A login request without `challenge` is answered exactly as before, so clients that do not know the extension keep working. The OpenAPI document lists `ChallengeResponse` as the optional `challenge` member of `LoginRequest`, and `ChallengeRequest` as an alternative body of the login `429`.

The dashboard bundled with Ingot does not present challenges. A provider whose users sign in through the bundled dashboard login form gets the plain delay message there, so the challenge needs a client that reads `ChallengeRequest`.

## When the provider fails

The provider never stops a login from being answered:

* A provider that throws is logged as a warning with its id and the exception class, never with the exception message, which may echo the answer. The login gets the plain `429` of the throttle, and no password check goes through.
* Each call of `parameters` or `verify` has to answer within two seconds. A call that takes longer counts as failed, with the same result.

Keep both methods fast, and run any call to an outside service with a timeout of its own.
