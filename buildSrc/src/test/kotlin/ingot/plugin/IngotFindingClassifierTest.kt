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

package ingot.plugin

import ingot.plugin.IngotFindingClassifier.Group
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngotFindingClassifierTest {

    private val experimentalPackages = listOf(
        "net.onelitefeather.ingot.format",
        "net.onelitefeather.ingot.search.backend",
    )

    @Test
    fun experimentalAnnotationMakesFindingExperimental() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.search",
            annotations = listOf("ExperimentalIngotSearchApi"),
            experimentalPackages = experimentalPackages,
        )

        assertThat(group)
            .describedAs("a type marked with an ExperimentalIngot* annotation is experimental, even outside a listed package")
            .isEqualTo(Group.EXPERIMENTAL)
    }

    @Test
    fun bareExperimentalIngotPrefixMakesFindingExperimental() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.plugin",
            annotations = listOf("ExperimentalIngot"),
            experimentalPackages = emptyList(),
        )

        assertThat(group)
            .describedAs("the annotation name starts with ExperimentalIngot, so the bare prefix already counts")
            .isEqualTo(Group.EXPERIMENTAL)
    }

    @Test
    fun otherExperimentalAnnotationDoesNotMakeFindingExperimental() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.plugin",
            annotations = listOf("RequiresOptIn", "ExperimentalStdlibApi"),
            experimentalPackages = emptyList(),
        )

        assertThat(group)
            .describedAs("only names that start with ExperimentalIngot count, so the Kotlin marker of another library does not")
            .isEqualTo(Group.PROMISED)
    }

    @Test
    fun listedExperimentalPackageMakesFindingExperimentalWithoutAnnotation() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.format",
            annotations = emptyList(),
            experimentalPackages = experimentalPackages,
        )

        assertThat(group)
            .describedAs("a package the documentation marks experimental is experimental even without an annotation")
            .isEqualTo(Group.EXPERIMENTAL)
    }

    @Test
    fun subpackageOfListedExperimentalPackageMakesFindingExperimental() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.search.backend.spi",
            annotations = emptyList(),
            experimentalPackages = experimentalPackages,
        )

        assertThat(group)
            .describedAs("a listed package also covers its subpackages, as japicmp package filters do")
            .isEqualTo(Group.EXPERIMENTAL)
    }

    @Test
    fun packageWithListedNameAsPrefixOnlyIsNotExperimental() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.formatter",
            annotations = emptyList(),
            experimentalPackages = experimentalPackages,
        )

        assertThat(group)
            .describedAs("net.onelitefeather.ingot.formatter is not inside net.onelitefeather.ingot.format")
            .isEqualTo(Group.PROMISED)
    }

    @Test
    fun plainFindingIsPromised() {
        val group = IngotFindingClassifier.classify(
            packageName = "net.onelitefeather.ingot.repository",
            annotations = emptyList(),
            experimentalPackages = experimentalPackages,
        )

        assertThat(group)
            .describedAs("a finding without an experimental marker or package is promised")
            .isEqualTo(Group.PROMISED)
    }
}
