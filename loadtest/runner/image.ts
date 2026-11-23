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
// The Helm values that pin the images under test. The dashboard of a split or scalable topology is a separate image of the
// same build, so it takes the tag of the server. Without it the chart default (its appVersion) would run an older dashboard.

/** The `--set` arguments for the server image and, with it, the dashboard image. */
export function imageHelmArguments(tag: string, digest?: string): string[] {
  return [
    '--set', `image.tag=${tag}`,
    // A rolling tag such as nightly can resolve to an older image on the node, so a digest pins the exact one.
    ...(digest ? ['--set', `image.digest=${digest}`] : []),
    // The chart has no digest value for the dashboard, so only its tag follows the server.
    '--set', `dashboard.image.tag=${tag}`,
  ];
}
