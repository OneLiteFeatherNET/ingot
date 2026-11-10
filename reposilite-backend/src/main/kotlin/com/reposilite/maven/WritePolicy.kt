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

package com.reposilite.maven

/**
 * What a repository allows writers to do, as the dashboard describes it. It is a view: it is derived from
 * `redeployment` and `readOnly` and stored nowhere, so `redeployment` stays the only source of the 409 answer.
 *
 * - [ALLOW] overwrites existing files.
 * - [ALLOW_ONCE] accepts a file once and refuses a second upload to the same path with 409.
 * - [READ_ONLY] refuses every deploy and delete with 405.
 */
enum class WritePolicy {
    ALLOW,
    ALLOW_ONCE,
    READ_ONLY,
}

/** The write policy that [redeployment] and [readOnly] describe together. [readOnly] wins over [redeployment]. */
fun writePolicyOf(redeployment: Boolean, readOnly: Boolean): WritePolicy =
    when {
        readOnly -> WritePolicy.READ_ONLY
        redeployment -> WritePolicy.ALLOW
        else -> WritePolicy.ALLOW_ONCE
    }
