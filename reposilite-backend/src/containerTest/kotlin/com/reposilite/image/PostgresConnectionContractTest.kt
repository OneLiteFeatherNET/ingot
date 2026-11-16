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

/**
 * The pure parts of the PostgreSQL rehearsal: the engine it runs, the connection string it hands to a replica, and the
 * image it starts. They need no Docker, no clock and no shared state, so they run on every build of the suite.
 */
class PostgresConnectionContractTest {

    @Test
    @DisplayName("the rehearsal knows a PostgreSQL engine next to SQLite and MariaDB")
    fun `postgresql is a database engine of the rehearsal`() {
        assertThat(DatabaseEngine.entries)
            .describedAs("the database engines of the upgrade rehearsal")
            .contains(DatabaseEngine.POSTGRESQL)
    }

    @Test
    @DisplayName("the PostgreSQL connection string has the host, database, user and password in the configuration order")
    fun `postgresql connection string follows the configuration form`() {
        assertThat(Postgres.connectionFor("10.89.3.2:5432"))
            .describedAs("the value of the database setting for a PostgreSQL server at 10.89.3.2:5432")
            .isEqualTo("postgresql 10.89.3.2:5432 ingot ingot database-password-for-the-mixed-version-test")
    }

    @Test
    @DisplayName("the PostgreSQL image is pinned by tag and digest, the same reference the integration tests use")
    fun `postgresql image is pinned by tag and digest`() {
        assertThat(POSTGRES_IMAGE)
            .describedAs("the PostgreSQL image of the upgrade rehearsal")
            .isEqualTo("postgres:18-alpine@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015")
    }
}
