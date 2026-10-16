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

import net.onelitefeather.ingot.search.access.SearchAccess
import org.jetbrains.exposed.v1.core.Op

/**
 * The row filter of [SearchAccess] as an Exposed condition over `search_artifact` (artifact search spec, 5.3). Every query
 * of the search engine starts with this condition.
 */
internal fun SearchAccess.toCondition(table: SearchArtifactTable): Op<Boolean> =
    TODO("task 14: render the row filter as SQL")
