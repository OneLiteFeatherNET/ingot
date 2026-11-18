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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode

// The closure check of the slim plugin API jar. A class of the jar may name a new Ingot type in its public or protected
// signature only if the jar holds that type too, because a plugin compiled against the jar would not find the type otherwise.
// Only the signature is read: method bodies and private members are not part of what a plugin compiles against.
//
// The legacy com.reposilite.* types and the journalist library are not checked. The server a plugin runs on provides them,
// so a reference to them stops the check without a violation and without an entry in the allowlist.
object PluginApiClosure {

    const val INGOT_PREFIX = "net/onelitefeather/ingot/"

    // Descriptors and generic signatures spell a class type as L<internal name> followed by ';', '<' or '.'.
    private val classType = Regex("""L([\w$/]+)[;<.]""")

    // The artifact maps internal class names (net/a/B) to class file bytes. The allowlist holds dotted names (net.a.B). The
    // result has one line per violation, in a stable order.
    fun violations(artifact: Map<String, ByteArray>, allowlist: Collection<String>): List<String> {
        val listed = allowlist.map { it.replace('.', '/') }
        val notCompiled = listed.filter { it !in artifact }.map {
            "${dotted(it)} is listed in plugin-api-classes.txt but is not in the build output. Remove it from the list or compile it."
        }
        val unlisted = artifact.toSortedMap().flatMap { (name, bytes) ->
            referencesOf(bytes)
                .filter { (_, target) -> target.startsWith(INGOT_PREFIX) && target !in artifact }
                .map { (where, target) ->
                    "${dotted(name)} ($where) refers to ${dotted(target)}, which is neither in the artifact nor on " +
                        "plugin-api-classes.txt. Add it to the allowlist in the same pull request or remove the reference."
                }
        }
        return (notCompiled + unlisted).distinct()
    }

    // Every class type named by a public or protected member, its supertypes or the class declaration, with the place it
    // appears in. Each type is returned once per place.
    private fun referencesOf(bytes: ByteArray): List<Pair<String, String>> {
        val node = ClassNode()
        ClassReader(bytes).accept(node, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        val found = mutableListOf<Pair<String, String>>()
        fun named(where: String, internalName: String?) {
            if (internalName != null) found += where to internalName
        }
        fun spelled(where: String, text: String?) {
            if (text == null) return
            classType.findAll(text).forEach { found += where to it.groupValues[1] }
        }
        fun annotated(where: String, visible: List<AnnotationNode>?, invisible: List<AnnotationNode>?) {
            (visible.orEmpty() + invisible.orEmpty()).forEach { spelled(where, it.desc) }
        }

        val classPlace = "the class declaration"
        spelled(classPlace, node.signature)
        named(classPlace, node.superName)
        node.interfaces.orEmpty().forEach { named(classPlace, it) }
        annotated(classPlace, node.visibleAnnotations, node.invisibleAnnotations)

        node.fields.filter { it.access.isPublicOrProtected() }.forEach { field ->
            val where = "field ${field.name}"
            spelled(where, field.desc)
            spelled(where, field.signature)
            annotated(where, field.visibleAnnotations, field.invisibleAnnotations)
        }

        node.methods.filter { it.access.isPublicOrProtected() }.forEach { method ->
            val where = "method ${method.name}"
            spelled(where, method.desc)
            spelled(where, method.signature)
            method.exceptions.orEmpty().forEach { named(where, it) }
            annotated(where, method.visibleAnnotations, method.invisibleAnnotations)
        }

        return found
    }

    private fun Int.isPublicOrProtected(): Boolean = this and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) != 0

    private fun dotted(internalName: String): String = internalName.replace('/', '.')
}
