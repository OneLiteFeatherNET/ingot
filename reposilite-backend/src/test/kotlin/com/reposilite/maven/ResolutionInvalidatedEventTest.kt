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

package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.DeployRequest
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.plugin.api.ResolutionInvalidatedEvent
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertOk

internal class ResolutionInvalidatedEventTest : MavenSpecification() {

    private val hosted = "hosted"
    private val by = "dzikoysk@127.0.0.1"

    override fun repositories() = listOf(RepositorySettings(hosted, visibility = PUBLIC, resolutionCacheMaxEntries = 16))

    private fun repository(name: String) =
        mavenFacade.getRepository(name)!!

    private fun content(text: String) = text.byteInputStream()

    @Test
    fun `deploying a metadata file emits exactly one resolution invalidation for its repository`() {
        // given: an invalidation listener over the maven extensions
        val events = mutableListOf<ResolutionInvalidatedEvent>()
        extensions.registerEvent { event: ResolutionInvalidatedEvent -> events.add(event) }

        // when: a maven-metadata.xml file is deployed
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), "com/example/app/maven-metadata.xml".toLocation(), by, content("xml"), generateChecksums = false)))

        // then: one event names the repository
        assertThat(events).hasSize(1)
        assertThat(events.single().repository).isEqualTo(hosted)
    }

    @Test
    fun `deploying an artifact emits no resolution invalidation`() {
        // given: an invalidation listener over the maven extensions
        val events = mutableListOf<ResolutionInvalidatedEvent>()
        extensions.registerEvent { event: ResolutionInvalidatedEvent -> events.add(event) }

        // when: a jar is deployed
        assertOk(mavenFacade.deployFile(DeployRequest(repository(hosted), "com/example/app/1.0.0/app-1.0.0.jar".toLocation(), by, content("jar"), generateChecksums = false)))

        // then: nothing is invalidated
        assertThat(events).isEmpty()
    }
}
