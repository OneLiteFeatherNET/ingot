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

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

// The binary incompatible changes of one class, read from the XML report japicmp writes. The class name is the binary name,
// so a nested class keeps its '$'.
data class JapicmpClassChanges(val className: String, val typeChanges: List<String>, val members: List<JapicmpMemberChanges>)

// A changed member: its name as japicmp reports it, a label with the parameter types, and the incompatible change types.
data class JapicmpMemberChanges(val name: String, val label: String, val changes: List<String>)

// Reads the XML report of japicmp. Only changes that are binary incompatible are kept, because those are the ones that break
// a compiled plugin. A removed class is reported with its removal alone, since its members add nothing to it. A member with a
// mangled internal name is dropped (design D4).
object JapicmpComparison {

    private val memberElements = setOf("method", "constructor", "field")

    fun parse(xml: InputStream): List<JapicmpClassChanges> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val root = factory.newDocumentBuilder().parse(xml).documentElement
        return root.elements("class").map(::classChanges)
    }

    private fun classChanges(type: Element): JapicmpClassChanges {
        val typeChanges = type.elements("compatibilityChange")
            .filter { owner(it) === type }
            .incompatibleTypes()
        val members = if ("CLASS_REMOVED" in typeChanges) emptyList() else memberElements
            .flatMap { name -> type.elements(name) }
            .filter { owner(it) === type && it.getAttribute("binaryCompatible") == "false" }
            // A constructor carries the binary name of its class, so only methods and fields are filtered by name.
            .filter { it.tagName == "constructor" || !InternalClassFilter.isExcludedMember(it.getAttribute("name")) }
            .map(::memberChanges)
            .filter { it.changes.isNotEmpty() }
        return JapicmpClassChanges(type.getAttribute("fullyQualifiedName"), typeChanges, members)
    }

    private fun memberChanges(member: Element): JapicmpMemberChanges {
        val name = member.getAttribute("name")
        val label = when (member.tagName) {
            "field" -> name
            else -> {
                val parameters = member.elements("parameter").joinToString(", ") { it.getAttribute("type") }
                val shown = if (member.tagName == "constructor") "<init>" else name
                "$shown($parameters)"
            }
        }
        return JapicmpMemberChanges(name, label, member.elements("compatibilityChange").incompatibleTypes())
    }

    // The nearest class or member element that holds a node.
    private fun owner(node: Element): Element? {
        var parent = node.parentNode
        while (parent is Element) {
            if (parent.tagName == "class" || parent.tagName in memberElements) return parent
            parent = parent.parentNode
        }
        return null
    }

    private fun List<Element>.incompatibleTypes(): List<String> =
        filter { it.getAttribute("binaryCompatible") == "false" }.map { it.getAttribute("type") }.distinct()

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
