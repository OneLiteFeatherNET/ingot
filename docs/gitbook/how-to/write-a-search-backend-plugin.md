---
description: Write a plugin that adds an artifact search backend to Ingot, with the factory and its API version, the context, the registration event, the contract suite, the busy signal, the blocking open and the redaction rule.
---

# Write a search backend plugin

A search backend answers artifact search and holds the index that search reads. Ingot ships one, the `database` backend. A plugin can add another one, for example a service that runs a dedicated search engine. This page covers what such a plugin provides. It builds on [Write a plugin](write-a-plugin.md), which covers the jar, the plugin class and the service file. Choosing a backend as an operator is covered in [Choose a search backend](choose-a-search-backend.md).

The search backend API is experimental. Its types carry `@ExperimentalIngotSearchApi`, which is an opt-in marker. Code that uses them has to opt in with `@OptIn(ExperimentalIngotSearchApi::class)`, or the compiler warns. The members may change before the API is declared stable.

A backend plugin provides four things: a factory, the backend the factory creates, a registration in the plugin event, and a passing run of the contract suite.

## The factory and its API version

The server asks a factory for a backend. The factory implements `ArtifactSearchBackendFactory`:

| Member | Meaning |
| --- | --- |
| `id` | The value that selects the backend in `searchEngine`. It must be unique on the server. |
| `apiVersion` | The version of the search backend API the factory was built for. It must equal `SEARCH_BACKEND_API_VERSION` of the server. In this release that constant is `1`. |
| `create(context)` | Returns the backend. The server passes a `SearchBackendContext`. |

```kotlin
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackend
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendFactory
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SEARCH_BACKEND_API_VERSION
import net.onelitefeather.ingot.search.backend.SearchBackendContext

@OptIn(ExperimentalIngotSearchApi::class)
class ExampleSearchFactory : ArtifactSearchBackendFactory {

    override val id = "example"
    override val apiVersion = SEARCH_BACKEND_API_VERSION

    override fun create(context: SearchBackendContext): ArtifactSearchBackend =
        ExampleSearchBackend(context)

}
```

A factory with another `apiVersion` is refused. The warning in the log names the factory class and both version numbers. Update the plugin against the API version of the server you run.

## The context

`SearchBackendContext` is what the server gives the backend. Take everything the backend needs from it, and nothing from the environment directly.

| Member | Use |
| --- | --- |
| `clock` | The time source. Read the time from it, and not from the system clock, so tests can fix the time. |
| `instanceId` | The id of this replica. Use it to tell replicas apart in your own data. |
| `environment(name)` | Reads a setting. The `INGOT_` spelling of the name is tried first, then the `REPOSILITE_` spelling. Use it for your own settings, so that they follow the naming of Ingot. |
| `secret(name)` | Reads a secret, from the variable `NAME` or from the file that `NAME_FILE` names. Read every password and token through it. Only values read this way are removed from messages, see [The redaction rule](#the-redaction-rule). |
| `logger()` | A logger. Its messages carry the prefix `SEARCH` and the id of the backend. |

## The backend

The backend implements `ArtifactSearchBackend`. It writes the index, answers the search queries and reports its health. The lifecycle methods are:

* `open()`, which opens the connection to the index. See [The blocking open](#the-blocking-open).
* `close()`, which releases what `open()` acquired.
* `health()`, which returns `BackendHealth.Up` or `BackendHealth.Down` with a message.
* `apply(actions)`, which writes a batch of index changes. It must be idempotent for each action, so that a batch that is retried does no harm. See [The busy signal](#the-busy-signal).

The interface also has the methods that read the index for the search and for the maintenance runs. The contract suite, described below, checks each of them.

The core calls `close()` when the server stops, after the backend was opened.

## The registration event

A factory does not need a service file of its own. It is registered in the plugin event `ArtifactSearchBackendRegistrationEvent`, which carries `register(factory)`. The search plugin emits the event after every plugin has initialized. Register the listener in `initialize` or in `load`, as for any other event:

```kotlin
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import com.reposilite.plugin.event
import net.onelitefeather.ingot.search.backend.ArtifactSearchBackendRegistrationEvent

@Plugin(name = "example-search")
class ExampleSearchPlugin : ReposilitePlugin() {

    override fun initialize(): Facade? {
        event { event: ArtifactSearchBackendRegistrationEvent ->
            event.register(ExampleSearchFactory())
        }

        return null
    }

}
```

The registry checks each factory when it is registered:

* A factory with another API version is refused. The warning names the class and both versions.
* A factory whose id is already registered is refused. The warning names both classes. The first factory registered keeps the id.
* The built-in `database` factory is registered before any plugin, so a plugin cannot replace it.

The plugin can also register in `load`, which runs before the event is emitted. A plugin whose listener runs after the event has been emitted is not seen. The jar still has to be loaded by the plugin loader, which it is when it sits in the plugins directory, or in the bundled directory with its switch on. A backend that is not registered shows the state `NOT_INSTALLED` on the server.

## The contract suite

The server ships a contract suite for search backends. It is an abstract test suite that checks a backend against what the core expects from it: the writes and reads of the index, the paging, the word search and the access checks. The suite is the abstract class `net.onelitefeather.ingot.search.backend.ArtifactSearchBackendContract`, in the test fixtures of `reposilite-backend`. A plugin module in the same Gradle build depends on it the way `nexus-import-plugin` does:

```kotlin
dependencies {
    testImplementation(testFixtures(project(":reposilite-backend")))
}
```

A test class of the plugin extends the abstract class and provides the backend under test by overriding `open(directory, clock)`, which returns the backend opened on an empty index together with the action that closes it. Run it before every release. Do not change the suite to make a backend pass. One requirement of the suite is that the access-mismatch count stays at zero for every backend. A backend that returns a row the caller may not see fails the suite. See [The access check in the core](#the-access-check-in-the-core).

## The busy signal

A backend that cannot take a batch right now says so by throwing `BackendBusyException` from `apply`. The exception takes a message and a `retryAfter` delay, the time before the next try. Ingot then releases the claims of that batch and retries it after the delay. A busy answer does not count as an attempt, so the entries of the batch do not move towards the dead-letter state. The release covers only the batch that failed, so the rest of the queue continues. Any other exception counts as an attempt. After ten failed attempts an entry becomes a dead letter, as it does for the database backend.

## The blocking open

`open()` may block, for example while it connects to a service. Ingot calls it on the IO pool, not on a request thread. The backend is not `AVAILABLE` until `open()` returns, and search answers `503` until then.

Two rules follow:

* If `open()` throws, the state becomes `OPEN_FAILED` and Ingot calls `open()` again every 30 seconds. Write `open()` so that a second call after a failure is safe.
* Ingot cannot interrupt a plugin. An `open()` that never returns keeps its IO thread for good. Set timeouts on every connection the backend makes, so that `open()` returns, with a result or with an exception.

## The access check in the core

The core checks every file and repository a backend returns against the access value of the caller. A row that fails the check is dropped from the response, logged at error level and counted. The count is shown in the state of the backend as `accessMismatches`. A backend should never return such a row. The contract suite checks it.

## The redaction rule

The reason of a state and the message of a down health reach the status and the body of a `503` answer. Ingot removes the user information of every URL in them before they are shown, that is the part before the `@` in `scheme://user:password@host`. The `detail` and `healthDetail` fields and the log lines of the state pass the same removal.

Ingot also removes every value that the backend reads through `secret()` from its messages, and each occurrence is shown as `***`. The removal covers the values the backend has read so far, and it applies to the reason, the health message and the log lines of the backend. It cannot recognise a value that the backend derives from a secret, such as a prefix or an encoded form, so a backend must still not put such a value into an exception message or a log line. Put a secret into a URL only as user information, so that the rule above removes it.

## Before you release

* The factory declares `SEARCH_BACKEND_API_VERSION` of the server you build against.
* The id is unique and is not `database`.
* The contract suite passes, and the access-mismatch count stays zero.
* `open()` sets a timeout on every connection, and a second call after a failure is safe.
* Busy answers are thrown as `BackendBusyException`, and no other exception is used for back pressure.
* No secret goes into an exception message or a log line.

## Related

* [Choose a search backend](choose-a-search-backend.md) covers the states of a backend and the switch between backends.
* [Plugin API](../reference/plugin-api.md#events) lists the events of the plugin lifecycle.
* [Write a plugin](write-a-plugin.md) covers the jar, the plugin class and the service file.
