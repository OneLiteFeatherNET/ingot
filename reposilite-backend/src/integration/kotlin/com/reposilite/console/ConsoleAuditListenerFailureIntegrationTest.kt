@file:Suppress("FunctionName")

package com.reposilite.console

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditEvent
import com.reposilite.token.AccessTokenFacade
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/**
 * An audit listener that throws must not change what a console command answers, on any channel. The listener is
 * registered by each test on its own instance, and the reach counter shows that the command really emitted an event.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConsoleAuditListenerFailureIntegrationTest : ReposiliteSpecification() {

    private val json = ObjectMapper()

    private val reached = AtomicInteger()

    private val sockets = mutableListOf<ConsoleSocket>()

    @AfterEach
    fun closeSockets() {
        sockets.forEach { it.close() }
    }

    private fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    /** Registered after the fixture is built, because creating a token emits an audit event as well. */
    private fun breakAuditListener() {
        reposilite.extensions.registerEvent<AuditEvent> {
            reached.incrementAndGet()
            throw IllegalStateException("broken audit listener")
        }
    }

    @Test
    fun `should answer a plain command with its usual body when an audit listener throws`() {
        // given: an instance admin and a broken audit listener
        val (name, secret) = useDefaultManagementToken()
        breakAuditListener()

        // when: a command runs in plain mode
        val response = Unirest.post("$base/api/console/execute")
            .header("Authorization", basic(name, secret))
            .body("level info")
            .asString()

        // then: the command answered as it always does, and the listener was reached
        assertThat(reached.get()).`as`("audit events emitted to the broken listener").isPositive()
        assertThat(response.status).isEqualTo(200)
        assertThat(json.readTree(response.body))
            .isEqualTo(json.readTree("""{"status":"SUCCEEDED","response":["The new logging level has been set to info"]}"""))
    }

    @Test
    fun `should answer a structured command with exit code 0 when an audit listener throws`() {
        // given: an instance admin and a broken audit listener
        val (name, secret) = useDefaultManagementToken()
        breakAuditListener()

        // when: a command runs in structured mode
        val response = Unirest.post("$base/api/console/execute?structured=true")
            .header("Authorization", basic(name, secret))
            .body("level info")
            .asString()

        // then: the command answered with exit code 0, and the listener was reached
        assertThat(reached.get()).`as`("audit events emitted to the broken listener").isPositive()
        assertThat(response.status).isEqualTo(200)
        val body = json.readTree(response.body)
        assertThat(body.get("status").asText()).isEqualTo("SUCCEEDED")
        assertThat(body.get("exitCode").asInt()).isEqualTo(0)
    }

    @Test
    fun `should answer a held dangerous command with 409 and keep the token when an audit listener throws`() {
        // given: an instance admin and a token that a structured revoke would remove
        val (name, secret) = useDefaultManagementToken()
        useAuth("listener-held", "held-secret")
        breakAuditListener()

        // when: the revoke runs in structured mode without confirmation
        val response = Unirest.post("$base/api/console/execute?structured=true")
            .header("Authorization", basic(name, secret))
            .body("token-revoke listener-held")
            .asString()

        // then: the 409 confirmation body is still the answer, nothing ran, and the listener was reached
        assertThat(reached.get()).`as`("audit events emitted to the broken listener").isPositive()
        assertThat(response.status).isEqualTo(409)
        assertThat(json.readTree(response.body).get("error").asText()).isEqualTo("confirmationRequired")
        assertThat(useFacade<AccessTokenFacade>().getAccessToken("listener-held"))
            .`as`("token after the held revoke")
            .isNotNull
    }

    @Test
    fun `should keep the WebSocket open and answer its command when an audit listener throws`() {
        // given: an instance admin connected to the console WebSocket with the legacy token, and a broken audit listener
        val (name, secret) = useDefaultManagementToken()
        breakAuditListener()
        val socket = ConsoleSocket(URI.create("${base.replace("http", "ws")}/api/console/sock")).also { sockets += it }
        socket.send("Authorization:$name:$secret")

        // when: a command runs, and a keep-alive follows it on the same connection
        socket.send("level info")
        socket.send("keep-alive")
        val received = socket.receiveUntil { it == "keep-alive" }

        // then: the keep-alive is answered, so the connection survived the command, and the listener was reached
        assertThat(reached.get()).`as`("audit events emitted to the broken listener").isPositive()
        assertThat(received.last()).`as`("keep-alive answered after the command").isEqualTo("keep-alive")
    }

}
