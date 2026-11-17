package net.onelitefeather.ingot.cli

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

private const val PASSWORD = "changeit"
private const val SAN = "san=dns:localhost,ip:127.0.0.1"

/**
 * A certificate authority and a server certificate for 127.0.0.1 and localhost, generated with keytool into a temporary
 * directory. The CA certificate is written as PEM for the CLI, the server certificate lives in a PKCS12 keystore for the
 * test server. Every test builds its own pair, so no state is shared between tests.
 */
private class TestPki(private val dir: Path) {

    val caCertificate: Path = dir.resolve("ca.pem")
    val serverKeystore: Path = dir.resolve("server.p12")

    init {
        val caStore = dir.resolve("ca.p12")
        val csr = dir.resolve("server.csr")
        val serverCertificate = dir.resolve("server.pem")

        keytool(caStore, "-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Ingot Test CA",
            "-ext", "bc:c=ca:true", "-ext", "ku:c=keyCertSign,cRLSign", "-validity", "2")
        keytool(caStore, "-exportcert", "-alias", "ca", "-rfc", "-file", caCertificate.toString())
        keytool(serverKeystore, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=localhost", "-validity", "2")
        keytool(serverKeystore, "-certreq", "-alias", "server", "-ext", SAN, "-file", csr.toString())
        keytool(caStore, "-gencert", "-alias", "ca", "-infile", csr.toString(), "-outfile", serverCertificate.toString(), "-rfc",
            "-validity", "2", "-ext", SAN, "-ext", "eku=serverAuth", "-ext", "ku:c=digitalSignature,keyEncipherment")
        keytool(serverKeystore, "-importcert", "-alias", "ca", "-file", caCertificate.toString(), "-noprompt")
        keytool(serverKeystore, "-importcert", "-alias", "server", "-file", serverCertificate.toString())
    }

    private fun keytool(keystore: Path, vararg arguments: String) {
        val keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
        val command = listOf(keytool) + arguments + listOf("-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD)
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "keytool failed with ${if (process.isAlive) "a timeout" else "exit code ${process.exitValue()}"}: ${arguments.joinToString(" ")}"
        }
    }

}

/**
 * An HTTPS stand-in for the console API, in process on an ephemeral port. It presents the server certificate of [TestPki]
 * and answers every request with a successful command. Nothing here waits by time: each call returns when its response has
 * been read.
 */
private class StubHttpsIngot(pki: TestPki) : AutoCloseable {

    val requests = mutableListOf<String>()
    private val server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    init {
        val store = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(pki.serverKeystore).use { load(it, PASSWORD.toCharArray()) }
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(store, PASSWORD.toCharArray())
        }.keyManagers
        val sslContext = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        server.httpsConfigurator = HttpsConfigurator(sslContext)
        server.createContext("/") { exchange ->
            synchronized(requests) { requests.add(exchange.requestURI.path) }
            val bytes = """{"status":"SUCCEEDED","exitCode":0,"output":[{"stream":"stdout","text":"Ingot status"}],"data":null,"node":"node-1"}"""
                .toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    val url: String
        get() = "https://127.0.0.1:${server.address.port}"

    override fun close() {
        server.stop(0)
    }

}

private class TlsRun(val exit: Int, val out: String, val err: String)

private fun runTls(
    url: String,
    config: Path,
    extraArgs: List<String> = emptyList(),
    extraEnv: Map<String, String> = emptyMap(),
): TlsRun {
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()
    val env = buildMap {
        put("INGOT_CONFIG", config.toString())
        put("INGOT_TOKEN", "ci:ci-secret")
        putAll(extraEnv)
    }
    val exit = runCli(
        args = listOf("--url", url) + extraArgs + listOf("exec", "status"),
        env = env,
        stdin = ByteArrayInputStream(ByteArray(0)),
        out = PrintStream(out, true, StandardCharsets.UTF_8),
        err = PrintStream(err, true, StandardCharsets.UTF_8),
    )
    return TlsRun(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
}

internal class TlsTest {

    @TempDir
    lateinit var dir: Path

    private fun config(): Path = dir.resolve("absent.yaml")

    @Test
    fun `should connect to a server whose certificate the custom CA signed`() {
        // given: a server certificate signed by a CA that only the CLI is told about
        val pki = TestPki(dir)
        StubHttpsIngot(pki).use { stub ->
            // when: the CLI runs with the CA file and without --insecure
            val result = runTls(stub.url, config(), extraEnv = mapOf("INGOT_CA_CERT" to pki.caCertificate.toString()))

            // then: the command succeeds and no TLS warning is printed
            assertThat(result.exit).describedAs("exit code, stderr: ${result.err}").isEqualTo(0)
            assertThat(result.out).contains("Ingot status")
            assertThat(result.err).doesNotContain("WARNING")
        }
    }

    @Test
    fun `should refuse a server certificate that no trusted CA signed`() {
        // given: the same server certificate, and no CA file and no --insecure
        val pki = TestPki(dir)
        StubHttpsIngot(pki).use { stub ->
            // when: the CLI runs without trusting the CA
            val result = runTls(stub.url, config())

            // then: the connection fails as unreachable, the command never reaches the server and nothing is printed to stdout
            assertThat(result.exit).isEqualTo(5)
            assertThat(result.err).contains("The server could not be reached")
            assertThat(result.out).isEmpty()
            assertThat(stub.requests).isEmpty()
        }
    }

    @Test
    fun `should connect without verification and warn on stderr with the insecure flag`() {
        // given: a server certificate that no trusted CA signed
        val pki = TestPki(dir)
        StubHttpsIngot(pki).use { stub ->
            // when: the CLI runs with --insecure
            val result = runTls(stub.url, config(), extraArgs = listOf("--insecure"))

            // then: the command succeeds and the warning is on stderr, not on stdout
            assertThat(result.exit).describedAs("exit code, stderr: ${result.err}").isEqualTo(0)
            assertThat(result.out).contains("Ingot status")
            assertThat(result.err).contains("WARNING: TLS certificate verification is disabled (--insecure)")
            assertThat(result.out).doesNotContain("WARNING")
        }
    }

    @Test
    fun `should warn on every run when the insecure variable disables verification`() {
        // given: a server certificate that no trusted CA signed
        val pki = TestPki(dir)
        StubHttpsIngot(pki).use { stub ->
            // when: the CLI runs with INGOT_INSECURE set and no flag
            val result = runTls(stub.url, config(), extraEnv = mapOf("INGOT_INSECURE" to "true"))

            // then: the warning is printed on stderr
            assertThat(result.exit).describedAs("exit code, stderr: ${result.err}").isEqualTo(0)
            assertThat(result.err).contains("WARNING: TLS certificate verification is disabled (--insecure)")
        }
    }

    @Test
    fun `should refuse insecure TLS in the config file before contacting the server`() {
        // given: a config file that switches verification off at the top level
        val pki = TestPki(dir)
        val file = dir.resolve("config.yaml")
        Files.writeString(file, "current: local\ninsecure: true\ncontexts:\n  - name: local\n    url: https://127.0.0.1:1\n")
        StubHttpsIngot(pki).use { stub ->
            // when: the CLI runs with that config file
            val result = runTls(stub.url, file)

            // then: the file is refused with a usage error and no request reaches the server
            assertThat(result.exit).isEqualTo(2)
            assertThat(result.err).contains("must not hold a token or insecure TLS")
            assertThat(stub.requests).isEmpty()
        }
    }

}
