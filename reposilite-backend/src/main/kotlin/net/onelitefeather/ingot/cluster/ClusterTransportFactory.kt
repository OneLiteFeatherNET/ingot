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

package net.onelitefeather.ingot.cluster

/**
 * Opens the connection to one bus. The plugin looks the factory up by the scheme of the configured URL, so a transport
 * class is only touched when its scheme is configured. The connection is closed when the instance is disposed.
 *
 * The returned value is replaced by the transport interface once the envelope codec exists.
 */
interface ClusterTransportFactory {

    fun open(url: ClusterBusUrl, settings: ClusterSettings): AutoCloseable
}
