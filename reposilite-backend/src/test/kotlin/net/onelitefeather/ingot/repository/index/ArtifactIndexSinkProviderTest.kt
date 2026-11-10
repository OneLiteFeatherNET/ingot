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

package net.onelitefeather.ingot.repository.index

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.specification.RecordingArtifactIndexSink
import com.reposilite.maven.specification.RecordingArtifactIndexSink.IndexCall
import com.reposilite.plugin.Extensions
import com.reposilite.plugin.api.Facade
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

/** The lazy sink looks for any facade that provides a sink, not for one particular facade type. */
internal class ArtifactIndexSinkProviderTest {

    private class ProvidingFacade(override val sink: ArtifactIndexSink) : Facade, ArtifactIndexSinkProvider

    private val extensions = Extensions(InMemoryLogger())

    @Test
    fun `should deliver calls to the sink of any registered sink provider`() {
        // given: a facade of an unrelated type that provides a recording sink, registered after the lazy sink was created
        val recording = RecordingArtifactIndexSink()
        val sink = SearchFacadeArtifactIndexSink(extensions)
        extensions.registerFacade(ProvidingFacade(recording))

        // when: a change is reported through the lazy sink
        sink.changed("releases", "com/example/app/1.0/app-1.0.jar")

        // then: the provided sink receives it
        assertThat(recording.recorded)
            .describedAs("the lazy sink must deliver to the sink of any registered ArtifactIndexSinkProvider")
            .containsExactly(IndexCall("changed", "releases", "com/example/app/1.0/app-1.0.jar"))
    }

    @Test
    fun `should discard calls while no registered facade provides a sink`() {
        // given: a lazy sink and only a facade that provides no sink
        extensions.registerFacade(object : Facade {})
        val sink = SearchFacadeArtifactIndexSink(extensions)

        // when / then: every call completes without effect and without an exception
        assertThatCode {
            sink.changed("releases", "com/example/app/1.0/app-1.0.jar")
            sink.removed("releases", "com/example/app/1.0")
            sink.reorigin("releases", "com/example/app/1.0")
        }.describedAs("without a sink provider the lazy sink must do nothing").doesNotThrowAnyException()
    }

}
