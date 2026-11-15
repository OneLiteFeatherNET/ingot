export interface paths {
    "/api/admin/read-only": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Reports whether the instance is read-only and where that state comes from. Note: Requires the maintenance area */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The state and its source, environment or stored */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Token without the maintenance area (ingot.admin.maintenance) has been used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        /** Turns read-only on: writes are refused with 503 on every replica until it is turned off. Note: Requires the maintenance area */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The state after the change */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Token without the maintenance area (ingot.admin.maintenance) has been used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The environment forces the state, or another change happened at the same time */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        /** Turns read-only off again. Note: Requires the maintenance area */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The state after the change */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Token without the maintenance area (ingot.admin.maintenance) has been used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The environment forces the state, or another change happened at the same time */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/support/availability": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Tells whether the system information and the support bundle are offered. Note: Requires the console area */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SupportAvailability"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/support/bundle": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Downloads a zip with the system information, the configuration, the cluster, the plugins and the cached log of this instance. Note: Requires the console area */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The support bundle as application/zip */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description No credentials were sent */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The principal has no console area */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The support bundle is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Another bundle is being built */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The final redaction check refused the bundle, or it exceeds the size cap */
                500: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/admin/support/system-info": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns the system information of this instance, with every secret withheld or masked. Note: Requires the console area */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SystemInformation"];
                    };
                };
                /** @description No credentials were sent */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The principal has no console area */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The support bundle is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The final redaction check refused the answer */
                500: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns audited events, newest first. Filters: actor, action (one or more, comma separated or repeated), repository, q, from, to. Pages with cursor and limit. Note: Requires manager */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description A page of events and the cursor of the next page, or null on the last page */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A filter, limit or cursor is not valid */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Token without the audit area (ingot.admin.audit) has been used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/audit/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Exports the audited events that match the filters of /api/audit as CSV, newest first. Note: Requires manager */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The matching events as text/csv */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A filter is not valid */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Token without the audit area (ingot.admin.audit) has been used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Log in with a login provider
         * @description Checks the credentials of a human user and starts a browser session. Wrong credentials and unknown users get the same answer. A user with an authenticator gets 200 with mfaRequired set instead of a session: the challenge cookie then goes to /api/auth/mfa/verify
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["LoginRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LoginResponse"];
                    };
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Unauthorized */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Forbidden */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * End the browser session
         * @description Revokes the refresh token family of the session and clears the session cookies
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Logged out */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get token details
         * @description Returns details about the requested token
         */
        get: {
            parameters: {
                query?: never;
                header: {
                    /** @description Name and secret provided as basic auth credentials */
                    Authorization: string;
                };
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Details about the token for succeeded authentication */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SessionDetails"];
                    };
                };
                /** @description Error message related to the unauthorized access in case of any failure */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/mfa/verify": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Complete a sign-in with a second factor
         * @description Completes the sign-in that the login route started for a user with an authenticator. Takes the code from the authenticator app or a recovery code, and needs the challenge cookie. The challenge is single use and expires after five minutes. Starts the browser session on success
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["SecondFactorRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LoginResponse"];
                    };
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Unauthorized */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/oidc/{id}/callback": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Finish a login with an OpenID Connect provider
         * @description Exchanges the code, validates the ID token, starts the browser session and redirects to the UI, or to /?loginError=<code>
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the provider */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Redirect back to the UI, with the session cookies or with a login error */
                302: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/oidc/{id}/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Start a login with an OpenID Connect provider
         * @description Redirects to the provider with PKCE, state and nonce. The state travels in the ingot_oidc cookie for ten minutes
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the provider */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Redirect to the authorization endpoint of the provider */
                302: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description No enabled provider has this id */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The provider lacks a required setting, named in the message */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/providers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List login providers
         * @description Public. Lists the configured login providers and whether the first admin still has to be set up
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LoginProvidersResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/providers/ldap/test": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Test the LDAP login
         * @description Instance admins only. Binds with the service account and, when a user name is given, looks the user up, binds as the user with the password and reads the groups
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["LdapTestRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LdapConnectionReport"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/providers/oidc/{id}/test": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Test an OpenID Connect provider
         * @description Instance admins only. Reads the discovery document and the key set again and reports the endpoints
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the provider */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["OidcConnectionReport"];
                    };
                };
                /** @description No enabled provider has this id */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Renew the browser session
         * @description Exchanges the refresh cookie for a new session. The refresh token is single use: presenting a spent one revokes the whole session
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SessionResponse"];
                    };
                };
                /** @description Unauthorized */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/auth/setup": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Setup status
         * @description Public. Tells whether the first administrator still has to be created
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SetupStatusResponse"];
                    };
                };
            };
        };
        put?: never;
        /**
         * Create the first administrator
         * @description Creates the first user as an instance administrator, using the one-time setup code from the log, and starts a session. Only while no user exists
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["SetupRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LoginResponse"];
                    };
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Forbidden */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Conflict */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/badge/latest/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Artifact's repository */
                    repository: string;
                    /** @description Artifacts' GAV */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cleanup/policies/{id}/dry-run": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Preview a cleanup policy
         * @description Lists the versions the policy would delete, with totals and warnings. Nothing is deleted.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the cleanup policy */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["CleanupPreviewResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The policy does not exist */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cleanup/policies/{id}/run": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Run a cleanup policy
         * @description Deletes the versions the policy plans right now, through the repository delete service, and records the run.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the cleanup policy */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["CleanupRunResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The policy does not exist */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The policy is already running */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/cleanup/runs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List cleanup runs
         * @description The recorded runs, newest first. Each policy keeps its last 20 runs.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns the runs as an array of CleanupRunResponse */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/console/commands": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Console command listing
         * @description The registered console commands with their aliases, options, parameters, and the 'dangerous' and 'sensitive' flags. Used for help and completion.
         */
        get: {
            parameters: {
                query?: never;
                header: {
                    /** @description Name and secret provided as basic auth credentials */
                    Authorization: string;
                };
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Command listing of the given version */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["CommandListing"];
                    };
                };
                /** @description Error message related to the unauthorized access */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Error message when the token has no console area (ingot.admin.console) */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/console/execute": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Remote command execution
         * @description Execute command using POST request. The commands are the same as in the console and can be listed using the 'help' command or '/api/console/commands'. With 'structured=true' the answer carries an exit code and the output with its streams, and a dangerous command waits for 'confirm=true' (409 until then).
         */
        post: {
            parameters: {
                query?: {
                    /** @description Answer with exit code, output streams and node instead of the plain status and response lines */
                    structured?: string;
                    /** @description Confirms a dangerous command in structured mode */
                    confirm?: string;
                };
                header: {
                    /** @description Name and secret provided as basic auth credentials */
                    Authorization: string;
                };
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Status of the executed command (plain), or the structured answer when 'structured=true': exit code, node, and the output lines with their stream (stdout or stderr) */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ExecutionResponse"];
                    };
                };
                /** @description Error message related to the invalid command format (0 < command length < 1024) */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Error message related to the unauthorized access */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Error message when the token has no console area (ingot.admin.console) */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Structured call of a dangerous command without 'confirm=true'. Nothing ran. Body: error 'confirmationRequired', command, reason */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ConfirmationRequiredResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/console/log": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @description Streams the output of logs through an SSE Connection. */
        get: {
            parameters: {
                query?: never;
                header?: {
                    /** @description Name and secret provided as basic auth credentials. Without it, the session cookie of a browser login is used. */
                    Authorization?: string;
                };
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Continuously sends out the log as messages under the `log` event. Sends a keepalive ping through comments. */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/console/sock": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        trace?: never;
    };
    "/api/database/snapshots": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Take a database snapshot
         * @description Writes a consistent copy of the SQLite database into the backups directory of the working directory while the instance keeps serving requests. The answer names the file only. Other databases are refused: use their dump tool.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["DatabaseSnapshotResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The database is not SQLite, or the file name is already taken */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The snapshot could not be written. The partial file is removed. */
                500: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/frontend/plugin-styles/{plugin}/{file}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch the dashboard stylesheet of a plugin
         * @description Returns the stylesheet of the plugin, wrapped in the ingot-plugins layer. The file name is the first 16 hexadecimal characters of the SHA-256 of the served bytes, so the answer is cached for a year. Needs no sign in.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description The name of the plugin */
                    plugin: string;
                    /** @description The first 16 hexadecimal characters of the SHA-256 of the sheet, followed by .css */
                    file: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The stylesheet */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "text/css": string;
                    };
                };
                /** @description Unknown plugin, unknown hash, or the plugin styles are switched off */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/frontend/plugin-ui": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch the dashboard pages and settings sections contributed by plugins
         * @description Returns the pages and settings sections the caller may open, and the names of the enabled plugins. Requires a signed in caller.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The manifest of version 1 */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["PluginUiManifest"];
                    };
                };
                /** @description The caller is not signed in */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/frontend/settings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Fetch the dashboard presentation settings
         * @description Returns the values a dashboard needs to render itself, including its appearance. Public, because a bundled dashboard already exposes all of them to anonymous visitors.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Presentation settings, as plain fields and as placeholder tokens */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FrontendSettingsResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/check": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Decides one node for a principal, optionally narrowed by one of its tokens, with the candidates considered. Or decides a repository path with the query parameters repository, path and action (or the action of a node). Instance admins may check any principal, others only their own. */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/access": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the caller's own access: its teams with their direct parents, its allowed and denied nodes, its grants and whether it is an instance administrator. Any signed-in principal may read it; it never describes another principal. */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The caller's own access summary */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["OwnAccessSummaryDto"];
                    };
                };
                /** @description When no valid credential is presented */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/factors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List own second factors
         * @description The authenticator codes of the signed-in user, without secrets. Pending enrolments are not listed
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The factors of the signed-in user */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/factors/totp": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start an authenticator enrolment
         * @description Starts an enrolment for the signed-in user and returns the otpauth URI once. The enrolment counts as a factor after /totp/confirm accepts a code. Needs the settings key: without it the answer is 409 with the reason
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["AuthenticatorEnrolmentDto"];
                    };
                };
                /** @description Conflict */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/factors/totp/confirm": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Confirm an authenticator enrolment
         * @description Confirms the pending enrolment with a code of the authenticator app. The first factor issues ten recovery codes, returned once
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["CodeRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ConfirmedEnrolmentDto"];
                    };
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Forbidden */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Conflict */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/factors/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Remove an own second factor
         * @description Removes one authenticator of the signed-in user. Needs a current code of the user in the body. Removing the last factor removes the recovery codes too
         */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the factor */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["CodeRequest"];
                };
            };
            responses: {
                /** @description Removed */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Forbidden */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Not Found */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Change own password
         * @description Changes the password of the signed-in user after checking the current one. Every session of the user ends
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/recovery-codes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Regenerate recovery codes
         * @description Replaces the recovery codes of the signed-in user with a new set, returned once. The old codes stop working at once. Needs a current code of the user in the body
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["CodeRequest"];
                };
            };
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["RecoveryCodesDto"];
                    };
                };
                /** @description Bad Request */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Forbidden */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Conflict */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Too Many Requests */
                429: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/tokens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List own tokens
         * @description The tokens of the signed-in user, without their secrets
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        /**
         * Create an own token
         * @description Creates a token for the signed-in user. The secret is returned once, in this response
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/tokens/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Delete an own token
         * @description Deletes an own token. It stops working at once
         */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/me/tokens/{name}/rotate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Rotate an own token
         * @description Replaces the secret of an own token. The new secret is returned once, the old one stops working at once
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/nodes/catalog": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the permission nodes an operator can pick, optionally by prefix. Note: Requires the instance admin (ingot.admin.*) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/nodes/templates": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the predefined node bundles of the user interface. Note: Requires the instance admin (ingot.admin.*) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the principals (users and machine accounts). Note: Requires the users area (ingot.admin.users) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        /** Creates a principal. Note: Requires the users area (ingot.admin.users) */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["CreatePrincipalRequest"];
                };
            };
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns a principal with its teams and tokens (without secrets). Note: Requires the users area (ingot.admin.users) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        /** Deletes a principal with its tokens, memberships and instance role. Note: Requires the users area (ingot.admin.users) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        /** Updates the display name or the disabled flag of a principal. Note: Requires the users area (ingot.admin.users) */
        patch: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["UpdatePrincipalRequest"];
                };
            };
            responses: never;
        };
        trace?: never;
    };
    "/api/identity/principals/{name}/factors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List a user's second factors
         * @description Instance admins only. Names, kinds and times of the factors of a user, never secrets
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the user */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The factors of the user */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Not Found */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        /**
         * Reset a user's second factors
         * @description Instance admins only. Removes every second factor and recovery code of a user, and ends the sessions of the user. The next sign-in needs no second factor
         */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the user */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FactorResetDto"];
                    };
                };
                /** @description Not Found */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/factors/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /**
         * Reset one second factor of a user
         * @description Instance admins only. Removes one factor of a user and ends the sessions of the user
         */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the user */
                    name: string;
                    /** @description Id of the factor */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FactorResetDto"];
                    };
                };
                /** @description Not Found */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/instance-admin": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Grants the instance role to a principal. Granting it again changes nothing. Note: Requires the instance admin (ingot.admin.*) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        post?: never;
        /** Removes the instance role of a principal. The last usable instance admin cannot be removed (409). Note: Requires the instance admin (ingot.admin.*) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/nodes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns the permission nodes of a principal, with its entity tag. Note: Requires the users area (ingot.admin.users) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        /** Replaces the whole permission node list of a principal. A caller may only give rights it holds. Note: Requires the users area (ingot.admin.users) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["ReplaceNodesRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Reset a user's password
         * @description Instance admins only. Sets a new password for a user, which must be changed at the next login. Every session of the user ends
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/tokens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the tokens of a principal. Note: Requires the tokens area (ingot.admin.tokens) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        /** Creates a token for a principal. The secret is returned once. Note: Requires the tokens area (ingot.admin.tokens) */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["CreateTokenRequest"];
                };
            };
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/principals/{name}/tokens/{tokenName}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Deletes a token of a principal. Note: Requires the tokens area (ingot.admin.tokens) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the principal */
                    name: string;
                    /** @description Name of the token */
                    tokenName: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/repositories/{repository}/access": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Explains who has which access on a repository, and why. Note: Requires the users area (ingot.admin.users) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the repository */
                    repository: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the teams with their member and grant counts. Note: Requires the teams area (ingot.admin.teams) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        /** Creates a team. Note: Requires the teams area (ingot.admin.teams) */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["CreateTeamRequest"];
                };
            };
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns a team with its members and grants. Note: Requires the teams area (ingot.admin.teams) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        /** Deletes a team with its memberships and grants. Note: Requires the teams area (ingot.admin.teams) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        /** Updates the description or the external reference of a team. Note: Requires the teams area (ingot.admin.teams) */
        patch: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["UpdateTeamRequest"];
                };
            };
            responses: never;
        };
        trace?: never;
    };
    "/api/identity/teams/{name}/grants": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replaces all grants of a team. Roles: READ, DEPLOY, MAINTAIN, ADMIN. Note: Requires the teams area (ingot.admin.teams) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["ReplaceGrantsRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams/{name}/instance-admin": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Grants the instance role to a team: its members with a valid token become instance admins. Note: Requires the instance admin (ingot.admin.*) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        post?: never;
        /** Removes the instance role of a team. The last usable instance admin cannot be removed (409). Note: Requires the instance admin (ingot.admin.*) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams/{name}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replaces the manual members of a team. Synchronised members are kept. Note: Requires the teams area (ingot.admin.teams) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["ReplaceMembersRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams/{name}/nodes": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns the permission nodes of a team, with its entity tag. Note: Requires the teams area (ingot.admin.teams) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        /** Replaces the whole permission node list of a team. A caller may only give rights it holds. Note: Requires the teams area (ingot.admin.teams) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["ReplaceNodesRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/identity/teams/{name}/parents": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns the parent teams of a team, with its entity tag. Note: Requires the teams area (ingot.admin.teams) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        /** Replaces the parent teams of a team. A cycle or a depth above 8 levels is refused with 409. Note: Requires the teams area (ingot.admin.teams) */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the team */
                    name: string;
                };
                cookie?: never;
            };
            requestBody: {
                content: {
                    "application/json": components["schemas"]["ReplaceParentsRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/details": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the available repositories
         * @description Get the repositories the requesting token is allowed to see as JSON response. Every entry is a RepositoryDirectoryInfo and carries the repository visibility (PUBLIC, HIDDEN or PRIVATE) in addition to the fields of a regular directory. Repositories the token cannot access are not listed at all.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns a directory listing where every file entry describes one accessible repository */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["DirectoryInfo"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/details/{repository}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Browse the root of a repository using API
         * @description Get the root directory of the requested repository as JSON response, the same document as the file path form returns for an empty path
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns the root directory of the repository */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FileDetails"];
                    };
                };
                /** @description Returns 401 in case of unauthorized attempt of access to private repository */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Returns 404 when the repository does not exist */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/details/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Browse the contents of repositories using API
         * @description Get details about the requested file as JSON response
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns document (different for directory and file) that describes requested resource */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FileDetails"];
                    };
                };
                /** @description Returns 401 in case of unauthorized attempt of access to private repository */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Returns 404 (for Maven) and frontend (for user) as a response if requested artifact is not in the repository */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/generate/pom/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            /** @description GroupId, ArtifactId and Version of the stub POM */
            requestBody: {
                content: {
                    "application/json": components["schemas"]["PomDetails"];
                };
            };
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/latest/details/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: {
                    /** @description Changes extension of matched file (by default matches 'jar'). Pass an empty value to match files without an extension. */
                    extension?: string;
                    /** @description Appends classifier suffix to matched file */
                    classifier?: string;
                    /** @description Version (prefix) filter to apply */
                    filter?: string;
                };
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Details about the given file */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FileDetails"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/latest/file/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: {
                    /** @description Changes extension of matched file (by default matches 'jar'). Pass an empty value to match files without an extension. */
                    extension?: string;
                    /** @description Appends classifier suffix to matched file */
                    classifier?: string;
                    /** @description Version (prefix) filter to apply */
                    filter?: string;
                };
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/latest/version/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: {
                    /** @description Changes extension of matched file (by default matches 'jar'). Pass an empty value to match files without an extension. */
                    extension?: string;
                    /** @description Appends classifier suffix to matched file */
                    classifier?: string;
                    /** @description Version (prefix) filter to apply */
                    filter?: string;
                    /** @description Format of expected response type: empty (default) for json; 'raw' for plain text */
                    type?: string;
                    /** @description Whether to sort versions using built-in comparator (default: true). Set to false to preserve raw order from maven-metadata.xml */
                    sorted?: string;
                };
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["LatestVersionResponse"];
                        "text/plain": string;
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/repositories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search the repositories
         * @description List the repositories the requesting token may see, sorted by id, each with its visibility, description and tags. PUBLIC repositories are listed for everyone, PRIVATE repositories for tokens with access to them and HIDDEN repositories for managers only.
         */
        get: {
            parameters: {
                query?: {
                    /** @description Case-insensitive text matched against the id, the description and the tags of a repository */
                    q?: string;
                    /** @description A tag the repository has to carry. Repeat the parameter to require several tags. */
                    tag?: string;
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns the visible repositories that match the query */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["RepositoryListEntriesResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/repositories/{repository}/members": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the repositories a group references
         * @description List the ids of the repositories that a group mirrors through loopback references, in configuration order. Only references the requesting token may list are included, and external proxies are never listed. A repository without loopback references has an empty list.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Repository to inspect */
                    repository: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns the visible repositories the group references */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["RepositoryMembersResponse"];
                    };
                };
                /** @description Returns 401 when the group itself is not visible to the caller */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description Returns 404 when the repository does not exist */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Search artifacts across all repositories the caller may browse
         * @description One page of groups (GAVs), each with the repositories that hold it, the facets over the visible files and the state of the index. A row is returned exactly when the caller may browse its path: PUBLIC repositories to everyone, HIDDEN and PRIVATE ones to callers with READ on the path.
         */
        get: {
            parameters: {
                query?: {
                    /** @description Free text, at most 200 characters. Tokens are matched by prefix; a token of the form group:artifact:version matches coordinates. */
                    q?: string;
                    /** @description Prefix of the groupId */
                    groupId?: string;
                    /** @description Prefix of the artifactId */
                    artifactId?: string;
                    /** @description Exact version, case-insensitive. Cannot be combined with versionPrefix. */
                    version?: string;
                    /** @description Prefix of the version */
                    versionPrefix?: string;
                    /** @description Exact repository name, repeatable */
                    repository?: string;
                    /** @description Exact file extension, repeatable */
                    extension?: string;
                    /** @description Exact classifier, repeatable; - selects files without a classifier */
                    classifier?: string;
                    /** @description 40 hexadecimal characters of the SHA-1 of a file */
                    sha1?: string;
                    /** @description true, false or all (default all) */
                    snapshot?: string;
                    /** @description hosted, mirror or all (default all) */
                    origin?: string;
                    /** @description ISO-8601 instant, inclusive */
                    modifiedAfter?: string;
                    /** @description ISO-8601 instant, exclusive */
                    modifiedBefore?: string;
                    /** @description Minimum size in bytes, inclusive */
                    sizeMin?: string;
                    /** @description Maximum size in bytes, inclusive */
                    sizeMax?: string;
                    /** @description groupId, artifactId, version, modified or size (default groupId) */
                    sort?: string;
                    /** @description asc or desc (default asc) */
                    order?: string;
                    /** @description 0-based page (default 0) */
                    page?: string;
                    /** @description Groups per page, 1 to 100 (default 20) */
                    size?: string;
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ArtifactSearchResponse"];
                    };
                };
                /** @description Invalid parameter or result window exceeded. The message names the parameter. */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Statement timeout, grant set too large for search, or the search backend is not available. Retry-After is set. */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search/files": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * List the files of one artifact version found by search
         * @description The stored files of one GAV, ordered by path. The coordinates are required and compared case-insensitively. The access rules of the search apply: files the caller may not browse are not listed.
         */
        get: {
            parameters: {
                query: {
                    /** @description GroupId of the artifact */
                    groupId: string;
                    /** @description ArtifactId of the artifact */
                    artifactId: string;
                    /** @description Version of the artifact */
                    version: string;
                    /** @description hosted, mirror or all (default all) */
                    origin?: string;
                    /** @description Exact repository name, repeatable */
                    repository?: string;
                    /** @description 0-based page (default 0) */
                    page?: string;
                    /** @description Files per page, 1 to 200 (default 50) */
                    size?: string;
                };
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ArtifactFilesResponse"];
                    };
                };
                /** @description A coordinate is missing or a parameter is invalid. The message names it. */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Statement timeout, grant set too large for search, or the search backend is not available. Retry-After is set. */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search/index": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the state of the search index
         * @description The state, lag and last full rebuild, the row and outbox counts, the repositories, the active run, the last 20 runs and the last verify report. Requires the maintenance area.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SearchIndexResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search/index/rebuild": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start a rebuild of the index from storage
         * @description Reads the stored files of the given repositories again and corrects the index. Without a body, or with an empty object, every repository is rebuilt. Requires the maintenance area.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: {
                content: {
                    "application/json": components["schemas"]["RebuildRequest"];
                };
            };
            responses: {
                /** @description Accepted */
                202: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["StartedRunResponse"];
                    };
                };
                /** @description A repository is unknown or the body is not an object of that shape */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A rebuild or verify run is already running */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The search backend is not available. Retry-After is set. */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search/index/runs/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cancel a live index run
         * @description The instance that runs the job stops after the batch in flight. A run that has finished cannot be cancelled. Requires the maintenance area.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Id of the run */
                    id: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description The run was cancelled */
                204: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance, or the run does not exist */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The run has already finished */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The search backend is not available. Retry-After is set. */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/search/index/verify": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Start a verify run
         * @description Compares a sample of index rows with storage and repairs what differs. Requires the maintenance area.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Accepted */
                202: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["StartedRunResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Search is turned off on this instance */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A rebuild or verify run is already running */
                409: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The search backend is not available. Retry-After is set. */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/storage": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the storage usage of the repositories
         * @description Returns the last computed usage. The first request starts a computation in the background, and computing is true until it finishes. The quota is reported as configured, and quotaBytes is its size in bytes when that is known.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["StorageUsageResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/storage/refresh": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Recompute the storage usage
         * @description Starts a new computation in the background and answers at once with the previous figures, flagged as computing. A computation that is already running is not started twice.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Accepted */
                202: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["StorageUsageResponse"];
                    };
                };
                /** @description Only principals with the maintenance area (ingot.admin.maintenance) can access this endpoint */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/maven/versions/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: {
                    /** @description Version (prefix) filter to apply */
                    filter?: string;
                    /** @description Whether to sort versions using built-in comparator (default: true). Set to false to preserve raw order from maven-metadata.xml */
                    sorted?: string;
                };
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/domain/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Find configuration */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of configuration to fetch */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns dto representing configuration */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 403 if a token without the settings area (ingot.admin.settings) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 404 if non-existing configuration is requested */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        /** Update configuration */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of configuration to update */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns 200 if configuration has been updated successfully */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 403 if a token without the settings area (ingot.admin.settings) is used */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 404 if non-existing configuration is requested */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/domains": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List configurations */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns list of configuration names */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 403 if a token without the settings area (ingot.admin.settings) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/encryption": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Get the settings encryption state
         * @description Reports whether a settings key is active, the configured mode and the number of stored secret values that are still plain text. No value is returned.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Encryption state of the shared settings */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["SettingsEncryptionState"];
                    };
                };
                /** @description Returns 401 without credentials */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 403 if a token without the settings area (ingot.admin.settings) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Export settings
         * @description Returns the saved settings encrypted with a password (Argon2id and AES-256-GCM). The password is at least 12 characters.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Encrypted settings file */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": Record<string, never>;
                    };
                };
                /** @description The password is missing or shorter than 12 characters */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A token without the settings area (ingot.admin.settings) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/import/open": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Open an encrypted settings file
         * @description Decrypts an export with its password and returns the plain settings for review. Nothing is stored.
         */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Plain settings, for review */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description The password is wrong or the file was changed */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description A token without the settings area (ingot.admin.settings) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/settings/schema/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get schema */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of schema to get */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns dto representing configuration schema */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "text/plain": string;
                    };
                };
                /** @description Returns 403 if a token without the settings area (ingot.admin.settings) is used */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 404 if non-existing configuration schema is requested */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/statistics/resolved/all": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Aggregated list of statistics per each repository */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["AllResolvedResponse"];
                    };
                };
                /** @description When a token without the dashboard area (ingot.admin.dashboard) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/statistics/resolved/phrase/{limit}/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Amount of records to find (Maximum: 100) */
                    limit: string;
                    /** @description Repository to search in */
                    repository: string;
                    /** @description Phrase to search for */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Aggregated sum of resolved requests with list a list of them all */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ResolvedCountResponse"];
                    };
                };
                /** @description When the limit is not a number between 1 and 100 */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description When invalid token is used */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
                /** @description When token cannot access the requested path */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/statistics/resolved/unique": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Number of all unique requests */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": number;
                    };
                };
                /** @description When a token without the dashboard area (ingot.admin.dashboard) is used */
                403: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ErrorResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/status/failures": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lists the recorded failures, newest first. Note: Requires Manager */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["FailuresResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        /** Clears all recorded failures. Note: Requires Manager */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/status/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["HealthResponse"];
                    };
                };
                /** @description Service Unavailable */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["HealthResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/status/instance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["InstanceStatusResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/status/ready": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Answers whether this instance can serve requests now. Lists the failing checks when it cannot. */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ReadinessResponse"];
                    };
                };
                /** @description Service Unavailable */
                503: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["ReadinessResponse"];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/status/snapshots": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description OK */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "application/json": components["schemas"]["StatusSnapshot"][];
                    };
                };
            };
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tokens": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns all existing tokens and data such as their permissions. Note: Requires the tokens area (ingot.admin.tokens) */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path?: never;
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/tokens/{name}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Returns data about the token given via it's name. Note: Requires manager or you must be the token owner */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the token to be deleted */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        /** Creates / Updates a token via the specified body. Note: Requires manager permission. */
        put: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the token to be created or updated */
                    name: string;
                };
                cookie?: never;
            };
            /** @description Data about the account including the secret and it's permissions */
            requestBody: {
                content: {
                    "application/json": components["schemas"]["CreateAccessTokenWithNoNameRequest"];
                };
            };
            responses: never;
        };
        post?: never;
        /** Deletes the token specified via it's name. Note: Requires the tokens area (ingot.admin.tokens) */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the token to be deleted */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        options?: never;
        head?: never;
        /** Updates token metadata (description, permissions, routes, expiry) without regenerating the secret. Note: Requires the tokens area (ingot.admin.tokens) */
        patch: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the token to be updated */
                    name: string;
                };
                cookie?: never;
            };
            /** @description Token metadata to update. Any field that is not provided is left unchanged */
            requestBody: {
                content: {
                    "application/json": components["schemas"]["UpdateAccessTokenWithNoNameRequest"];
                };
            };
            responses: never;
        };
        trace?: never;
    };
    "/api/tokens/{name}/secret": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Regenerates and returns a new secret for the token, invalidating the previous one. Note: Requires the tokens area (ingot.admin.tokens) */
        post: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Name of the token whose secret is regenerated */
                    name: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: never;
        };
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/{repository}/{gav}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Browse the contents of repositories
         * @description The route may return various responses to properly handle Maven specification and frontend application using the same path.
         */
        get: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Input stream of requested file */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "multipart/form-data": unknown;
                    };
                };
                /** @description Returns 404 (for Maven) with frontend (for user) as a response if requested resource is not located in the current repository */
                404: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        /**
         * Deploy artifact to the repository
         * @description Deploy supports both, POST and PUT, methods and allows to deploy artifact builds
         */
        put: {
            parameters: {
                query?: never;
                header?: {
                    /** @description Determines if Ingot should generate checksums for this file */
                    "X-Generate-Checksums"?: string;
                };
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Input stream of requested file */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "multipart/form-data": unknown;
                    };
                };
                /** @description Returns 400 if the version policy of the repository refuses the version of the artifact, e.g. a snapshot into a RELEASE repository */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 401 for invalid credentials */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 405 if the repository is a proxy or a group, which take no uploads, or if it is read-only */
                405: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 507 if Ingot does not have enough disk space to store the uploaded file */
                507: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        /**
         * Deploy artifact to the repository
         * @description Deploy supports both, POST and PUT, methods and allows to deploy artifact builds
         */
        post: {
            parameters: {
                query?: never;
                header?: {
                    /** @description Determines if Ingot should generate checksums for this file */
                    "X-Generate-Checksums"?: string;
                };
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Input stream of requested file */
                200: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content: {
                        "multipart/form-data": unknown;
                    };
                };
                /** @description Returns 400 if the version policy of the repository refuses the version of the artifact, e.g. a snapshot into a RELEASE repository */
                400: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 401 for invalid credentials */
                401: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 405 if the repository is a proxy or a group, which take no uploads, or if it is read-only */
                405: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
                /** @description Returns 507 if Ingot does not have enough disk space to store the uploaded file */
                507: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        /**
         * Delete the given file from repository
         * @description Returns 405 if the repository is a proxy or a group, which take no uploads and no deletions, or if it is read-only.
         */
        delete: {
            parameters: {
                query?: never;
                header?: never;
                path: {
                    /** @description Destination repository */
                    repository: string;
                    /** @description Artifact path qualifier */
                    gav: string;
                };
                cookie?: never;
            };
            requestBody?: never;
            responses: {
                /** @description Returns 405 if the repository is a proxy or a group, which take no deletions, or if it is read-only */
                405: {
                    headers: {
                        [name: string]: unknown;
                    };
                    content?: never;
                };
            };
        };
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        AccessTokenDto: {
            /** Format: date */
            createdAt: string;
            description: string;
            expired: boolean;
            /** Format: date-time */
            expiresAt?: string | null;
            identifier: components["schemas"]["AccessTokenIdentifier"];
            name: string;
        };
        AccessTokenIdentifier: {
            type: components["schemas"]["AccessTokenType"];
            /** Format: int32 */
            value: number;
        };
        /** @enum {string} */
        AccessTokenPermission: "MANAGER";
        /** @enum {string} */
        AccessTokenType: "PERSISTENT" | "TEMPORARY" | "PRINCIPAL";
        AllResolvedResponse: {
            repositories: components["schemas"]["RepositoryStatistics"][];
            statisticsEnabled: boolean;
        };
        /**
         * Appearance
         * @description Colours, logos, favicon and footer of the dashboard.
         */
        AppearanceSettings: {
            /**
             * Error colour
             * @description Hex colour in the form #RRGGBB that overrides the error key colour. Leave empty to derive it.
             */
            errorColor: string;
            /**
             * Favicon
             * @description Link to the favicon of the dashboard. Leave empty to use the bundled favicon.
             */
            favicon: string;
            /**
             * Footer text
             * @description Plain text shown in the footer of the dashboard. HTML is not rendered.
             */
            footerText: string;
            /**
             * Logo for dark theme
             * @description Link to a logo shown in the dark theme. Leave empty to use the organisation logo.
             */
            logoDark: string;
            /**
             * Primary colour
             * @description Hex colour in the form #RRGGBB that overrides the primary key colour. Leave empty to derive it.
             */
            primaryColor: string;
            /**
             * Scheme variant
             * @description How the palette is derived from the seed colour. Only applies when a seed colour is set.
             */
            schemeVariant: components["schemas"]["SchemeVariant"];
            /**
             * Secondary colour
             * @description Hex colour in the form #RRGGBB that overrides the secondary key colour. Leave empty to derive it.
             */
            secondaryColor: string;
            /**
             * Seed colour
             * @description Hex colour in the form #RRGGBB that the dashboard palette is derived from.
             *     Leave empty to keep the built-in Ingot theme.
             */
            seedColor: string;
            /**
             * Tertiary colour
             * @description Hex colour in the form #RRGGBB that overrides the tertiary key colour. Leave empty to derive it.
             */
            tertiaryColor: string;
        };
        ArtifactFileItem: {
            browseLink: string;
            classifier?: string | null;
            extension: string;
            /** Format: date-time */
            lastModified: string;
            name: string;
            origin: components["schemas"]["Origin"];
            path: string;
            repository: string;
            sha1?: string | null;
            /** Format: int64 */
            size: number;
            sourceHost?: string | null;
        };
        ArtifactFilesResponse: {
            items: components["schemas"]["ArtifactFileItem"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            total: number;
        };
        ArtifactSearchItem: {
            artifactId: string;
            browseLink: string;
            /** Format: int64 */
            fileCount: number;
            groupId: string;
            /** Format: date-time */
            lastModified: string;
            latestVersion?: string | null;
            repositories: components["schemas"]["RepositoryPresence"][];
            snapshot: boolean;
            version: string;
        };
        ArtifactSearchResponse: {
            facets: components["schemas"]["SearchFacets"];
            indexStatus: components["schemas"]["IndexStatus"];
            items: components["schemas"]["ArtifactSearchItem"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            total: number;
            totalCapped: boolean;
        };
        AuthenticatorEnrolmentDto: {
            otpauthUri?: string;
        };
        CleanupPreviewResponse: {
            /** Format: int32 */
            count: number;
            deletions: components["schemas"]["PlannedDeletion"][];
            evaluatedAt: string;
            policyId: string;
            /** Format: int64 */
            totalBytes: number;
            warnings: string[];
        };
        CleanupRunResponse: {
            /** Format: int64 */
            deletedBytes: number;
            /** Format: int32 */
            deletedCount: number;
            failures: string[];
            finishedAt: string;
            id: string;
            policyId: string;
            startedAt: string;
            status: components["schemas"]["CleanupRunStatus"];
            trigger: components["schemas"]["CleanupTrigger"];
        };
        /** @enum {string} */
        CleanupRunStatus: "SUCCEEDED" | "PARTIAL" | "FAILED";
        /** @enum {string} */
        CleanupTrigger: "MANUAL" | "SCHEDULED";
        ClusterBusResponse: {
            /** Format: int64 */
            applied: number;
            /** Format: int64 */
            dropped?: number | null;
            /** Format: int64 */
            ignored: number;
            /** Format: int64 */
            lastReceivedAt?: number | null;
            /** Format: int64 */
            published: number;
            state: string;
            transport: string;
            url: string;
        };
        ClusterInstanceResponse: {
            /** Format: int32 */
            failuresCount: number;
            health: string;
            id: string;
            /** Format: int64 */
            interval: number;
            /** Format: int32 */
            maxMemory: number;
            /** Format: int32 */
            maxThreads: number;
            samples: components["schemas"]["StatusSnapshot"][];
            stale: boolean;
            /** Format: int64 */
            uptimeStart: number;
            /** Format: double */
            usedMemory: number;
            /** Format: int32 */
            usedThreads: number;
            version: string;
        };
        ClusterStatusResponse: {
            bus: components["schemas"]["ClusterBusResponse"];
            instances: components["schemas"]["ClusterInstanceResponse"][];
        };
        CodeRequest: {
            code?: string | null;
        };
        CommandDescriptor: {
            aliases: string[];
            dangerous: boolean;
            description: string;
            name: string;
            options: components["schemas"]["OptionDescriptor"][];
            parameters: components["schemas"]["ParameterDescriptor"][];
            sensitive: boolean;
            subcommands: string[];
        };
        CommandListing: {
            commands: components["schemas"]["CommandDescriptor"][];
            /** Format: int32 */
            version: number;
        };
        /** @enum {string} */
        CommandStatus: "SUCCEEDED" | "FAILED";
        ConfigurationInformation: {
            local: {
                [key: string]: string;
            };
            shared: {
                [key: string]: components["schemas"]["JsonNode"];
            };
        };
        ConfirmationRequiredResponse: {
            command: string;
            error: string;
            reason: string;
        };
        ConfirmedEnrolmentDto: {
            recoveryCodes?: string[] | null;
        };
        CreateAccessTokenWithNoNameRequest: {
            /** @description Human-readable description of the token */
            description: string;
            /**
             * Format: date-time
             * @description Expiration timestamp (ISO-8601 Instant). If not provided, the token never expires
             */
            expiresAt?: string | null;
            /** @description Permissions assigned to the created token: [MANAGER] */
            permissions: string[];
            /** @description Route permissions assigned to the created token */
            routes: components["schemas"]["CreateAccessTokenWithNoNameRequestRoute"][];
            /** @description If not provided, the secret will be generated automatically */
            secret?: string | null;
            /** @description Determines whether the provided secret is raw or already encrypted */
            secretType: components["schemas"]["SecretType"];
            /** @description Type of the created token (persistent or temporary) */
            type: components["schemas"]["AccessTokenType"];
        };
        CreateAccessTokenWithNoNameRequestRoute: {
            /** @description The path to which the permissions apply */
            path: string;
            /** @description Permissions assigned to the provided path: [READ, WRITE] */
            permissions: string[];
        };
        CreatePrincipalRequest: {
            disabled?: boolean | null;
            displayName?: string | null;
            kind?: string | null;
            name?: string | null;
        };
        CreateTeamRequest: {
            description?: string | null;
            externalRef?: string | null;
            name?: string | null;
        };
        CreateTokenRequest: {
            description?: string | null;
            /** Format: date-time */
            expiresAt?: string | null;
            name?: string | null;
            nodes?: components["schemas"]["NodeDto"][] | null;
            scopes?: string[] | null;
            secret?: string | null;
        };
        DatabaseInformation: {
            endpoint?: string | null;
            kind: string;
        };
        DatabaseSnapshotResponse: {
            createdAt: string;
            fileName: string;
            /** Format: int64 */
            sizeBytes: number;
        };
        DirectoryInfo: {
            files: components["schemas"]["FileDetails"][];
            name: string;
            type: components["schemas"]["FileType"];
        };
        ErrorProblem: {
            message: string;
            node: string;
        };
        ErrorResponse: {
            detail: string;
            instance?: string | null;
            message: string;
            problems?: components["schemas"]["ErrorProblem"][] | null;
            /** Format: int32 */
            status: number;
            title?: string | null;
            type: string;
        };
        ExecutionResponse: {
            response: string[];
            status: components["schemas"]["CommandStatus"];
        };
        FacetBucket: {
            /** Format: int64 */
            count: number;
            value: string;
        };
        FactorResetDto: {
            /** Format: int32 */
            removed?: number;
        };
        FailureResponse: {
            /** Format: int32 */
            count: number;
            /** Format: int64 */
            id: number;
            message?: string | null;
            source: string;
            stacktrace: string;
            timestamp: string;
            type: string;
        };
        FailuresResponse: {
            failures: components["schemas"]["FailureResponse"][];
        };
        FileDetails: {
            name: string;
            type: components["schemas"]["FileType"];
        };
        /** @enum {string} */
        FileType: "FILE" | "DIRECTORY";
        FrontendSettingsResponse: {
            appearance: components["schemas"]["AppearanceSettings"];
            description: string;
            icpLicense: string;
            id: string;
            organizationLogo: string;
            organizationWebsite: string;
            placeholders: {
                [key: string]: string;
            };
            privacyPolicy: string;
            secretsHidden: boolean;
            title: string;
        };
        GrantRequest: {
            pathPrefix?: string | null;
            repository?: string | null;
            role?: string | null;
        };
        HealthResponse: {
            status: string;
        };
        /** @enum {string} */
        IndexState: "READY" | "BUILDING" | "MISSING" | "DEGRADED";
        IndexStatus: {
            /** Format: int64 */
            deadLetters: number;
            /** Format: int64 */
            droppedEnqueues: number;
            /** Format: int64 */
            lagSeconds: number;
            /** Format: date-time */
            lastFullRebuild?: string | null;
            /** Format: int64 */
            pendingEntries: number;
            state: components["schemas"]["IndexState"];
            tokensComplete: boolean;
        };
        IngotInformation: {
            buildTime: string;
            commit: string;
            version: string;
        };
        InstanceStatusResponse: {
            cluster?: components["schemas"]["ClusterStatusResponse"] | null;
            /** Format: int32 */
            failuresCount: number;
            latestVersion: string;
            /** Format: int32 */
            maxMemory: number;
            /** Format: int32 */
            maxThreads: number;
            readOnly: boolean;
            readOnlySource: string;
            /** Format: int64 */
            uptime: number;
            /** Format: double */
            usedMemory: number;
            /** Format: int32 */
            usedThreads: number;
            version: string;
        };
        IntervalRecord: {
            /** Format: int64 */
            count: number;
            /** Format: int64 */
            date: number;
        };
        JsonNode: {
            ""?: components["schemas"]["JsonNode"];
            array: boolean;
            bigDecimal: boolean;
            bigInteger: boolean;
            binary: boolean;
            boolean: boolean;
            containerNode: boolean;
            double: boolean;
            empty: boolean;
            float: boolean;
            floatingPointNumber: boolean;
            int: boolean;
            integralNumber: boolean;
            long: boolean;
            missingNode: boolean;
            nodeType?: components["schemas"]["JsonNodeType"];
            null: boolean;
            number: boolean;
            object: boolean;
            pojo: boolean;
            short: boolean;
            textual: boolean;
            valueNode: boolean;
        };
        /** @enum {string} */
        JsonNodeType: "ARRAY" | "BINARY" | "BOOLEAN" | "MISSING" | "NULL" | "NUMBER" | "OBJECT" | "POJO" | "STRING";
        JvmInformation: {
            /** Format: int32 */
            heapMaxMb: number;
            /** Format: double */
            heapUsedMb: number;
            inputArguments: string[];
            /** Format: int32 */
            processors: number;
            /** Format: int32 */
            threads: number;
            /** Format: int64 */
            uptimeMillis: number;
            vendor: string;
            version: string;
        };
        LastVerifyReport: {
            /** Format: date-time */
            at: string;
            /** Format: int32 */
            changed: number;
            /** Format: double */
            drift: number;
            /** Format: int32 */
            missing: number;
            /** Format: int64 */
            removedRepositoryRows: number;
            /** Format: int32 */
            reorigined: number;
            /** Format: int64 */
            runId: number;
            /** Format: int32 */
            sampled: number;
        };
        LatestVersionResponse: {
            snapshot: boolean;
            version: string;
        };
        LdapConnectionReport: {
            error?: string | null;
            groups?: string[] | null;
            ok: boolean;
            userDn?: string | null;
        };
        LdapTestRequest: {
            password?: string | null;
            username?: string | null;
        };
        LoginPrincipalDto: {
            displayName?: string;
            /** Format: int32 */
            id?: number;
            kind?: string;
            name?: string;
        };
        LoginProviderDto: {
            icon?: string | null;
            id?: string;
            label?: string;
            loginUrl?: string | null;
            type?: string;
        };
        LoginProvidersResponse: {
            providers?: components["schemas"]["LoginProviderDto"][];
            setupRequired?: boolean;
            tokenLogin?: boolean;
        };
        LoginRequest: {
            password?: string | null;
            provider?: string | null;
            username?: string | null;
        };
        LoginResponse: {
            /** Format: date-time */
            expiresAt?: string;
            mustChangePassword?: boolean;
            principal?: components["schemas"]["LoginPrincipalDto"];
        };
        NodeDto: {
            allowed: boolean;
            node: string;
        };
        OidcConnectionReport: {
            endpoints?: components["schemas"]["OidcEndpointsReport"] | null;
            error?: string | null;
            issuer?: string | null;
            ok: boolean;
        };
        OidcEndpointsReport: {
            authorization: string;
            jwks?: string | null;
            token: string;
            userinfo?: string | null;
        };
        OperatingSystemInformation: {
            architecture: string;
            container: boolean;
            name: string;
            version: string;
        };
        OptionDescriptor: {
            arity: string;
            description: string;
            names: string[];
        };
        /** @enum {string} */
        Origin: "HOSTED" | "MIRROR";
        OutboxCounts: {
            /** Format: int64 */
            deadLetters: number;
            /** Format: int64 */
            droppedEnqueues: number;
            /** Format: int64 */
            pending: number;
        };
        OwnAccessSummaryDto: {
            denials: components["schemas"]["OwnDenialDto"][];
            grants: components["schemas"]["OwnGrantDto"][];
            instanceAdmin: boolean;
            nodes: string[];
            principal?: components["schemas"]["OwnPrincipalDto"] | null;
            teams: components["schemas"]["OwnTeamDto"][];
            token?: string | null;
            tokenNarrowed: boolean;
        };
        OwnDenialDto: {
            node: string;
            overridden: boolean;
        };
        OwnGrantDto: {
            pathPrefix?: string | null;
            repository?: string | null;
            role: string;
            team: string;
        };
        OwnPrincipalDto: {
            displayName: string;
            kind: string;
            name: string;
            providers: string[];
        };
        OwnTeamDto: {
            name: string;
            parents: string[];
        };
        ParameterDescriptor: {
            description: string;
            index: string;
            label: string;
            required: boolean;
        };
        PlannedDeletion: {
            path: string;
            reason: string;
            repository: string;
            /** Format: int64 */
            sizeBytes: number;
            version: string;
        };
        PluginInformation: {
            dependencies: string[];
            name: string;
            version: string;
        };
        PluginUiBlock: {
            type: string;
        };
        PluginUiManifest: {
            enabledPlugins: string[];
            pages: components["schemas"]["PluginUiManifestPage"][];
            sections: components["schemas"]["PluginUiManifestSection"][];
            stylesheets: components["schemas"]["PluginUiManifestStylesheet"][];
            /** Format: int32 */
            version: number;
        };
        PluginUiManifestPage: {
            area: string;
            blocks: components["schemas"]["PluginUiBlock"][];
            description?: string | null;
            group: string;
            id: string;
            plugin: string;
            title: string;
        };
        PluginUiManifestSection: {
            domain: string;
            group: string;
            label: string;
            plugin: string;
        };
        PluginUiManifestStylesheet: {
            path: string;
            plugin: string;
            sha256: string;
        };
        PomDetails: {
            artifactId: string;
            groupId: string;
            version: string;
        };
        ReadinessResponse: {
            failing: string[];
            status: string;
        };
        RebuildRequest: {
            repositories?: string[] | null;
        };
        RecoveryCodesDto: {
            recoveryCodes?: string[];
        };
        ReplaceGrantsRequest: {
            grants?: components["schemas"]["GrantRequest"][] | null;
        };
        ReplaceMembersRequest: {
            members?: string[] | null;
        };
        ReplaceNodesRequest: {
            nodes?: components["schemas"]["NodeDto"][] | null;
        };
        ReplaceParentsRequest: {
            parents?: string[] | null;
        };
        RepositoryListEntriesResponse: {
            repositories: components["schemas"]["RepositoryListEntry"][];
        };
        RepositoryListEntry: {
            description: string;
            id: string;
            readOnly?: boolean | null;
            tags: string[];
            type: string;
            typeExplicit: boolean;
            versionPolicy?: string | null;
            visibility: components["schemas"]["RepositoryVisibility"];
            writePolicy?: string | null;
        };
        RepositoryMembersResponse: {
            members: string[];
        };
        RepositoryPresence: {
            name: string;
            origin: components["schemas"]["Origin"];
        };
        RepositoryStatistics: {
            data: components["schemas"]["IntervalRecord"][];
            name: string;
        };
        RepositoryStorageUsage: {
            /** Format: int64 */
            fileCount: number;
            group: boolean;
            id: string;
            quota?: string | null;
            /** Format: int64 */
            quotaBytes?: number | null;
            storageType: string;
            /** Format: int64 */
            usedBytes: number;
        };
        /** @enum {string} */
        RepositoryVisibility: "PUBLIC" | "HIDDEN" | "PRIVATE";
        ResolvedCountResponse: {
            requests: components["schemas"]["ResolvedEntry"][];
            /** Format: int64 */
            sum: number;
        };
        ResolvedEntry: {
            /** Format: int64 */
            count: number;
            gav: string;
        };
        Route: {
            path: string;
            permission: components["schemas"]["RoutePermission"];
        };
        /** @enum {string} */
        RoutePermission: "READ" | "WRITE";
        RunCounters: {
            /** Format: int64 */
            deleted: number;
            /** Format: int64 */
            errors: number;
            /** Format: int64 */
            processed: number;
            /** Format: int64 */
            skippedTooLong: number;
            /** Format: int64 */
            unparsed: number;
            /** Format: int64 */
            upserted: number;
        };
        /** @enum {string} */
        RunKind: "REBUILD" | "VERIFY";
        /** @enum {string} */
        RunStatus: "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
        /** @enum {string} */
        RunTrigger: "MANUAL" | "SCHEDULED" | "STARTUP" | "GAP" | "BACKEND";
        /** @enum {string} */
        SchemeVariant: "TONAL_SPOT" | "VIBRANT" | "EXPRESSIVE" | "NEUTRAL" | "FIDELITY" | "CONTENT" | "MONOCHROME";
        SearchBackendResponse: {
            /** Format: int64 */
            accessMismatches: number;
            detail: string;
            health: string;
            healthDetail: string;
            id: string;
            properties?: {
                [key: string]: string;
            } | null;
            state: components["schemas"]["SearchBackendState"];
        };
        /** @enum {string} */
        SearchBackendState: "AVAILABLE" | "NOT_INSTALLED" | "REFUSED" | "OPEN_FAILED" | "DOWN" | "CLUSTER_MISMATCH";
        SearchFacets: {
            classifier: components["schemas"]["FacetBucket"][];
            extension: components["schemas"]["FacetBucket"][];
            origin: components["schemas"]["FacetBucket"][];
            repository: components["schemas"]["FacetBucket"][];
            snapshot: components["schemas"]["FacetBucket"][];
        };
        SearchIndexResponse: {
            activeRun?: components["schemas"]["SearchRunResponse"] | null;
            backend: components["schemas"]["SearchBackendResponse"];
            engine: string;
            /** Format: int64 */
            lagSeconds: number;
            /** Format: date-time */
            lastFullRebuild?: string | null;
            lastVerify?: components["schemas"]["LastVerifyReport"] | null;
            outbox: components["schemas"]["OutboxCounts"];
            repositories: string[];
            /** Format: int64 */
            rows: number;
            runs: components["schemas"]["SearchRunResponse"][];
            state: components["schemas"]["IndexState"];
        };
        SearchRunResponse: {
            counters: components["schemas"]["RunCounters"];
            cursorPath?: string | null;
            cursorRepository?: string | null;
            /** Format: date-time */
            finishedAt?: string | null;
            /** Format: int64 */
            id: number;
            kind: components["schemas"]["RunKind"];
            lastError?: string | null;
            leaseOwner?: string | null;
            /** Format: date-time */
            leaseUntil?: string | null;
            repositories?: string[] | null;
            /** Format: date-time */
            startedAt: string;
            status: components["schemas"]["RunStatus"];
            trigger: components["schemas"]["RunTrigger"];
        };
        SecondFactorRequest: {
            code?: string | null;
        };
        /** @enum {string} */
        SecretType: "RAW" | "ENCRYPTED";
        SessionDetails: {
            accessToken: components["schemas"]["AccessTokenDto"];
            grants: components["schemas"]["SessionGrant"][];
            instanceAdmin: boolean;
            nodes: string[];
            permissions: components["schemas"]["AccessTokenPermission"][];
            principal?: components["schemas"]["SessionPrincipal"] | null;
            routes: components["schemas"]["Route"][];
            teams: string[];
        };
        SessionGrant: {
            pathPrefix?: string | null;
            repository?: string | null;
            role: string;
            team: string;
        };
        SessionPrincipal: {
            /** Format: int32 */
            id?: number | null;
            kind: string;
            name: string;
        };
        SessionResponse: {
            /** Format: date-time */
            expiresAt?: string;
            principal?: components["schemas"]["LoginPrincipalDto"];
        };
        SettingsEncryptionState: {
            mode: string;
            /** Format: int32 */
            plaintextSecrets: number;
            secretsEncrypted: boolean;
        };
        SetupRequest: {
            displayName?: string | null;
            password?: string | null;
            setupCode?: string | null;
            username?: string | null;
        };
        SetupStatusResponse: {
            required?: boolean;
        };
        StartedRunResponse: {
            /** Format: int64 */
            runId: number;
        };
        StatusSnapshot: {
            /** Format: int64 */
            at: number;
            /** Format: int32 */
            memory: number;
            /** Format: int32 */
            threads: number;
        };
        StorageInformation: {
            details: {
                [key: string]: string;
            };
            kind: string;
            region?: string | null;
            repository: string;
        };
        StorageUsageResponse: {
            computedAt?: string | null;
            computing: boolean;
            repositories: components["schemas"]["RepositoryStorageUsage"][];
            /** Format: int64 */
            totalBytes: number;
        };
        SupportAvailability: {
            enabled: boolean;
        };
        SystemInformation: {
            cluster?: components["schemas"]["ClusterStatusResponse"] | null;
            configuration: components["schemas"]["ConfigurationInformation"];
            database: components["schemas"]["DatabaseInformation"];
            environment: {
                [key: string]: string;
            };
            generatedAt: string;
            ingot: components["schemas"]["IngotInformation"];
            instance: string;
            jvm: components["schemas"]["JvmInformation"];
            operatingSystem: components["schemas"]["OperatingSystemInformation"];
            plugins: components["schemas"]["PluginInformation"][];
            /** Format: int32 */
            redactionRuleVersion: number;
            storage: components["schemas"]["StorageInformation"][];
            systemProperties: {
                [key: string]: string;
            };
        };
        UpdateAccessTokenWithNoNameRequest: {
            /** @description Human-readable description of the token. If not provided, the current description is kept */
            description?: string | null;
            /**
             * Format: date-time
             * @description Expiration timestamp (ISO-8601 Instant). If not provided, the current expiration is kept. A null value clears it
             */
            expiresAt?: string | null;
            /** @description Permissions assigned to the token: [MANAGER]. If not provided, the current permissions are kept */
            permissions?: string[] | null;
            /** @description Route permissions assigned to the token. If not provided, the current routes are kept */
            routes?: components["schemas"]["CreateAccessTokenWithNoNameRequestRoute"][] | null;
        };
        UpdatePrincipalRequest: {
            disabled?: boolean | null;
            displayName?: string | null;
        };
        UpdateTeamRequest: {
            description?: string | null;
            externalRef?: string | null;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export type operations = Record<string, never>;
