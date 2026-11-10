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

package net.onelitefeather.ingot.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * The dependency edges between the backend packages, checked on the compiled main classes. The rules are the decisions of
 * the extract-repository-registry change (design D4). Each rule names the edge it keeps cut, so a failure says what to undo.
 */
internal class DependencyRulesTest {

    private companion object {
        /** Read once per JVM and never modified, so the rules see the same classes in every test. */
        val backendClasses: JavaClasses by lazy {
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("com.reposilite", "net.onelitefeather.ingot")
        }
    }

    /** Maven is the producer of repository changes and must not know the search index that consumes them. */
    @Test
    fun `maven does not depend on search`() {
        noClasses()
            .that().resideInAPackage("com.reposilite.maven..")
            .should().dependOnClassesThat().resideInAPackage("net.onelitefeather.ingot.search..")
            .because("the artifact index sink lives in net.onelitefeather.ingot.repository.index, see design D3")
            .check(backendClasses)
    }

}
