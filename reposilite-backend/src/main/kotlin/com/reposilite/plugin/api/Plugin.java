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

package com.reposilite.plugin.api;

import com.reposilite.ReposilitePropertiesKt;
import com.reposilite.configuration.shared.api.SharedSettings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents plugin metadata, should be used to annotate {@link com.reposilite.plugin.api.ReposilitePlugin}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Plugin {

    /**
     * @return the name of this plugin
     */
    String name();

    /**
     * @return version of plugin, by default it is same as the current Reposilite version
     * @see com.reposilite.ReposilitePropertiesKt#VERSION
     */
    String version() default ReposilitePropertiesKt.VERSION;

    /**
     * @return array of plugins required to launch before this one
     */
    String[] dependencies() default {};

    /**
     * @return custom configuration class used by plugin
     */
    Class<? extends SharedSettings> settings() default SharedSettings.class;

    /**
     * @return the support tier of this plugin. A plugin that does not set it reads as {@link PluginTier#COMMUNITY}.
     * The tier is information only, it never decides whether the plugin loads.
     */
    PluginTier tier() default PluginTier.COMMUNITY;

    /**
     * @return a short description of what this plugin is for, as plain text. Control characters are removed, and a text longer
     * than 280 characters is cut with a warning at start. The description never decides whether the plugin loads, and the empty
     * text, the default, means no description.
     */
    String description() default "";

}
