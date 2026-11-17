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

package com.reposilite.configuration

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.configuration.specification.SettingsIntegrationSpecification
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.InMemoryLogger
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * The export passphrase is used to seal and open the settings file and nothing else. Neither the passphrase, nor the wrong one
 * a caller sends, nor the ciphertext may reach the log lines, the audit events or an error body. Each test boots its own instance
 * with its own captured log, and reads the audit events from the store the instance writes to.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SettingsExportPassphraseIntegrationTest : SettingsIntegrationSpecification() {

    private val passphrase = "passphrase-under-test-42"
    private val wrongPassphrase = "not-the-passphrase-42"
    private val journal = InMemoryLogger()

    override fun logger(): Logger = journal

    private fun export(password: String) =
        Unirest.post("$base/api/settings/export")
            .basicAuth(MANAGER, MANAGER_SECRET)
            .header("Content-Type", "application/json")
            .body(DEFAULT_OBJECT_MAPPER.createObjectNode().put("password", password).toString())
            .asString()

    private fun open(password: String, file: JsonNode) =
        Unirest.post("$base/api/settings/import/open")
            .basicAuth(MANAGER, MANAGER_SECRET)
            .header("Content-Type", "application/json")
            .body(DEFAULT_OBJECT_MAPPER.createObjectNode().put("password", password).set<JsonNode>("export", file).toString())
            .asString()

    private fun auditEvents(action: AuditAction): Pair<String, AuditPageDto> {
        val response = Unirest.get("$base/api/audit?action=$action")
            .basicAuth(MANAGER, MANAGER_SECRET)
            .asString()
        return response.body to DEFAULT_OBJECT_MAPPER.readValue(response.body, AuditPageDto::class.java)
    }

    // The captured lines of the instance. A log that holds nothing would make every "no passphrase" check pass, so the
    // lines are asserted to exist before they are read.
    private fun loggedLines(): String {
        assertThat(journal.messages).describedAs("captured log lines").isNotEmpty()
        return journal.messages.joinToString("\n") { it.value }
    }

    private fun exportedFile(): JsonNode {
        val exported = export(passphrase)
        assertSuccessResponse(OK, exported)
        return DEFAULT_OBJECT_MAPPER.readTree(exported.body)
    }

    @Test
    fun `an export never writes the passphrase or the ciphertext to the log`() {
        // given: an export made with the passphrase
        val exported = export(passphrase)
        assertSuccessResponse(OK, exported)
        val ciphertext = DEFAULT_OBJECT_MAPPER.readTree(exported.body).get("ciphertext").asText()

        // then: no log line holds the passphrase or the ciphertext
        assertThat(loggedLines()).describedAs("log lines").doesNotContain(passphrase).doesNotContain(ciphertext)
    }

    @Test
    fun `an export never writes the passphrase to an audit event`() {
        // given: an export made with the passphrase
        assertSuccessResponse(OK, export(passphrase))

        // when: the export event is read back from the audit store
        val (raw, page) = auditEvents(AuditAction.SETTINGS_EXPORTED)

        // then: the event exists, and the raw page does not hold the passphrase
        assertThat(page.events).describedAs("export events").hasSize(1)
        assertThat(raw).describedAs("audit page of the export").doesNotContain(passphrase)
    }

    @Test
    fun `a refused import never writes the passphrase or the wrong one to the error body`() {
        // given: an export made with the passphrase
        val file = exportedFile()

        // when: the file is opened with a wrong passphrase
        val refused = open(wrongPassphrase, file)

        // then
        assertThat(refused.status).describedAs("status of the refused import").isEqualTo(BAD_REQUEST.code)
        assertThat(refused.body).describedAs("error body").doesNotContain(passphrase).doesNotContain(wrongPassphrase)
    }

    @Test
    fun `a refused import never writes the passphrase or the wrong one to the log`() {
        // given: an export made with the passphrase
        val file = exportedFile()

        // when: the file is opened with a wrong passphrase
        open(wrongPassphrase, file)

        // then
        assertThat(loggedLines()).describedAs("log lines").doesNotContain(passphrase).doesNotContain(wrongPassphrase)
    }

    @Test
    fun `a refused import writes a refusal event without the passphrase or the wrong one`() {
        // given: an export made with the passphrase
        val file = exportedFile()

        // when: the file is opened with a wrong passphrase
        open(wrongPassphrase, file)

        // then: the refusal is audited, and the audit page holds neither passphrase
        val (raw, page) = auditEvents(AuditAction.SETTINGS_IMPORT_REFUSED)
        assertThat(page.events).describedAs("refusal events").hasSize(1)
        assertThat(raw).describedAs("audit page of the refusal").doesNotContain(passphrase).doesNotContain(wrongPassphrase)
    }

    @Test
    fun `a successful import never writes the passphrase to the log or to an audit event`() {
        // given: an export made with the passphrase
        val file = exportedFile()

        // when: the file is opened with the passphrase
        assertSuccessResponse(OK, open(passphrase, file))

        // then: the decryption is audited, and neither the log nor the audit page holds the passphrase
        val (raw, page) = auditEvents(AuditAction.SETTINGS_IMPORT_DECRYPTED)
        assertThat(page.events).describedAs("decryption events").hasSize(1)
        assertThat(loggedLines()).describedAs("log lines").doesNotContain(passphrase)
        assertThat(raw).describedAs("audit page of the decryption").doesNotContain(passphrase)
    }

    @Test
    fun `an export without a password answers 400`() {
        // when: the export is requested with a body that names no password
        val response = Unirest.post("$base/api/settings/export")
            .basicAuth(MANAGER, MANAGER_SECRET)
            .header("Content-Type", "application/json")
            .body("{}")
            .asString()

        // then
        assertThat(response.status).describedAs("status without a password").isEqualTo(BAD_REQUEST.code)
    }

    @Test
    fun `a successful export carries no-store so that no cache keeps the file`() {
        // when
        val exported = export(passphrase)

        // then
        assertSuccessResponse(OK, exported)
        assertThat(exported.headers.getFirst("Cache-Control")).describedAs("Cache-Control of the export").isEqualTo("no-store")
    }

    @Test
    fun `an export with an 11 character passphrase is refused and writes no export event`() {
        // when: the passphrase is one character short of the minimum
        val response = export("eleven-char")

        // then: the request is refused, and no export event is recorded
        assertThat(response.status).describedAs("status of the short passphrase").isEqualTo(BAD_REQUEST.code)
        val (_, page) = auditEvents(AuditAction.SETTINGS_EXPORTED)
        assertThat(page.events).describedAs("export events").isEmpty()
    }

    private companion object {
        const val MANAGER = "manager"
        const val MANAGER_SECRET = "manager-secret"
    }

}
