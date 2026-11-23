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


package com.reposilite.plugin.prometheus

import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry

/**
 * Registers the JVM series (memory, garbage collection, threads, class loading, process) of the Prometheus client unless the
 * operator switched them off.
 *
 * @return true when the series were registered
 */
internal fun registerJvmMetrics(settings: PrometheusSettings, registry: PrometheusRegistry): Boolean {
    if (!settings.jvmMetrics) {
        return false
    }

    JvmMetrics.builder().register(registry)
    return true
}
