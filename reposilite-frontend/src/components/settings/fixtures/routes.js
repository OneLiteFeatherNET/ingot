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

/*
 * The route table of the dashboard with every component stubbed. The navigation registry resolves the name of every
 * page it links to, so a test router must know all of them, not only the ones the test draws. The order is the order
 * of the real table, so /settings/repositories is matched before the generic settings domain.
 */

import { ROUTES } from '../../../router/routes'

export const stubRoutes = () => ROUTES
  .filter(route => !route.redirect)
  .map(({ name, path }) => ({ name, path, component: { render: () => null } }))
