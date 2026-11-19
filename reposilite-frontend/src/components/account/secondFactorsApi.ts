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
import type { PasskeyAttestationFields } from '../../store/webauthn'

/* One second factor of the signed in user, as GET /api/identity/me/factors lists it. */
export interface SecondFactor {
  id: number
  name: string
  kind: string
  createdAt: string
  lastUsedAt: string | null
}

/* The options of a passkey registration, as POST /api/identity/me/factors/passkey/options answers them. */
export interface PasskeyRegistrationOptions {
  challengeId: string
  challenge: string
  rpId: string
  userHandle: string | null
}

/* The attestation sent back for a registration, with the challenge it answers. */
export type PasskeyRegistration = PasskeyAttestationFields & { challengeId: string }

/*
 * The endpoints the second factors card is given: client.identity.me.factors and client.identity.me.recoveryCodes of
 * store/client.ts. Every call resolves with the axios answer, so the card reads the body from data.
 */
export interface SecondFactorsApi {
  factors: {
    list(): Promise<{ data: SecondFactor[] }>
    startAuthenticator(): Promise<{ data: { otpauthUri: string } }>
    confirmAuthenticator(code: string): Promise<{ data: { recoveryCodes?: string[] | null } }>
    startPasskey(): Promise<{ data: PasskeyRegistrationOptions }>
    registerPasskey(registration: PasskeyRegistration): Promise<{ data: { id: number; name: string } }>
    rename(id: number, name: string): Promise<{ data: SecondFactor }>
    remove(id: number, code: string): Promise<unknown>
  }
}
