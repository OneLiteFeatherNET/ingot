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
// The k6 sink of the step result metric. It imports k6/metrics, which exists only inside the k6 runtime, so no unit
// test imports this file; the emitter that uses it is tested in step-metric.ts with a fake sink.

import { Gauge } from 'k6/metrics';
import type { MetricSink } from './step-metric.ts';

/** One gauge per metric name, created on first use, so a sample lands in the metric it names. */
export function k6MetricSink(): MetricSink {
  const gauges = new Map<string, Gauge>();
  return {
    add(name, value, tags) {
      let gauge = gauges.get(name);
      if (gauge === undefined) {
        gauge = new Gauge(name);
        gauges.set(name, gauge);
      }
      gauge.add(value, tags);
    },
  };
}
