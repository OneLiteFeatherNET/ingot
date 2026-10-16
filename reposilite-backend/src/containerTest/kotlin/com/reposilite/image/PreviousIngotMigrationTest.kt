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
 * The upgrade of an operator who already runs Ingot, rehearsed against the previous release. Its tokens were created by
 * a version without identity tables, so this is the first run of the identity migration on real data, and the rollback
 * goes back to that release.
 */
class PreviousIngotMigrationTest {

    @Test
    @DisplayName("an installation on the previous Ingot release keeps its tokens, routes and settings across the switch and the rollback")
    fun `previous ingot installation survives the switch and the rollback`(@TempDir directory: Path) {
        UpgradeScenario(ImageUnderTest.previous).run(directory)
    }
}
