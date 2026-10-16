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

package net.onelitefeather.ingot.search.outbox

import net.onelitefeather.ingot.search.database.H2FileDatabase
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path

/** The outbox worker tests on H2 (MySQL mode, as production configures it). */
internal class H2OutboxWorkerTest : OutboxWorkerTest() {

    override fun withDatabase(file: Path, block: (Database) -> Unit) {
        H2FileDatabase(file).use { block(it.database) }
    }

}
