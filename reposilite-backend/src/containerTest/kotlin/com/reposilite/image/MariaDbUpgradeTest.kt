/*
 * Copyright (c) 2023 dzikoysk
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

import java.nio.file.Path
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The same upgrade rehearsal as [UpstreamMigrationTest], with the database on MariaDB instead of the SQLite file. MariaDB
 * is the database an operator moves to when the instance grows, and its conflict handling and identifier counting differ
 * from SQLite, which the identity migration depends on.
 */
class MariaDbUpgradeTest {

    @Test
    @DisplayName("an installation on MariaDB keeps its tokens, routes and settings across the switch and the rollback")
    fun `mariadb installation survives the switch and the rollback`(@TempDir directory: Path) {
        UpgradeScenario(ImageUnderTest.upstream, DatabaseEngine.MARIADB).run(directory)
    }
}
