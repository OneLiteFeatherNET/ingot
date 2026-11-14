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

package com.reposilite.auth

import net.onelitefeather.ingot.identity.model.AccessSubject
import net.onelitefeather.ingot.identity.model.PrincipalKind

/**
 * The rule for a directory login that matches a token by name. A token whose owner is a user with a second factor must not
 * be reached with the directory password: the factor covers the sign-in of that user, and this login would skip it.
 */
internal object DirectoryTokenMatch {

    fun refused(owner: AccessSubject, hasSecondFactor: (Int) -> Boolean): Boolean {
        val principalId = owner.principalId ?: return false
        return owner.kind == PrincipalKind.USER && hasSecondFactor(principalId)
    }

}
