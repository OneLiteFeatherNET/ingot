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
package net.onelitefeather.ingot.identity.evaluation

import net.onelitefeather.ingot.identity.model.NodePattern
import net.onelitefeather.ingot.identity.model.NodeRule

/** Validation of node lists as the administration API reports it: every problem, in one list. */
object NodeRules {

    /** The problems of the list, one message each. Empty when every node is valid and no node is listed twice. */
    fun problemsOf(nodes: List<NodeRule>): List<String> {
        val problems = nodes.mapNotNull { NodePattern.problemOf(it.node) }.toMutableList()
        nodes.groupingBy { it.node }.eachCount().filterValues { it > 1 }.keys.sorted().forEach {
            problems += "Node '$it' is listed twice"
        }
        return problems
    }
}
