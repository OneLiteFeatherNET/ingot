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

package net.onelitefeather.ingot.opensearch

// The transport, the circuit breaker and the redactor are shared with the other search plugins in reposilite-search-http. This plugin
// keeps the names it was written with, so that its code and its tests read as before the extraction. The targets are fully qualified
// on purpose: an import of the same name would shadow the alias in its file.
internal typealias OpenSearchHttp = net.onelitefeather.ingot.search.http.SearchHttpClient
internal typealias OpenSearchCircuitBreaker = net.onelitefeather.ingot.search.http.SearchCircuitBreaker
internal typealias OpenSearchRedactor = net.onelitefeather.ingot.search.http.CredentialRedactor
internal typealias HttpCall = net.onelitefeather.ingot.search.http.HttpCall
internal typealias HttpAnswer = net.onelitefeather.ingot.search.http.HttpAnswer
internal typealias HttpExchange = net.onelitefeather.ingot.search.http.HttpExchange
internal typealias JdkHttpExchange = net.onelitefeather.ingot.search.http.JdkHttpExchange
