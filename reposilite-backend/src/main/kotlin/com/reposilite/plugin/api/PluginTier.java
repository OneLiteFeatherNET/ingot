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

package com.reposilite.plugin.api;

/**
 * The support tier a plugin declares with {@link Plugin#tier()}. The tier is information and a warning: it never decides
 * whether a plugin loads.
 */
public enum PluginTier {

    /** Built into the server or shipped in the image. */
    CORE,

    /** Maintained and tested by the OneLiteFeather maintainers. */
    OFFICIAL,

    /** Any plugin without a further claim. This is the default of {@link Plugin#tier()}. */
    COMMUNITY,

    /** Opts in to an experimental API, which may change or be removed without a major release. */
    EXPERIMENTAL

}
