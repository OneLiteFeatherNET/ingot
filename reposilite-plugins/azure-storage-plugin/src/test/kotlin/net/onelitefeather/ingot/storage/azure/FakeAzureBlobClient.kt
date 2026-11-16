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

package net.onelitefeather.ingot.storage.azure

/**
 * An in-memory blob service. Committed blobs are visible, staged blocks are not, as in Azure. A failure can be armed for the next
 * stage, which is how a failed upload is produced without a network.
 */
internal class FakeAzureBlobClient : AzureBlobClient {

    val committed = mutableMapOf<String, ByteArray>()
    val contentTypes = mutableMapOf<String, String>()
    val putNames = mutableListOf<String>()
    val commitCalls = mutableListOf<Pair<String, List<String>>>()
    val stagedBlocks = mutableMapOf<String, MutableMap<String, ByteArray>>()

    /** Called before each stage, with the blob name and the block id. */
    var beforeStage: (String, String) -> Unit = { _, _ -> }

    var stageFailure: RuntimeException? = null

    override fun putBlob(name: String, content: ByteArray, contentType: String) {
        putNames += name
        committed[name] = content
        contentTypes[name] = contentType
    }

    override fun stageBlock(name: String, blockId: String, content: ByteArray) {
        beforeStage(name, blockId)
        stageFailure?.let { throw it }
        stagedBlocks.getOrPut(name) { mutableMapOf() }[blockId] = content
    }

    override fun commitBlockList(name: String, blockIds: List<String>, contentType: String) {
        commitCalls += name to blockIds
        val blocks = stagedBlocks[name].orEmpty()
        committed[name] = blockIds.fold(ByteArray(0)) { acc, id -> acc + blocks.getValue(id) }
        contentTypes[name] = contentType
    }

}
