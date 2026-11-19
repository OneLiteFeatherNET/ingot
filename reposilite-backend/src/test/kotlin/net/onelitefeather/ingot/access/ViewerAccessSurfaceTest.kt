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

package net.onelitefeather.ingot.access

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility

/**
 * The public surface of `net.onelitefeather.ingot.access`, read from the Kotlin metadata so that `internal` members, which
 * the bytecode marks public, are not counted. The rules keep the authorization chain, the token secret and the token hash
 * out of what a plugin can see, see expose-plugin-access-checks, group 4.
 */
internal class ViewerAccessSurfaceTest {

    private companion object {
        const val ACCESS_PACKAGE = "net.onelitefeather.ingot.access"
        const val AUTHORIZATION_PACKAGE = "net.onelitefeather.ingot.identity.authorization."
        const val TOKEN_ENTITY = "com.reposilite.token.AccessToken"
        const val TOKEN_INFRASTRUCTURE_PACKAGE = "com.reposilite.token.infrastructure."

        /** The members every class inherits from Any; hashCode names no token hash, it is the identity hash of the object. */
        val OBJECT_MEMBERS = setOf("equals", "hashCode", "toString")

        /** The public classes of the package, each read once per JVM and never modified. */
        val publicClasses: List<KClass<*>> by lazy {
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages(ACCESS_PACKAGE)
                .filter { it.packageName == ACCESS_PACKAGE }
                .map { it.reflect().kotlin }
                .filter { it.visibility == KVisibility.PUBLIC }
        }
    }

    /** Every public constructor and member of the public classes, labelled by class and name for the failure message. */
    private fun publicMembers(): List<Pair<String, KCallable<*>>> = publicClasses.flatMap { type ->
        (type.constructors + type.members)
            .filter { it.visibility == KVisibility.PUBLIC }
            .map { "${type.simpleName}.${it.name}" to it }
    }

    /** The classes named by the parameters, the return type and their type arguments of [callable]. */
    private fun classesOf(callable: KCallable<*>): List<Class<*>> =
        (callable.parameters.map { it.type } + callable.returnType).flatMap { classesIn(it) }

    private fun classesIn(type: KType): List<Class<*>> =
        listOfNotNull((type.classifier as? KClass<*>)?.java) + type.arguments.mapNotNull { it.type }.flatMap { classesIn(it) }

    @Test
    fun `should read the public classes of the access package`() {
        val names = publicClasses.map { it.simpleName }

        assertThat(names)
            .describedAs("the public classes of the access package, so that the rules below are not checked against nothing")
            .contains("Viewer", "ViewerAccessFacade")
    }

    @Test
    fun `should expose no type of the authorization package in any public member`() {
        val leaks = publicMembers().flatMap { (label, callable) ->
            classesOf(callable)
                .filter { it.name.startsWith(AUTHORIZATION_PACKAGE) }
                .map { "$label names ${it.name}" }
        }

        assertThat(leaks)
            .describedAs("public members of the access package must not name a type of the authorization chain")
            .isEmpty()
    }

    @Test
    fun `should expose no token entity or token infrastructure type in any public member`() {
        val leaks = publicMembers().flatMap { (label, callable) ->
            classesOf(callable)
                .filter { it.name == TOKEN_ENTITY || it.name.startsWith(TOKEN_INFRASTRUCTURE_PACKAGE) }
                .map { "$label names ${it.name}" }
        }

        assertThat(leaks)
            .describedAs("the token entity carries the encrypted secret, so no public member may name it")
            .isEmpty()
    }

    @Test
    fun `should expose no public member named after a token secret or hash`() {
        val named = publicMembers()
            .filter { (_, callable) -> callable.name !in OBJECT_MEMBERS }
            .map { it.first }
            .filter { label -> listOf("secret", "hash").any { label.contains(it, ignoreCase = true) } }

        assertThat(named)
            .describedAs("public members of the access package must not be named after a token secret or hash")
            .isEmpty()
    }

    @Test
    fun `should give the viewer no public constructor`() {
        val constructors = publicClasses
            .filter { it.simpleName == "Viewer" }
            .flatMap { it.constructors }
            .filter { it.visibility == KVisibility.PUBLIC }

        assertThat(constructors)
            .describedAs("a viewer is created only by the facade, so Viewer must have no public constructor")
            .isEmpty()
    }

    @Test
    fun `should not override toString of the viewer`() {
        val declaringClass = Viewer::class.java.getMethod("toString").declaringClass

        assertThat(declaringClass)
            .describedAs("Viewer keeps the default toString, so a log line can never show the token")
            .isEqualTo(Any::class.java)
    }

}
