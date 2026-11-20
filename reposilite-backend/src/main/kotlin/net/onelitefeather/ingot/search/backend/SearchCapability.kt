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

package net.onelitefeather.ingot.search.backend

/**
 * What a backend can do beyond the core contract (search document attributes, D3). A backend declares a capability only when
 * its own module implements it; the core never asks a backend for something it did not declare.
 */
@ExperimentalIngotSearchApi
enum class SearchCapability {

    /** The backend stores the attributes of a row and removes them together with the row. */
    ATTRIBUTES,

    /** The backend filters by attribute values. */
    ATTRIBUTE_FILTERS,

    /** The backend counts attribute values as facets. */
    ATTRIBUTE_FACETS,

    /** The backend sorts by attribute values. */
    ATTRIBUTE_SORT,

}
