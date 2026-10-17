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

/** The InnoDB limit of one index key with the `DYNAMIC` row format (artifact search spec, section 2.9). */
private const val INNODB_MAX_KEY_BYTES = 3072

/**
 * What the search tables need from a database vendor (artifact search spec, sections 2.9 and 10.3). The [vendor] names
 * are the lower-cased `Database.vendor` values, as the statistics store reads them.
 *
 * @property binaryCollation the collation of the "binary collation" columns, as Exposed renders it after `COLLATE`
 *   (PostgreSQL quotes the name itself), or null when the vendor's default already compares code points
 * @property bytesPerCharacter the bytes one character of the declared character set can take
 * @property maxIndexKeyBytes the largest index key the vendor accepts, or null when the profile sets no limit
 * @property namedUpsertKeys whether an upsert names its conflict key (`ON CONFLICT (...)`). MySQL, MariaDB and H2 in
 *   MySQL mode, which production always runs, refuse named keys and take the conflict from any unique key of the
 *   statement (`ON DUPLICATE KEY UPDATE`).
 */
internal enum class SearchDialect(
    val vendor: String,
    val binaryCollation: String?,
    val bytesPerCharacter: Int,
    val maxIndexKeyBytes: Int?,
    val namedUpsertKeys: Boolean,
) {
    SQLITE(vendor = "sqlite", binaryCollation = null, bytesPerCharacter = 1, maxIndexKeyBytes = null, namedUpsertKeys = true),
    H2(vendor = "h2", binaryCollation = null, bytesPerCharacter = 1, maxIndexKeyBytes = null, namedUpsertKeys = false),
    MYSQL(vendor = "mysql", binaryCollation = "utf8mb4_bin", bytesPerCharacter = 4, maxIndexKeyBytes = INNODB_MAX_KEY_BYTES, namedUpsertKeys = false),
    MARIADB(vendor = "mariadb", binaryCollation = "utf8mb4_bin", bytesPerCharacter = 4, maxIndexKeyBytes = INNODB_MAX_KEY_BYTES, namedUpsertKeys = false),
    POSTGRESQL(vendor = "postgresql", binaryCollation = "C", bytesPerCharacter = 1, maxIndexKeyBytes = null, namedUpsertKeys = true);

    companion object {

        /** The profile of a database vendor; an unknown vendor is refused rather than guessed. */
        fun of(vendor: String): SearchDialect =
            entries.firstOrNull { it.vendor == vendor.lowercase() }
                ?: throw IllegalStateException("Artifact search does not support the database vendor '$vendor'")

    }

}
