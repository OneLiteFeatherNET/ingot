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

package com.reposilite.cleanup

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The switch is read from a map that the test builds, so no test depends on the environment of the machine. */
internal class CleanupCatchUpSettingTest {

    @Test
    fun `catch-up is off when no variable is set`() {
        assertFalse(CleanupCatchUp.read(emptyMap()), "catch-up without the variable")
    }

    @Test
    fun `catch-up is on when the INGOT variable is true`() {
        assertTrue(CleanupCatchUp.read(mapOf("INGOT_CLEANUP_CATCHUP" to "true")), "catch-up with INGOT_CLEANUP_CATCHUP=true")
    }

    @Test
    fun `catch-up is on when only the REPOSILITE fallback is true`() {
        assertTrue(CleanupCatchUp.read(mapOf("REPOSILITE_CLEANUP_CATCHUP" to "true")), "catch-up with the REPOSILITE_ fallback")
    }

    @Test
    fun `the INGOT variable wins over the REPOSILITE fallback`() {
        val environment = mapOf("INGOT_CLEANUP_CATCHUP" to "false", "REPOSILITE_CLEANUP_CATCHUP" to "true")

        assertFalse(CleanupCatchUp.read(environment), "catch-up when INGOT_CLEANUP_CATCHUP=false overrides the fallback")
    }

    @Test
    fun `catch-up stays off for any value other than true`() {
        assertFalse(CleanupCatchUp.read(mapOf("INGOT_CLEANUP_CATCHUP" to "yes")), "catch-up with INGOT_CLEANUP_CATCHUP=yes")
    }

}
