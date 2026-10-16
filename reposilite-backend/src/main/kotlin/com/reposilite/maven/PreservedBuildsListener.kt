/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.maven

import net.onelitefeather.ingot.search.ArtifactIndexSink
import com.reposilite.maven.api.DeployEvent
import com.reposilite.maven.api.METADATA_FILE
import com.reposilite.plugin.api.EventListener

/**
 * Removes the files of older builds from SNAPSHOT directories of repositories that do not preserve snapshots, and
 * reconciles the version-level metadata with what is left.
 *
 * Event sequence of one snapshot deploy: the client stores its `maven-metadata.xml`, which emits the [DeployEvent] this
 * listener handles. The stored metadata is the source for the pruning, and the reconciliation runs right after the
 * pruning, so it never writes an older copy of the metadata. The client then uploads the checksums of its metadata,
 * which emit their own events and are handled as well, because they describe the content the client sent and not the
 * reconciled one.
 */
internal class PreservedBuildsListener(
    private val mavenFacade: MavenFacade,
    private val artifactIndexSink: ArtifactIndexSink,
) : EventListener<DeployEvent> {

    override fun onCall(event: DeployEvent) {
        val repository = event.repository
            .takeUnless { it.preserveSnapshots }
            ?: return

        val versionDirectory = event.gav.getParent()
        val fileName = event.gav.getSimpleName()
        if (!versionDirectory.getSimpleName().endsWith(SNAPSHOT_SUFFIX) || fileName !in versionMetadataFiles) {
            return
        }

        if (fileName != METADATA_FILE) {
            mavenFacade.reconcileSnapshotVersion(repository, versionDirectory)
            return
        }

        mavenFacade.findMetadata(repository, versionDirectory)
            .merge(repository.storageProvider.getFiles(versionDirectory)) { metadata, files -> metadata to files }
            .peek { (metadata, files) ->
                val snapshotToPreserve = metadata.versioning?.snapshot?.timestamp ?: return@peek

                files
                    .filter { it.locationAfterLast("/").toString().startsWith(metadata.artifactId!!) }
                    .filterNot { it.toString().contains(snapshotToPreserve) }
                    .map { file ->
                        repository.storageProvider.removeFile(file)
                            // queued right after the removal, so the index hears of it even when the reconcile below fails
                            .peek { artifactIndexSink.removed(repository.name, file.provenancePath()) }
                    }
                    .count()
                    .also { mavenFacade.logger.info("DEPLOY | Preserved Builds Listener | $it deprecated file(s) have been removed") }

                mavenFacade.reconcileSnapshotVersion(repository, versionDirectory)
            }
            .onError { throw RuntimeException(it.toString()) } // not sure how to handle failures of events yet
    }

}
