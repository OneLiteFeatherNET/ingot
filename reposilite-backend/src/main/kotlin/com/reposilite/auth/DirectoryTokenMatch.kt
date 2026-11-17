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
import net.onelitefeather.ingot.identity.model.PrincipalRecord

/**
 * The rule for a directory login that matches a token by name. The login is refused when the user it names has a second
 * factor, because this login would skip that factor. It is also refused when the owner of the token is a user with a
 * second factor: the token must not be reached with the directory password either. Machine principals have no factor.
 */
internal object DirectoryTokenMatch {

    fun refused(owner: AccessSubject, directoryUser: PrincipalRecord?, hasSecondFactor: (Int) -> Boolean): Boolean {
        val ownerHasFactor = owner.principalId?.let { owner.kind == PrincipalKind.USER && hasSecondFactor(it) } ?: false
        val directoryUserHasFactor = directoryUser?.let { it.kind == PrincipalKind.USER && hasSecondFactor(it.id) } ?: false
        return ownerHasFactor || directoryUserHasFactor
    }

}
