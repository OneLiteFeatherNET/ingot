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
package com.reposilite.frontend.api

import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.identity.model.NodePattern

/**
 * Checks what a plugin registers, as plain functions that return the problems found. An empty list means valid.
 * Every message names the problem and, where it helps, the value, so a warning in the log is enough to fix it.
 */
internal object PluginUiValidator {

    private val ID = Regex("[a-z0-9-]{1,32}")
    private val PATH_CHARACTERS = Regex("[A-Za-z0-9._~/-]+")
    private val WRITE_METHODS = setOf("POST", "PUT", "DELETE")
    private val FORM_METHODS = setOf("PUT", "POST")
    private val AREAS: List<String> = NodePattern.ADMIN_AREAS + PluginUiModel.WILDCARD_AREA

    private const val TEXT_LIMIT = 2000
    private const val TITLE_LIMIT = 60
    private const val DESCRIPTION_LIMIT = 200
    private const val LABEL_LIMIT = 60
    private const val CONFIRM_LIMIT = 300
    private const val MAX_COLUMNS = 12
    private const val MIN_REFRESH = 5
    private const val MAX_REFRESH = 300

    fun pageProblems(plugin: String, page: PluginUiPage): List<String> = buildList {
        if (!ID.matches(page.id)) add("id '${page.id}' must match [a-z0-9-] and be at most 32 characters")
        if (page.title.isEmpty() || page.title.length > TITLE_LIMIT) add("title of page '${page.id}' must be 1 to $TITLE_LIMIT characters")
        page.description?.let { if (it.length > DESCRIPTION_LIMIT) add("description of page '${page.id}' is longer than $DESCRIPTION_LIMIT characters") }
        if (page.group !in PluginUiModel.GROUPS) add("group '${page.group}' of page '${page.id}' is unknown, valid groups: ${PluginUiModel.GROUPS.joinToString()}")
        if (page.area !in AREAS) add("area '${page.area}' of page '${page.id}' is unknown, valid areas: ${AREAS.joinToString()}")
        if (page.blocks.isEmpty() || page.blocks.size > PluginUiModel.MAX_BLOCKS) {
            add("page '${page.id}' has ${page.blocks.size} blocks, a page takes 1 to ${PluginUiModel.MAX_BLOCKS} blocks")
        }
        page.blocks.forEachIndexed { index, block -> addAll(blockProblems(plugin, page.id, index, block)) }
    }

    private fun blockProblems(plugin: String, pageId: String, index: Int, block: PluginUiBlock): List<String> {
        val where = "block $index of page '$pageId'"
        return buildList {
            when (block) {
                is PluginUiBlock.Text -> {
                    if (block.text.isEmpty() || block.text.length > TEXT_LIMIT) add("text of $where must be 1 to $TEXT_LIMIT characters")
                }
                is PluginUiBlock.KeyValue -> pathProblem(plugin, block.source, "source of $where")?.let(::add)
                is PluginUiBlock.Table -> {
                    pathProblem(plugin, block.source, "source of $where")?.let(::add)
                    if (block.columns.isEmpty() || block.columns.size > MAX_COLUMNS) add("$where has ${block.columns.size} columns, a table takes 1 to $MAX_COLUMNS")
                    block.columns.forEach { column ->
                        if (column.key.isEmpty() || column.label.isEmpty() || column.label.length > LABEL_LIMIT) {
                            add("column '${column.key}' of $where needs a key and a label of 1 to $LABEL_LIMIT characters")
                        }
                    }
                }
                is PluginUiBlock.Form -> {
                    pathProblem(plugin, block.source, "source of $where")?.let(::add)
                    pathProblem(plugin, block.target, "target of $where")?.let(::add)
                    if (block.method !in FORM_METHODS) add("method '${block.method}' of $where must be PUT or POST")
                    if (block.schema.isEmpty()) add("schema of $where is empty")
                    block.schema.let { addAll(referenceProblems(it, where)) }
                    block.uiSchema?.let { addAll(referenceProblems(it, where)) }
                }
                is PluginUiBlock.Action -> {
                    if (block.label.isEmpty() || block.label.length > LABEL_LIMIT) add("label of $where must be 1 to $LABEL_LIMIT characters")
                    if (block.method !in WRITE_METHODS) add("method '${block.method}' of $where must be POST, PUT or DELETE")
                    pathProblem(plugin, block.path, "path of $where")?.let(::add)
                    block.confirm?.let { if (it.length > CONFIRM_LIMIT) add("confirm text of $where is longer than $CONFIRM_LIMIT characters") }
                }
                is PluginUiBlock.Status -> {
                    pathProblem(plugin, block.source, "source of $where")?.let(::add)
                    if (block.refreshSeconds !in MIN_REFRESH..MAX_REFRESH) {
                        add("refreshSeconds ${block.refreshSeconds} of $where must be $MIN_REFRESH to $MAX_REFRESH")
                    }
                }
            }
        }
    }

    /** A path is a route of the plugin: it starts with `/api/plugins/<plugin>/` and climbs out of nothing. */
    fun pathProblem(plugin: String, path: String, what: String): String? {
        val prefix = "/api/plugins/$plugin/"
        val rest = path.removePrefix(prefix)
        return when {
            !path.startsWith(prefix) || rest.isEmpty() ->
                "$what '$path' must start with $prefix"
            !PATH_CHARACTERS.matches(path) ->
                "$what '$path' contains characters outside A-Z a-z 0-9 . _ ~ - /"
            rest.split('/').any { it.isEmpty() || it == "." || it == ".." } ->
                "$what '$path' contains an empty, '.' or '..' segment"
            else -> null
        }
    }

    /** A JSON schema may refer only into its own document, so no address outside it is loaded by the browser. */
    private fun referenceProblems(node: Any?, where: String): List<String> = buildList {
        when (node) {
            is Map<*, *> -> node.forEach { (key, value) ->
                if (key == "\$ref" && value is String && !value.startsWith("#")) {
                    add("schema of $where refers to '$value', only references inside the document ('#...') are allowed")
                } else {
                    addAll(referenceProblems(value, where))
                }
            }
            is List<*> -> node.forEach { addAll(referenceProblems(it, where)) }
        }
    }

    fun sectionProblems(plugin: String, section: PluginUiSection): List<String> = buildList {
        if (section.domain.isEmpty()) add("settings section of plugin '$plugin' names no domain")
        if (section.label.isEmpty() || section.label.length > LABEL_LIMIT) add("label of the settings section '${section.domain}' must be 1 to $LABEL_LIMIT characters")
        if (section.group !in PluginUiModel.SECTION_GROUPS) {
            add("group '${section.group}' of the settings section '${section.domain}' is unknown, valid groups: ${PluginUiModel.SECTION_GROUPS.joinToString()}")
        }
    }

    /** The serialised pages of one plugin must fit in [PluginUiModel.MAX_MANIFEST_BYTES]. Null when they fit. */
    fun manifestProblem(plugin: String, pages: List<PluginUiPage>): String? {
        val size = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
            .writeValueAsBytes(pages.map { PluginUiManifestPage.of(plugin, it) })
            .size
        return if (size > PluginUiModel.MAX_MANIFEST_BYTES) {
            "the pages of plugin '$plugin' take $size bytes, the limit is 64 KiB"
        } else {
            null
        }
    }
}
