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

package net.onelitefeather.ingot.deploy

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The gate decides which singleton jobs of design F2 a process submits. Each test builds its own fake scheduler and
 * resolves the switch through [BackgroundJobsSetting], so no test reads the environment or shares state.
 */
internal class BackgroundJobGateTest {

    @Test
    fun `all with the default switch submits every singleton job`() {
        assertThat(submittedBy(DeployTarget.ALL)).isEqualTo(ALL_SINGLETON_JOBS)
    }

    @Test
    fun `server with the default switch submits every singleton job`() {
        assertThat(submittedBy(DeployTarget.SERVER)).isEqualTo(ALL_SINGLETON_JOBS)
    }

    @Test
    fun `worker with the default switch submits every singleton job`() {
        assertThat(submittedBy(DeployTarget.WORKER)).isEqualTo(ALL_SINGLETON_JOBS)
    }

    @Test
    fun `server with the switch false submits no singleton job`() {
        assertThat(submittedBy(DeployTarget.SERVER, switch = "false")).isEmpty()
    }

    @Test
    fun `all with the switch false is refused before any job is submitted`() {
        assertThatThrownBy { submittedBy(DeployTarget.ALL, switch = "false") }
            .isInstanceOf(BackgroundJobsSettingException::class.java)
    }

    @Test
    fun `worker with the switch false is refused before any job is submitted`() {
        assertThatThrownBy { submittedBy(DeployTarget.WORKER, switch = "false") }
            .isInstanceOf(BackgroundJobsSettingException::class.java)
    }

    @Test
    fun `the gate names exactly the singleton jobs of design F2`() {
        assertThat(SingletonJob.entries.map { it.id }).containsExactlyInAnyOrder(
            "cleanup-schedule",
            "audit-purge",
            "search-outbox",
            "search-rebuild",
            "search-verify",
            "search-token-sweep",
        )
    }

    private fun submittedBy(target: DeployTarget, switch: String? = null): Set<SingletonJob> {
        val enabled = BackgroundJobsSetting.resolve(
            target = target,
            commandLine = null,
            properties = emptyMap(),
            environment = switch?.let { mapOf(BackgroundJobsSetting.ENVIRONMENT_VARIABLE to it) } ?: emptyMap(),
        )
        val scheduler = FakeScheduler()
        val gate = BackgroundJobGate(enabled)
        SingletonJob.entries.forEach { job -> gate.submit(job) { scheduler.submit(job) } }
        return scheduler.submitted
    }

    private class FakeScheduler {
        val submitted = mutableSetOf<SingletonJob>()

        fun submit(job: SingletonJob) {
            submitted += job
        }
    }

    private companion object {
        val ALL_SINGLETON_JOBS: Set<SingletonJob> = SingletonJob.entries.toSet()
    }

}
