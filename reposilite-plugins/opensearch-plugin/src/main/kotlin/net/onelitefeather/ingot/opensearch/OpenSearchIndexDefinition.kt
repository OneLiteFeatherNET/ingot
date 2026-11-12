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

package net.onelitefeather.ingot.opensearch

import com.fasterxml.jackson.databind.node.ObjectNode
import com.reposilite.ReposiliteObjectMapper

/**
 * The index definition of mapping version one (design D3): a strict mapping of the document fields, one shard, the configured
 * replicas, a one-second refresh and the lower-case normalizer of the `_lc` fields. The version is recorded in the mapping metadata,
 * so that [open] can refuse an index that another version wrote.
 */
internal object OpenSearchIndexDefinition {

    const val MAPPING_VERSION: Int = 1

    private const val TEMPLATE = """
    {
      "settings": {
        "index": {"number_of_shards": 1, "number_of_replicas": 1, "refresh_interval": "1s"},
        "analysis": {"normalizer": {"ingot_lowercase": {"type": "custom", "filter": ["lowercase"]}}}
      },
      "mappings": {
        "dynamic": "strict",
        "_meta": {"ingot_mapping_version": 1},
        "properties": {
          "repository": {"type": "keyword"},
          "path": {"type": "keyword"},
          "path_lc": {"type": "keyword", "normalizer": "ingot_lowercase"},
          "group_id": {"type": "keyword"},
          "group_id_lc": {"type": "keyword", "normalizer": "ingot_lowercase"},
          "artifact_id": {"type": "keyword"},
          "artifact_id_lc": {"type": "keyword", "normalizer": "ingot_lowercase"},
          "version": {"type": "keyword"},
          "version_lc": {"type": "keyword", "normalizer": "ingot_lowercase"},
          "gav": {"type": "keyword"},
          "artifact_key": {"type": "keyword"},
          "classifier": {"type": "keyword"},
          "extension": {"type": "keyword"},
          "snapshot": {"type": "boolean"},
          "origin": {"type": "keyword"},
          "source_host": {"type": "keyword"},
          "size_bytes": {"type": "long"},
          "last_modified": {"type": "long"},
          "indexed_at": {"type": "long"},
          "sha1": {"type": "keyword"},
          "sha256": {"type": "keyword"},
          "tokens": {"type": "keyword"}
        }
      }
    }
    """

    private val template: ObjectNode by lazy {
        ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readTree(TEMPLATE) as ObjectNode
    }

    /** The body of the create index request, with [replicas] replicas. */
    fun create(replicas: Int): ObjectNode {
        val definition = template.deepCopy()
        (definition.at("/settings/index") as ObjectNode).put("number_of_replicas", replicas)
        return definition
    }

    /** The names of the mapped document fields. */
    fun fieldNames(): Set<String> = template.at("/mappings/properties").fieldNames().asSequence().toSet()

}
