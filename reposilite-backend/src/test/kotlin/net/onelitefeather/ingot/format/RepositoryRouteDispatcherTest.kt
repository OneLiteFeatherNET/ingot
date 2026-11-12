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

package net.onelitefeather.ingot.format

import com.reposilite.maven.EffectiveRepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.plugin.api.Facade
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import net.onelitefeather.ingot.repository.RegisteredRepository
import net.onelitefeather.ingot.repository.RepositoryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.asSuccess

/**
 * The dispatcher picks the handler of a repository by its format, with a fake registry and a fake format. No server is started,
 * so the test needs no request context: the handler that serves a repository is the answer under test.
 */
@OptIn(ExperimentalIngotFormatApi::class)
internal class RepositoryRouteDispatcherTest {

    private val maven2Handler = object : RepositoryRequestHandler {
        override fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse> = Unit.asSuccess()
    }

    private val fakeHandler = object : RepositoryRequestHandler {
        override fun handle(request: RepositoryRequest): Result<Unit, ErrorResponse> = Unit.asSuccess()
    }

    private val formats = RepositoryFormats(listOf(
        FakeFormat("maven2", maven2Handler),
        FakeFormat("fake", fakeHandler),
    ))

    private fun dispatcher(vararg repositories: RegisteredRepository): RepositoryRouteDispatcher =
        RepositoryRouteDispatcher(FakeRegistry(repositories.toList()), formats, NoFacades)

    @Test
    fun `a repository of format fake is served by the handler of fake`() {
        val dispatcher = dispatcher(FakeRepository("fake-hosted", format = "fake"))

        assertThat(dispatcher.handlerFor("fake-hosted"))
            .describedAs("a request for a repository of format fake must reach the handler of fake")
            .isSameAs(fakeHandler)
    }

    @Test
    fun `an unknown repository is served by the default format`() {
        val dispatcher = dispatcher(FakeRepository("fake-hosted", format = "fake"))

        assertThat(dispatcher.handlerFor("missing"))
            .describedAs("a repository that does not exist keeps the answer of the default format, maven2")
            .isSameAs(maven2Handler)
    }

    @Test
    fun `a repository of the default format is served by its handler`() {
        val dispatcher = dispatcher(FakeRepository("releases", format = "maven2"))

        assertThat(dispatcher.handlerFor("releases"))
            .describedAs("a repository that names maven2 is served by the maven2 handler")
            .isSameAs(maven2Handler)
    }

    /** A format whose handler is given, so the test can tell which handler serves a request. */
    private class FakeFormat(override val id: String, private val handler: RepositoryRequestHandler) : RepositoryFormat {
        override val pathRules: PathRules = NoPathRules
        override val mount: FormatMount = FormatMount.RepositoryPath
        override val storageHints: StorageHints = NoStorageHints
        override fun handler(context: FormatContext): RepositoryRequestHandler = handler
    }

    private class FakeRepository(override val name: String, override val format: String) : RegisteredRepository {
        override val visibility: RepositoryVisibility = RepositoryVisibility.PUBLIC
        override val effectiveType: EffectiveRepositoryType = EffectiveRepositoryType.HOSTED
        override val storageProvider: StorageProvider
            get() = error("the dispatcher does not read storage")
        override val storesProxiedFiles: Boolean = false
    }

    private class FakeRegistry(private val repositories: List<RegisteredRepository>) : RepositoryRegistry {
        override fun repository(name: String): RegisteredRepository? =
            repositories.firstOrNull { it.name == name }

        override fun repositories(): List<RegisteredRepository> =
            repositories
    }

    private object NoFacades : FormatContext {
        override fun <F : Facade> facade(type: Class<F>): F =
            error("the fake formats do not use facades")
    }

}
