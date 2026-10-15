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

package com.reposilite.token

import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.api.UpdateAccessTokenRequest
import com.reposilite.token.specification.AccessTokenSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

internal class AccessTokenEntityTagTest : AccessTokenSpecification() {

    private fun createStoredToken(name: String): AccessTokenDto =
        accessTokenFacade.createAccessToken(CreateAccessTokenRequest(TEMPORARY, name, secret = "secret")).accessToken

    @Test
    fun `should expose an entity tag together with the token details`() {
        // given: an existing token
        createStoredToken("reposilite")

        // when: the token is read
        val snapshot = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!

        // then: the details come with a strong entity tag
        assertThat(snapshot.value.name).isEqualTo("reposilite")
        assertThat(snapshot.etag).matches("\"[0-9a-f]{64}\"")
    }

    @Test
    fun `should keep the entity tag when the token is read again without changes`() {
        // given: an existing token
        createStoredToken("reposilite")

        // when: the token is read twice
        val first = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag
        val second = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag

        // then: the entity tag is stable
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `should update and return a new entity tag when if-match is current`() {
        // given: the current entity tag of a token
        createStoredToken("reposilite")
        val current = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag

        // when: a metadata update carries that entity tag
        val result = accessTokenFacade.updateAccessToken("reposilite", UpdateAccessTokenRequest(description = "updated"), ifMatch = current)

        // then: the update is applied and the returned tag belongs to the stored token
        assertOk(result)
        assertThat(result.get().value.description).isEqualTo("updated")
        assertThat(result.get().etag)
            .isNotEqualTo(current)
            .isEqualTo(accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag)
    }

    @Test
    fun `should reject an update with a stale entity tag and keep the stored token`() {
        // given: a tag read before the token was changed by someone else
        createStoredToken("reposilite")
        val stale = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag
        assertOk(accessTokenFacade.updateAccessToken("reposilite", UpdateAccessTokenRequest(description = "other-admin")))

        // when: an update with the stale tag arrives
        val result = accessTokenFacade.updateAccessToken("reposilite", UpdateAccessTokenRequest(description = "overwrite"), ifMatch = stale)

        // then: it is rejected with 412 and names the token
        assertThat(result.isErr).isTrue
        assertThat(result.error.status).isEqualTo(412)
        assertThat(result.error.message).contains("'reposilite'")

        // then: the other admin's change is kept
        assertThat(accessTokenFacade.getAccessToken("reposilite")!!.description).isEqualTo("other-admin")
    }

    @Test
    fun `should change the entity tag when the secret is regenerated`() {
        // given: a token and its tag before the secret is regenerated
        val token = createStoredToken("reposilite")
        val beforeRegeneration = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag
        accessTokenFacade.regenerateAccessToken(token, secret = null)

        // when: an update still carries the tag from before the regeneration
        val result = accessTokenFacade.updateAccessToken("reposilite", UpdateAccessTokenRequest(description = "late"), ifMatch = beforeRegeneration)

        // then: the stale update is rejected
        assertThat(result.error.status).isEqualTo(412)
    }

    @Test
    fun `should update without precondition when no if-match is sent`() {
        // given: an existing token
        createStoredToken("reposilite")

        // when: an update is sent without if-match
        val result = accessTokenFacade.updateAccessToken("reposilite", UpdateAccessTokenRequest(description = "unconditional"))

        // then: it is applied as it always was
        assertOk(result)
        assertThat(accessTokenFacade.getAccessToken("reposilite")!!.description).isEqualTo("unconditional")
    }

    @Test
    fun `should report not found rather than precondition failure for a missing token`() {
        // when: an update of an unknown token carries an if-match
        val result = accessTokenFacade.updateAccessToken("ghost", UpdateAccessTokenRequest(description = "x"), ifMatch = "*")

        // then: the token is reported missing
        assertThat(result.error.status).isEqualTo(404)
    }

    @Test
    fun `should reject an upsert with if-match for a token that does not exist`() {
        // when: a token is created while the client expected an existing one
        val result = accessTokenFacade.upsertAccessToken(
            CreateAccessTokenRequest(TEMPORARY, "reposilite", secret = "secret"),
            ifMatch = "\"expected\"",
        )

        // then: the precondition fails and no token is created
        assertThat(result.error.status).isEqualTo(412)
        assertThat(accessTokenFacade.getAccessToken("reposilite")).isNull()
    }

    @Test
    fun `should upsert with a current entity tag and return the new one`() {
        // given: an existing token and its current tag
        createStoredToken("reposilite")
        val current = accessTokenFacade.getAccessTokenSnapshot("reposilite")!!.etag

        // when: the token is upserted with that tag
        val result = accessTokenFacade.upsertAccessToken(
            CreateAccessTokenRequest(TEMPORARY, "reposilite", secret = "rotated"),
            ifMatch = current,
        )

        // then: the write is applied and the tag belongs to the stored token
        assertOk(result)
        assertThat(result.get().etag).isNotEqualTo(current)
    }

}
