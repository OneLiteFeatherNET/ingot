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
package net.onelitefeather.ingot.identity.service

import com.reposilite.token.AccessTokenIdentifier

/**
 * Who asks for an identity write. A token is checked against the rights it holds. The system is the server itself, used by
 * code that is not a request, and it is not checked. There is no default, so every caller states which one it is.
 */
sealed interface WriteCaller {

    data class Token(val identifier: AccessTokenIdentifier) : WriteCaller

    object System : WriteCaller
}
