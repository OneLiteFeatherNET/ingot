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

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteParameters
import com.reposilite.ReposiliteSpecification
import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Characterisation of the redirect plugin: the top-level paths of the redirected repository answer as they do on main,
 * through the same repository routes. The redirect target is read once, when the instance is built, so the property is set
 * before the launch and cleared after every test.
 */
@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class RedirectDispatchCharacterisationIntegrationTest : ReposiliteSpecification() {

    private companion object {
        const val REDIRECT_PROPERTY = "reposilite.redirect.default-repository"
        const val GAV = "com/example/app/1.0"
        const val JAR = "$GAV/app-1.0.jar"
        const val FIXTURE = "/characterisation/redirect-dispatch.txt"
    }

    override fun overrideParameters(parameters: ReposiliteParameters) {
        // The plugins read the top-level entries of the target while the instance is built, before any launch hook runs,
        // so the artifact is written into the storage root of the target first.
        File(reposiliteWorkingDirectory, "repositories/releases/$JAR").apply {
            parentFile.mkdirs()
            writeText("jar-content")
        }
        System.setProperty(REDIRECT_PROPERTY, "releases")
    }

    @AfterEach
    fun clearRedirectTarget() {
        System.clearProperty(REDIRECT_PROPERTY)
    }

    @Test
    fun `redirected paths answer as they do on main`() {
        // when: the top-level paths are requested, with and without a token
        val (name, secret) = useDefaultManagementToken()
        val transcript = renderTranscript(base, name, secret, listOf(
            CharacterisationProbe("redirected file, anonymous read", "GET", JAR),
            CharacterisationProbe("redirected file, head", "HEAD", JAR),
            CharacterisationProbe("redirected file, authenticated read", "GET", JAR, auth = true),
            CharacterisationProbe("redirected missing file", "GET", "$GAV/missing.jar"),
        ))

        // then: the transcript equals the fixture recorded on main
        compareWithFixture(FIXTURE, transcript)
    }

}
