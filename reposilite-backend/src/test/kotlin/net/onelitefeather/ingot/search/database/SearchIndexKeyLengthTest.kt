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

package net.onelitefeather.ingot.search.database

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.AutoIncColumnType
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.CharColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.junit.jupiter.api.Test

/**
 * The index key limit of MySQL and MariaDB with InnoDB (artifact search spec, section 2.9). The sizes are computed from
 * the declared column lengths and the bytes per character of the dialect profile, so the test needs no server.
 */
internal class SearchIndexKeyLengthTest {

    @Test
    fun `every index of the search tables fits the InnoDB key limit on MariaDB`() {
        assertEveryIndexFits(SearchDialect.MARIADB)
    }

    @Test
    fun `every index of the search tables fits the InnoDB key limit on MySQL`() {
        assertEveryIndexFits(SearchDialect.MYSQL)
    }

    @Test
    fun `the group, artifact and version index needs 3060 bytes on MySQL`() {
        assertThat(indexNamed(SearchDialect.MYSQL, "ix_search_gav").keyBytes(SearchDialect.MYSQL))
            .`as`("bytes of ix_search_gav as utf8mb4")
            .isEqualTo(3060)
    }

    @Test
    fun `the repository and path index needs 2560 bytes on MariaDB`() {
        assertThat(indexNamed(SearchDialect.MARIADB, "ix_search_path").keyBytes(SearchDialect.MARIADB))
            .`as`("bytes of ix_search_path as utf8mb4")
            .isEqualTo(2560)
    }

    @Test
    fun `the binary collation of MySQL and MariaDB is utf8mb4_bin`() {
        assertThat(SearchDialect.MARIADB.binaryCollation).isEqualTo("utf8mb4_bin")
        assertThat(SearchDialect.MYSQL.binaryCollation).isEqualTo("utf8mb4_bin")
    }

    private fun assertEveryIndexFits(dialect: SearchDialect) {
        val limit = requireNotNull(dialect.maxIndexKeyBytes) { "${dialect.vendor} has no key limit to check" }
        val oversized = allIndexes(dialect)
            .map { it.customName to it.keyBytes(dialect) }
            .filter { (_, bytes) -> bytes > limit }

        assertThat(oversized)
            .`as`("indexes above the $limit byte key limit of ${dialect.vendor}")
            .isEmpty()
    }

    private fun indexNamed(dialect: SearchDialect, name: String): Index =
        allIndexes(dialect).single { it.customName == name }

    private fun allIndexes(dialect: SearchDialect): List<Index> =
        SearchTables(dialect).all().flatMap { it.indices }

    private fun Index.keyBytes(dialect: SearchDialect): Int =
        columns.sumOf { it.keyBytes(dialect) }

    private fun Column<*>.keyBytes(dialect: SearchDialect): Int =
        when (val type = columnType) {
            is VarCharColumnType -> type.colLength * dialect.bytesPerCharacter
            is CharColumnType -> type.colLength * dialect.bytesPerCharacter
            is LongColumnType, is AutoIncColumnType -> LONG_BYTES
            is IntegerColumnType -> INT_BYTES
            is BooleanColumnType -> BOOLEAN_BYTES
            else -> error("no key size known for column $name of type ${type::class.simpleName}")
        }

    private companion object {
        const val LONG_BYTES = 8
        const val INT_BYTES = 4
        const val BOOLEAN_BYTES = 1
    }

}
