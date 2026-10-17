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

package net.onelitefeather.ingot.search.application

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.specification.RecordingArtifactIndexSink
import com.reposilite.maven.specification.RecordingArtifactIndexSink.IndexCall
import com.reposilite.plugin.Extensions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

/** The sink that the maven plugin writes through, while search may be registered later or not at all. */
internal class SearchFacadeArtifactIndexSinkTest {

    private val extensions = Extensions(InMemoryLogger())

    @Test
    fun `should queue into the sink of the search facade once it is registered`() {
        // given: a search facade whose sink records, registered after the maven sink was created
        val recording = RecordingArtifactIndexSink()
        val sink = SearchFacadeArtifactIndexSink(extensions)
        extensions.registerFacade(SearchFacade(recording))

        // when: a change is reported through the maven sink
        sink.changed("releases", "com/example/app/1.0/app-1.0.jar")

        // then: the search sink receives it
        assertThat(recording.recorded)
            .containsExactly(IndexCall("changed", "releases", "com/example/app/1.0/app-1.0.jar"))
    }

    @Test
    fun `should discard every call while no search facade is registered`() {
        // given: a maven sink and no search facade registered
        val sink = SearchFacadeArtifactIndexSink(extensions)

        // when / then: the calls complete without effect and without an exception
        assertThatCode {
            sink.changed("releases", "com/example/app/1.0/app-1.0.jar")
            sink.removed("releases", "com/example/app/1.0")
            sink.reorigin("releases", "com/example/app/1.0")
        }.doesNotThrowAnyException()
    }

}
