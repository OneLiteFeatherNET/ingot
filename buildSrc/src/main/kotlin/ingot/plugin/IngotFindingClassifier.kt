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

// Groups a finding of the Ingot plugin API report (design D3 of extend-plugin-abi-gate). A finding is experimental when its type carries
// an annotation whose simple name starts with ExperimentalIngot, or when its package is one of the experimental packages, which covers
// their subpackages too. Every other finding is promised. Only the promised group may block a later release.
object IngotFindingClassifier {

    const val EXPERIMENTAL_ANNOTATION_PREFIX = "ExperimentalIngot"

    enum class Group { PROMISED, EXPERIMENTAL }

    fun classify(
        packageName: String,
        annotations: Collection<String>,
        experimentalPackages: Collection<String>,
    ): Group {
        val annotatedExperimental = annotations.any { it.startsWith(EXPERIMENTAL_ANNOTATION_PREFIX) }
        val inExperimentalPackage = experimentalPackages.any { packageName == it || packageName.startsWith("$it.") }
        return if (annotatedExperimental || inExperimentalPackage) Group.EXPERIMENTAL else Group.PROMISED
    }
}
