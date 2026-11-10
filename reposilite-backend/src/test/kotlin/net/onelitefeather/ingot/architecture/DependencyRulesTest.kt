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

import com.reposilite.maven.EffectiveRepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
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

    /**
     * Token, cluster, search and the dashboard handler read repositories through the registry. The maven facade stays with
     * the consumers that need its operations (cleanup, redirect, javadoc and the frontend endpoints), see design D4.
     */
    @Test
    fun `token, cluster, search and the dashboard handler do not depend on the maven facade`() {
        noClasses()
            .that().resideInAnyPackage("com.reposilite.token..", "net.onelitefeather.ingot.cluster..", "net.onelitefeather.ingot.search..")
            .or().haveFullyQualifiedName("com.reposilite.frontend.infrastructure.ResourcesFrontendHandler")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.reposilite.maven.MavenFacade")
            .because("they read repository names, visibility and storage through RepositoryRegistry, see design D1")
            .check(backendClasses)
    }

    /** The registry package may name the two value types of Maven and nothing else from it, see design D1. */
    @Test
    fun `the repository package depends on maven only through the visibility and type values`() {
        val valueTypes = JavaClass.Predicates.belongToAnyOf(RepositoryVisibility::class.java, EffectiveRepositoryType::class.java)
        noClasses()
            .that().resideInAPackage("net.onelitefeather.ingot.repository..")
            .should().dependOnClassesThat(
                JavaClass.Predicates.resideInAPackage("com.reposilite.maven..").and(DescribedPredicate.not(valueTypes))
            )
            .because("the registry must stay format-neutral; only the two value types may come from com.reposilite.maven")
            .check(backendClasses)
    }

    /**
     * The Maven endpoints are the handlers of the maven2 format. Only the maven package may name them: every other package
     * reaches Maven requests through the repository dispatcher, see design D4.
     */
    @Test
    fun `only the maven package depends on the maven endpoints`() {
        noClasses()
            .that().resideOutsideOfPackage("com.reposilite.maven..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.reposilite.maven.infrastructure.MavenEndpoints")
            .because("repository requests reach the maven handlers through the repository dispatcher, see design D4")
            .check(backendClasses)
    }

    /**
     * The storage layer moves bytes by path and names no Maven behaviour. The one Maven type it may name is the visibility
     * of a repository, which the public storage API carries and which keeps its package for binary compatibility, see design D6.
     */
    @Test
    fun `storage depends on maven only through the repository visibility`() {
        val visibility = JavaClass.Predicates.belongToAnyOf(RepositoryVisibility::class.java)
        noClasses()
            .that().resideInAPackage("com.reposilite.storage..")
            .should().dependOnClassesThat(
                JavaClass.Predicates.resideInAPackage("com.reposilite.maven..").and(DescribedPredicate.not(visibility))
            )
            .because("the storage layer must not know the Maven rules; only RepositoryVisibility may come from com.reposilite.maven")
            .check(backendClasses)
    }

}
