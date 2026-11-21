/*
 * Copyright (c) 2023 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.onelitefeather.ingot.cli

import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.io.InputStream
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.system.exitProcess

/** Options that take a value. The token option is listed only to be refused with a pointer to the variables. */
private val VALUE_OPTIONS = setOf("url", "context", "token-file", "credential-helper", "ca-cert", "output", "timeout", "color", "token")
private val SWITCH_OPTIONS = setOf("insecure", "yes", "keep-going")

private const val USAGE = """Usage: ingot [options] exec <command> [args...]
       ingot [options] exec -            (commands from standard input, one per line)
       ingot [options]                   (interactive, on a terminal)

Options: --url <url>, --context <name>, --token-file <path>, --credential-helper <program>,
         --ca-cert <path>, --insecure, --output text|json, --timeout <seconds>, --yes, --keep-going
Variables: INGOT_URL, INGOT_TOKEN, INGOT_TOKEN_FILE, INGOT_CREDENTIAL_HELPER, INGOT_CA_CERT, INGOT_CONTEXT, INGOT_CONFIG
Exit codes: 0 success, 1 command failed, 2 usage or unknown command, 3 confirmation required, 4 denied, 5 unreachable, 6 server too old"""

/** The `client` of the hello, which the server writes into the audit event. */
private val CLIENT_NAME = "ingot-cli/" + (IngotClient::class.java.`package`?.implementationVersion ?: "dev")

fun main(args: Array<String>) {
    val interactive = System.console() != null
    exitProcess(runCli(args.toList(), System.getenv(), System.`in`, System.out, System.err, interactive) { createTerminal() })
}

private fun createTerminal(): Terminal? =
    runCatching { TerminalBuilder.builder().system(true).build() }.getOrNull()

/**
 * The whole CLI behind one function, so that tests can drive it with their own streams. [interactive] is true only when
 * standard input and output are a terminal. [terminal] is asked for only by the interactive mode.
 */
fun runCli(
    args: List<String>,
    env: Map<String, String>,
    stdin: InputStream,
    out: PrintStream,
    err: PrintStream,
    interactive: Boolean = false,
    terminal: () -> Terminal? = { null },
): Int {
    val flags = mutableMapOf<String, String>()
    val rest = mutableListOf<String>()
    var index = 0
    while (index < args.size) {
        val arg = args[index]
        val name = arg.removePrefix("--")
        when {
            arg.startsWith("--") && name in SWITCH_OPTIONS -> flags[name] = "true"
            arg.startsWith("--") && name in VALUE_OPTIONS -> {
                val value = args.getOrNull(index + 1)
                if (value == null) {
                    err.println("Option --$name needs a value.")
                    return ExitCode.USAGE
                }
                flags[name] = value
                index++
            }
            // Anything else is part of the command, so that a command argument like "-s" reaches the server untouched.
            else -> rest.add(arg)
        }
        index++
    }

    if (flags.containsKey("token")) {
        err.println(
            "The --token option is not accepted, because the token would reach the shell history and the process list. " +
                "Use INGOT_TOKEN, INGOT_TOKEN_FILE or a credentials helper."
        )
        return ExitCode.USAGE
    }

    val config = try {
        ConfigResolver.resolve(flags, env, readConfigFile(env))
    } catch (failure: ConfigException) {
        err.println(failure.message)
        return ExitCode.USAGE
    }

    // No words means the interactive mode. The exec word starts a command. Any other word is refused.
    val isRepl = rest.isEmpty()
    if (!isRepl && rest.first() != "exec") {
        err.println("Use 'ingot exec <command>' to run a command.")
        err.println(USAGE)
        return ExitCode.USAGE
    }
    val commandArguments = rest.drop(1)
    if (!isRepl && commandArguments.isEmpty()) {
        err.println(USAGE)
        return ExitCode.USAGE
    }

    val url = config.url
    if (url == null) {
        err.println("No server URL: set INGOT_URL, --url or a context in the config file.")
        return ExitCode.USAGE
    }

    if (config.insecure) {
        err.println("WARNING: TLS certificate verification is disabled (--insecure). Do not use this against a server you do not control.")
    }

    val token = try {
        TokenResolver.resolve(config, env, warn = { err.println("WARNING: $it") })
    } catch (failure: ConfigException) {
        err.println(failure.message)
        return ExitCode.USAGE
    }

    val timeout = Duration.ofSeconds(config.timeoutSeconds)
    val http = httpClient(config, err)
    val client = IngotClient(url, token, timeout, http)
    val json = config.output == "json" || flags["output"] == "json"
    val yes = flags.containsKey("yes")
    // The server's colour reaches command output and log lines only when it can show. The CLI's own messages have none.
    val colour = colourEnabled(stdoutIsTerminal = interactive, env = env)
    val commandOut = if (colour) out else withoutColour(out)
    val commandErr = if (colour) err else withoutColour(err)

    if (isRepl) {
        val term = terminal() ?: run {
            err.println(USAGE)
            return ExitCode.USAGE
        }
        val connector = ConsoleConnector { resume, onLog ->
            ConsoleSocket.open(url, token, CLIENT_NAME, http, timeout, { log -> onLog(if (colour) log else log.copy(text = stripAnsi(log.text))) }, resume)
        }
        val history = ReplHistory.defaultPath(env).takeIf { ReplHistory.prepare(it) }
        if (history == null) err.println("WARNING: the history file cannot be created, so the history of this session is not kept.")
        // `:context <name>` is accepted when the config file resolves with that context. The session then ends, and the
        // CLI starts again on the same terminal as if --context had been given, so the new context is set up like the first.
        var next: String? = null
        val switchContext = { name: String ->
            try {
                ConfigResolver.resolve(flags + ("context" to name), env, readConfigFile(env))
                next = name
                null
            } catch (failure: ConfigException) {
                failure.message
            }
        }
        val exit = runRepl(client, connector, term, config.context ?: url, json = json, yes = yes, out = commandOut, err = commandErr, historyFile = history, switchContext = switchContext)
        return next?.let { runCli(args + listOf("--context", it), env, stdin, out, err, interactive) { term } } ?: exit
    }

    val lines = if (commandArguments == listOf("-")) {
        scriptLines(stdin.readBytes().toString(Charsets.UTF_8))
    } else {
        if (commandArguments.any { it.contains(' ') }) {
            err.println("An argument contains a space. Commands are sent as one line that the server splits on spaces, so the argument cannot be passed. Refusing to send it.")
            return ExitCode.USAGE
        }
        listOf(commandArguments.joinToString(" "))
    }

    val options = ExecOptions(
        json = json,
        yes = yes,
        keepGoing = flags.containsKey("keep-going"),
        interactive = interactive,
        confirm = { command, reason -> askYesNo("Run '$command'${if (reason.isEmpty()) "" else " ($reason)"}? [y/N] ", err) },
    )
    return Exec(client, commandOut, commandErr, options).runAll(lines)
}

private fun readConfigFile(env: Map<String, String>): ConfigFile {
    val path = ConfigFile.defaultPath(env)
    return if (Files.exists(path)) ConfigFile.parse(Files.readString(path)) else ConfigFile.parse("")
}

/** Reads a yes or no answer from the terminal. Anything but `y` or `yes` is no. */
private fun askYesNo(question: String, err: PrintStream): Boolean {
    err.print(question)
    err.flush()
    return System.console()?.readLine()?.trim()?.lowercase()?.let { it == "y" || it == "yes" } ?: false
}

private fun httpClient(config: CliConfig, err: PrintStream): HttpClient {
    val builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.timeoutSeconds))
    when {
        config.insecure -> builder.sslContext(insecureSslContext())
        config.caCert != null -> builder.sslContext(sslContextWithCa(config.caCert, err))
    }
    return builder.build()
}

/** The default trust store plus one more CA. The default store keeps working, so a public certificate still verifies. */
private fun sslContextWithCa(caCert: Path, err: PrintStream): SSLContext {
    val certificate = Files.newInputStream(caCert).use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
    val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null, null)
        setCertificateEntry("ingot-ca", certificate)
    }
    val defaultFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
    val caFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
    val trusted = defaultFactory.trustManagers.filterIsInstance<X509TrustManager>() + caFactory.trustManagers.filterIsInstance<X509TrustManager>()
    return SSLContext.getInstance("TLS").apply { init(null, arrayOf(CompositeTrustManager(trusted)), null) }
}

private fun insecureSslContext(): SSLContext =
    SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), null)
    }

/** Accepts a chain when any of its trust managers does. */
private class CompositeTrustManager(private val managers: List<X509TrustManager>) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        check(chain, authType) { manager, c -> manager.checkClientTrusted(c, authType) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        check(chain, authType) { manager, c -> manager.checkServerTrusted(c, authType) }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        managers.flatMap { it.acceptedIssuers.toList() }.toTypedArray()

    private fun check(chain: Array<out X509Certificate>, authType: String, verify: (X509TrustManager, Array<out X509Certificate>) -> Unit) {
        var failure: Exception? = null
        for (manager in managers) {
            try {
                verify(manager, chain)
                return
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("No trust manager configured")
    }
}
