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

package com.reposilite.plugin.example;

import com.reposilite.plugin.api.Plugin;
import com.reposilite.plugin.api.PluginTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamplePluginTierDeclarationTest {

    @Test
    void shouldDeclareTheExamplePluginTierAsCommunity() {
        // when: the tier is read from the plugin annotation by reflection
        PluginTier tier = ExamplePlugin.class.getAnnotation(Plugin.class).tier();

        // then: the example is a template for plugin authors, so it is community
        assertThat(tier)
            .describedAs("tier of example-plugin")
            .isEqualTo(PluginTier.COMMUNITY);
    }

}
