/*
 * Copyright (c) 2026 OneLiteFeather
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

package com.reposilite.image

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Extra certificate authorities, mounted as a directory of PEM files and imported into a copy of the JDK trust store at start.
 *
 * The import is observed in the running container: the copy under the temporary directory is listed with `keytool`, and the
 * JVM command line is read from `/proc`. The certificates are generated on the host with the JDK's `keytool`, so nothing is
 * downloaded and every test owns its own material in its own temporary directory.
 */
class ExtraCaBundleContainerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    @DisplayName("a private CA in the mounted directory is imported into the trust store copy the server runs with")
    fun `a private CA in the bundle is imported into the trust store copy`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        startWithBundle(bundle).use { server ->
            assertThat(server.keytoolAliases(server.trustStoreCopy()))
                .describedAs("aliases of the trust store copy")
                .contains("ingot-extra-private-ca.pem-1")
        }
    }

    @Test
    @DisplayName("the trust store copy keeps every public authority of the JDK store and adds only the bundle")
    fun `the copy keeps the public authorities of the JDK store`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        startWithBundle(bundle).use { server ->
            val jdkEntries = server.keytoolAliases("\$JAVA_HOME/lib/security/cacerts")
            val copyEntries = server.keytoolAliases(server.trustStoreCopy())

            assertThat(jdkEntries).describedAs("public authorities in the JDK store").isNotEmpty()
            assertThat(copyEntries)
                .describedAs("entries of the copy, which must be the JDK store plus the one private CA")
                .hasSize(jdkEntries.size + 1)
        }
    }

    @Test
    @DisplayName("the JVM is started with the trust store copy and not with the mounted directory")
    fun `the JVM trust store option points at the copy`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        startWithBundle(bundle).use { server ->
            val copy = server.trustStoreCopy()
            val serverCommand = server.serverCommandLine()

            assertThat(serverCommand)
                .describedAs("server command line, which must name the copy as trust store")
                .contains("-Djavax.net.ssl.trustStore=$copy")
                .doesNotContain("-Djavax.net.ssl.trustStore=$BUNDLE_PATH")
        }
    }

    @Test
    @DisplayName("every certificate of a multi-certificate PEM file is imported")
    fun `a second certificate in the same PEM file is imported too`() {
        val chain = caPem("ingot-test-root-ca") + caPem("ingot-test-intermediate-ca")
        val bundle = bundleOf("chain.pem" to chain)

        startWithBundle(bundle).use { server ->
            assertThat(server.keytoolAliases(server.trustStoreCopy()))
                .describedAs("aliases of the trust store copy")
                .contains("ingot-extra-chain.pem-1", "ingot-extra-chain.pem-2")
        }
    }

    @Test
    @DisplayName("a Kubernetes volume layout imports the linked certificate once and ignores the dot entries")
    fun `a ConfigMap style directory imports each certificate once`() {
        val bundle = tempDir.resolve("k8s-bundle")
        val data = Files.createDirectories(bundle.resolve("..data"))
        Files.writeString(data.resolve("ca.pem"), caPem("ingot-test-private-ca"))
        Files.createSymbolicLink(bundle.resolve("ca.pem"), Path.of("..data", "ca.pem"))
        makeReadable(bundle)

        startWithBundle(bundle).use { server ->
            assertThat(server.keytoolAliases(server.trustStoreCopy()).filter { it.startsWith("ingot-extra-") })
                .describedAs("imported aliases, a dot entry or a second link must not add any")
                .containsExactly("ingot-extra-ca.pem-1")
        }
    }

    @Test
    @DisplayName("REPOSILITE_EXTRA_CA_CERTS names the bundle when INGOT_EXTRA_CA_CERTS is not set")
    fun `the REPOSILITE fallback variable imports the bundle`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        serverContainer(ImageUnderTest.server, environment = mapOf(LEGACY_VARIABLE to BUNDLE_PATH))
            .withFileSystemBind(bundle.toString(), BUNDLE_PATH, BindMode.READ_ONLY)
            .startOrExplain()
            .use { server ->
                assertThat(server.keytoolAliases(server.trustStoreCopy()))
                    .describedAs("aliases of the trust store copy")
                    .contains("ingot-extra-private-ca.pem-1")
            }
    }

    @Test
    @DisplayName("INGOT_EXTRA_CA_CERTS wins over REPOSILITE_EXTRA_CA_CERTS when both name a bundle")
    fun `the INGOT variable takes precedence over the REPOSILITE fallback`() {
        val ingotBundle = bundleOf("ingot-ca.pem" to caPem("ingot-test-ingot-ca"))
        val legacyBundle = bundleOf("legacy-ca.pem" to caPem("ingot-test-legacy-ca"))

        serverContainer(
            ImageUnderTest.server,
            environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH, LEGACY_VARIABLE to "/etc/legacy-extra-ca"),
        )
            .withFileSystemBind(ingotBundle.toString(), BUNDLE_PATH, BindMode.READ_ONLY)
            .withFileSystemBind(legacyBundle.toString(), "/etc/legacy-extra-ca", BindMode.READ_ONLY)
            .startOrExplain()
            .use { server ->
                assertThat(server.keytoolAliases(server.trustStoreCopy()))
                    .describedAs("aliases of the trust store copy")
                    .contains("ingot-extra-ingot-ca.pem-1")
                    .doesNotContain("ingot-extra-legacy-ca.pem-1")
            }
    }

    @Test
    @DisplayName("the trust store copy is readable by the service account after the privilege drop to PUID")
    fun `the service account reads the copy after the root start drops privileges`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        startWithBundle(bundle, environment = mapOf("PUID" to "1234", "PGID" to "1234")).use { server ->
            assertThat(server.exec("id", "-u", "reposilite").trim())
                .describedAs("uid of the service account after PUID is applied")
                .isEqualTo("1234")
            server.exec("runuser", "-u", "reposilite", "--", "cat", server.trustStoreCopy())
        }
    }

    @Test
    @DisplayName("a non-root start with a read-only root filesystem still imports the bundle")
    fun `a non-root start with a read-only root filesystem imports the bundle`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        serverContainer(ImageUnderTest.server, environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH), runAs = "977:977")
            .withFileSystemBind(bundle.toString(), BUNDLE_PATH, BindMode.READ_ONLY)
            // Writable /tmp that allows exec, as the chart's emptyDir gives: SQLite loads its native library from there.
            // The log directory belongs to the service account, as a volume the operator prepares would.
            .withTmpFs(mapOf("/tmp" to "rw,exec,mode=1777", "/var/log/reposilite" to "rw,uid=977,gid=977"))
            .withCreateContainerCmdModifier { it.hostConfig!!.withReadonlyRootfs(true) }
            .startOrExplain()
            .use { server ->
                assertThat(server.keytoolAliases(server.trustStoreCopy()))
                    .describedAs("aliases of the trust store copy")
                    .contains("ingot-extra-private-ca.pem-1")
            }
    }

    @Test
    @DisplayName("an unset variable leaves the JVM command line without a trust store option and creates no copy")
    fun `an unset variable adds no trust store option`() {
        serverContainer(ImageUnderTest.server).startOrExplain().use { server ->
            assertThat(server.serverCommandLine())
                .describedAs("server command line without the variable")
                .doesNotContain("javax.net.ssl.trustStore")
            assertThat(server.exec("sh", "-c", "find /tmp -mindepth 2 -maxdepth 2 -name cacerts").trim())
                .describedAs("trust store copies under /tmp without the variable")
                .isEmpty()
        }
    }

    @Test
    @DisplayName("the start logs the count and the file names of the bundle, never the certificate contents")
    fun `the start log names the files and omits the certificate contents`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        startWithBundle(bundle).use { server ->
            val logs = server.logs
            assertThat(logs).describedAs("start log").contains("private-ca.pem")
            assertThat(logs).describedAs("start log must not carry certificate contents").doesNotContain("BEGIN CERTIFICATE")
        }
    }

    @Test
    @DisplayName("a missing directory named by INGOT_EXTRA_CA_CERTS stops the start with exit code 1 and one line naming the variable")
    fun `a missing bundle directory stops the start`() {
        val refusal = refusedStart(environment = mapOf(INGOT_VARIABLE to "/etc/ingot/does-not-exist"))

        assertRefusedNaming(refusal, INGOT_VARIABLE)
    }

    @Test
    @DisplayName("an empty directory named by INGOT_EXTRA_CA_CERTS stops the start with exit code 1 and one line naming the variable")
    fun `an empty bundle directory stops the start`() {
        val refusal = refusedStart(
            environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH),
            mount = makeReadable(tempDir.resolve("empty-bundle").also { Files.createDirectories(it) }),
        )

        assertRefusedNaming(refusal, INGOT_VARIABLE)
    }

    @Test
    @DisplayName("a file without a certificate block stops the start with exit code 1 and one line naming the variable and the file")
    fun `a file that is not a certificate stops the start`() {
        val bundle = bundleOf("notes.txt" to "this is not a certificate\n")

        val refusal = refusedStart(environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH), mount = bundle)

        assertRefusedNaming(refusal, INGOT_VARIABLE, "notes.txt")
    }

    @Test
    @DisplayName("a trust store already set in JAVA_OPTS stops the start with exit code 1 and one line naming the variable")
    fun `a conflicting trust store in JAVA_OPTS stops the start`() {
        val bundle = bundleOf("private-ca.pem" to caPem("ingot-test-private-ca"))

        val refusal = refusedStart(
            environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH, "JAVA_OPTS" to "-Djavax.net.ssl.trustStore=/x"),
            mount = bundle,
        )

        assertRefusedNaming(refusal, INGOT_VARIABLE)
    }

    // --- helpers -----------------------------------------------------------------------------------------------------

    /** Writes the given PEM contents into a fresh bundle directory and makes it readable by any uid, as a ConfigMap is. */
    private fun bundleOf(vararg files: Pair<String, String>): Path {
        val directory = Files.createDirectories(tempDir.resolve("bundle-${UUID.randomUUID()}"))
        files.forEach { (name, content) -> Files.writeString(directory.resolve(name), content) }
        return makeReadable(directory)
    }

    private fun makeReadable(directory: Path): Path {
        Files.walk(directory).use { paths ->
            paths.filter { !Files.isSymbolicLink(it) }.forEach { path ->
                val mode = if (Files.isDirectory(path)) "rwxr-xr-x" else "rw-r--r--"
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(mode))
            }
        }
        return directory
    }

    /** Starts the server with the bundle mounted read-only at [BUNDLE_PATH] and waits until it answers. */
    private fun startWithBundle(
        bundle: Path,
        environment: Map<String, String> = emptyMap(),
    ): GenericContainer<*> =
        serverContainer(ImageUnderTest.server, environment = mapOf(INGOT_VARIABLE to BUNDLE_PATH) + environment)
            .withFileSystemBind(bundle.toString(), BUNDLE_PATH, BindMode.READ_ONLY)
            .startOrExplain()

    /** The `docker run` outcome of a start that is expected to be refused. */
    private class Refusal(val finished: Boolean, val exitCode: Int, val output: String)

    /**
     * The container is run with the docker CLI, as in [FileValuesContainerTest]: a refused start never reaches a wait
     * strategy. Output goes to a file so a server that does start cannot block on a full pipe; it is killed at the timeout.
     */
    private fun refusedStart(environment: Map<String, String>, mount: Path? = null): Refusal {
        val name = "ingot-ca-${UUID.randomUUID()}"
        val output = tempDir.resolve("$name.log").toFile()
        val command = mutableListOf("docker", "run", "--rm", "--name", name)
        environment.forEach { (key, value) -> command += listOf("-e", "$key=$value") }
        mount?.let { command += listOf("-v", "$it:$BUNDLE_PATH:ro") }
        command += ImageUnderTest.server.toString()

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(output)
            .start()
        val finished = process.waitFor(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        ProcessBuilder("docker", "rm", "-f", name).start().waitFor(1, TimeUnit.MINUTES)

        return Refusal(
            finished = finished,
            exitCode = if (finished) process.exitValue() else -1,
            output = output.readText(),
        )
    }

    private fun assertRefusedNaming(refusal: Refusal, vararg names: String) {
        assertThat(refusal.finished).describedAs("the refused start ended within %d seconds", REFUSAL_TIMEOUT_SECONDS).isTrue()
        assertThat(refusal.exitCode).describedAs("exit code of the refused start, output was:\n%s", refusal.output).isEqualTo(1)
        assertThat(refusal.output.lines().filter { line -> names.all { it in line } })
            .describedAs("one line naming %s, output was:\n%s", names.joinToString(), refusal.output)
            .hasSize(1)
        assertThat(refusal.output).describedAs("output must not carry a stack trace").doesNotContain("Exception in thread")
    }

    /** A certificate authority generated with the host JDK's keytool, returned as PEM. */
    private fun caPem(alias: String): String {
        val keystore = tempDir.resolve("$alias.p12")
        val pem = tempDir.resolve("$alias.pem")
        keytool(
            "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=$alias",
            "-ext", "bc:critical=ca:true", "-validity", "3650",
            "-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD,
        )
        keytool(
            "-exportcert", "-rfc", "-alias", alias,
            "-keystore", keystore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD,
            "-file", pem.toString(),
        )
        return Files.readString(pem)
    }

    private fun keytool(vararg arguments: String) {
        val process = ProcessBuilder(listOf(KEYTOOL) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertThat(process.waitFor(1, TimeUnit.MINUTES)).describedAs("keytool finished").isTrue()
        assertThat(process.exitValue()).describedAs("exit code of keytool, output was:\n%s", output).isEqualTo(0)
    }

    /** Runs a command in the running container and returns its standard output. */
    private fun GenericContainer<*>.exec(vararg command: String): String {
        val result = execInContainer(*command)
        assertThat(result.exitCode)
            .describedAs("exit code of %s, stderr was:\n%s", command.joinToString(" "), result.stderr)
            .isEqualTo(0)
        return result.stdout
    }

    /** The one copy of the trust store the entrypoint made for this start, found by its file name. */
    private fun GenericContainer<*>.trustStoreCopy(): String {
        val found = exec("sh", "-c", "find /tmp -mindepth 2 -maxdepth 2 -name cacerts").trim()
        assertThat(found).describedAs("trust store copy under /tmp").isNotEmpty()
        return found
    }

    /** The aliases of a keystore as `keytool -list` prints them, one per trusted entry. */
    private fun GenericContainer<*>.keytoolAliases(keystore: String): List<String> =
        exec("sh", "-c", "keytool -list -keystore $keystore -storepass $PASSWORD")
            .lines()
            .filter { "trustedCertEntry" in it }
            .map { it.substringBefore(',').trim() }

    /** The command line of the java process that runs the server, with arguments separated by spaces. */
    private fun GenericContainer<*>.serverCommandLine(): String =
        exec("sh", "-c", "for p in /proc/[0-9]*/cmdline; do tr '\\0' ' ' < \"\$p\"; echo; done")
            .lines()
            .firstOrNull { "-jar ingot.jar" in it }
            ?: error("no java process running ingot.jar was found")

    private companion object {
        const val INGOT_VARIABLE = "INGOT_EXTRA_CA_CERTS"
        const val LEGACY_VARIABLE = "REPOSILITE_EXTRA_CA_CERTS"
        const val BUNDLE_PATH = "/etc/ingot/extra-ca"
        const val PASSWORD = "changeit"
        const val REFUSAL_TIMEOUT_SECONDS = 90L
        val KEYTOOL: String = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
    }

}
