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

package net.onelitefeather.ingot.cluster

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.NKey
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64

/**
 * A throwaway NATS operator, account and user, generated at test time. Nothing here is a secret outside the test run:
 * the keys live in memory and the credentials file is written into a test temporary directory.
 *
 * The signed JWTs follow the NATS decentralised auth layout: the operator signs the account, the account signs the user,
 * and the broker preloads the account so that it can verify the user without a resolver round trip.
 */
internal class NatsOperatorFixture(clock: Clock) {

    private val mapper = ObjectMapper()
    private val random = SecureRandom()
    private val issuedAt = clock.instant().epochSecond

    private val operator = NKey.createOperator(random)
    private val account = NKey.createAccount(random)
    private val user = NKey.createUser(random)

    private val operatorPublic = String(operator.publicKey)
    private val accountPublic = String(account.publicKey)
    private val userPublic = String(user.publicKey)

    private val operatorJwt = signed(
        issuer = operator,
        subject = operatorPublic,
        issuerPublic = operatorPublic,
        nats = mapOf("type" to "operator", "version" to 2),
    )

    private val accountJwt = signed(
        issuer = operator,
        subject = accountPublic,
        issuerPublic = operatorPublic,
        nats = mapOf(
            "type" to "account",
            "version" to 2,
            "limits" to mapOf("subs" to -1, "data" to -1, "payload" to -1, "imports" to -1, "exports" to -1, "conn" to -1, "leaf" to -1),
        ),
    )

    private val userJwt = signed(
        issuer = account,
        subject = userPublic,
        issuerPublic = accountPublic,
        nats = mapOf("type" to "user", "version" to 2, "pub" to emptyMap<String, Any>(), "sub" to emptyMap<String, Any>()),
    )

    /** The server configuration of an operator mode broker: the operator and the preloaded account, no users. */
    fun serverConfiguration(): ByteArray =
        """
        |port: 4222
        |operator: $operatorJwt
        |resolver: MEMORY
        |resolver_preload: {
        |  $accountPublic: $accountJwt
        |}
        |""".trimMargin().toByteArray(Charsets.US_ASCII)

    /** The `.creds` file of the user, in the layout `nats` tooling writes: the user JWT and the user seed. */
    fun credentialsFile(): String =
        """
        |-----BEGIN NATS USER JWT-----
        |$userJwt
        |------END NATS USER JWT------
        |
        |************************* IMPORTANT *************************
        |NKEY Seed printed below can be used to sign and prove identity.
        |NKEYs are sensitive and should be treated as secrets.
        |
        |-----BEGIN USER NKEY SEED-----
        |${String(user.seed)}
        |------END USER NKEY SEED------
        |
        |*************************************************************
        |""".trimMargin()

    private fun signed(issuer: NKey, subject: String, issuerPublic: String, nats: Map<String, Any>): String {
        val header = encode(mapper.writeValueAsBytes(mapOf("typ" to "JWT", "alg" to "ed25519-nkey")))
        val claims = encode(
            mapper.writeValueAsBytes(
                mapOf("iat" to issuedAt, "iss" to issuerPublic, "sub" to subject, "name" to "ingot-test", "nats" to nats)
            )
        )
        val signingInput = "$header.$claims"
        return "$signingInput.${encode(issuer.sign(signingInput.toByteArray(Charsets.US_ASCII)))}"
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
