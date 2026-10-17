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

package net.onelitefeather.ingot.search.api

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.LocalConfiguration
import kong.unirest.core.Unirest.get
import kong.unirest.core.Unirest.post
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import panda.std.reactive.ReferenceUtils

/**
 * With `searchEnabled` off every search route answers 404, as if it did not exist (artifact search spec, 6.4), for anonymous
 * callers and for managers alike: the feature is absent by configuration, not unavailable.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class SearchDisabledApiIntegrationTest : ReposiliteSpecification() {

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        ReferenceUtils.setValue(localConfiguration.searchEnabled, false)
    }

    private val manager: Pair<String, String>
        get() = useDefaultManagementToken()

    @Test
    fun `answers 404 to the anonymous search and file lookup`() {
        assertThat(get("$base/api/maven/search").asString().status).isEqualTo(404)
        assertThat(get("$base/api/maven/search/files?groupId=a&artifactId=b&version=1").asString().status).isEqualTo(404)
    }

    @Test
    fun `answers 404 to the index administration even for a manager`() {
        val (name, secret) = manager

        assertThat(get("$base/api/maven/search/index").basicAuth(name, secret).asString().status).isEqualTo(404)
        assertThat(post("$base/api/maven/search/index/rebuild").basicAuth(name, secret).body("{}").asString().status).isEqualTo(404)
        assertThat(post("$base/api/maven/search/index/verify").basicAuth(name, secret).asString().status).isEqualTo(404)
        assertThat(post("$base/api/maven/search/index/runs/1/cancel").basicAuth(name, secret).asString().status).isEqualTo(404)
    }
}
