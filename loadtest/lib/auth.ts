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
// Authorization headers for the auth and dashboard scenarios. Pure string work, so it runs in k6 and in Node.

const BASE64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

/** Standard base64 of an ASCII string. Credentials here are ASCII, so no UTF-8 handling is needed. */
export function base64Encode(text: string): string {
  let out = '';
  for (let i = 0; i < text.length; i += 3) {
    const a = text.charCodeAt(i);
    const b = i + 1 < text.length ? text.charCodeAt(i + 1) : 0;
    const c = i + 2 < text.length ? text.charCodeAt(i + 2) : 0;
    const triple = (a << 16) | (b << 8) | c;
    out += BASE64[(triple >> 18) & 63];
    out += BASE64[(triple >> 12) & 63];
    out += i + 1 < text.length ? BASE64[(triple >> 6) & 63] : '=';
    out += i + 2 < text.length ? BASE64[triple & 63] : '=';
  }
  return out;
}

export function basicAuthHeader(user: string, password: string): Record<string, string> {
  return { Authorization: `Basic ${base64Encode(`${user}:${password}`)}` };
}

export function bearerAuthHeader(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}` };
}
