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

package com.reposilite.web

import org.slf4j.LoggerFactory

/**
 * Lives in the package that the package level under test names. The logger of the caller decides the package level,
 * so a line written from this class is the one that `INGOT_LOG_LEVELS` can mute.
 */
object LogLevelWebProbe {

    fun logInfo() {
        LoggerFactory.getLogger(LogLevelWebProbe::class.java).info("probe web info line")
    }

}
