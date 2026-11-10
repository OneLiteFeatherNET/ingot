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

@file:Suppress("FunctionName")

package net.onelitefeather.ingot.backup

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditPageDto
import com.reposilite.shared.ErrorResponse
import io.javalin.http.HttpStatus.FORBIDDEN
import io.javalin.http.HttpStatus.OK
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.sql.DriverManager

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class DatabaseSnapshotIntegrationTest : ReposiliteSpecification() {

    @Test
    fun `should refuse the snapshot endpoint for a principal without the maintenance area`() {
        // given: a token without the maintenance area
        val (token, secret) = useAuth("unauthorized-token", "secret")

        // when: the snapshot is requested
        val response = Unirest.post("$base/api/database/snapshots").basicAuth(token, secret).asJacksonObject(ErrorResponse::class)

        // then: the request is refused and no backups directory is created
        assertErrorResponse(FORBIDDEN, response)
        assertThat(Files.exists(reposilite.parameters.workingDirectory.resolve(BACKUP_DIRECTORY))).`as`("no snapshot is written").isFalse
    }

    @Test
    fun `should write a snapshot that reads back and answer with its name and size but no path`() {
        // given: a manager token of the running instance
        val (token, secret) = useDefaultManagementToken()

        // when: the snapshot is requested while the instance runs
        val response = Unirest.post("$base/api/database/snapshots").basicAuth(token, secret).asString()

        // then: the answer names one snapshot file and carries no path
        assertThat(response.status).`as`("status of the snapshot request").isEqualTo(OK.code)
        val body = response.body
        assertThat(body).`as`("answer of the snapshot request").contains("\"fileName\":\"ingot-snapshot-")
        assertThat(body).`as`("answer carries no absolute path").doesNotContain(reposilite.parameters.workingDirectory.toAbsolutePath().toString())
        assertThat(body).`as`("answer carries no path separator").doesNotContain("/")

        // and: the file exists in the backups directory and opens as a SQLite database that passes the integrity check
        val snapshots = Files.list(reposilite.parameters.workingDirectory.resolve(BACKUP_DIRECTORY)).use { files -> files.toList() }
        assertThat(snapshots).`as`("snapshot files in backups").hasSize(1)
        val integrity = DriverManager.getConnection("jdbc:sqlite:${snapshots.single()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
        }
        assertThat(integrity).`as`("integrity check of the snapshot").isEqualTo("ok")
    }

    @Test
    fun `should audit the snapshot with the principal and the file name`() {
        // given: a manager token of the running instance
        val (token, secret) = useDefaultManagementToken()

        // when: the snapshot is requested
        val created = Unirest.post("$base/api/database/snapshots").basicAuth(token, secret).asString()
        assertThat(created.status).`as`("status of the snapshot request").isEqualTo(OK.code)

        // then: the audit log holds one snapshot event naming the manager and the written file
        val page = Unirest.get("$base/api/audit?action=${AuditAction.DATABASE_SNAPSHOT_CREATED}")
            .basicAuth(token, secret)
            .asJacksonObject(AuditPageDto::class)
            .body
        assertThat(page.events).`as`("snapshot events in the audit log").hasSize(1)
        val event = page.events.single()
        assertThat(event.actor).`as`("actor of the snapshot event").isEqualTo("manager")
        assertThat(event.details.toString()).`as`("details name the snapshot file").contains("ingot-snapshot-")
    }

}
