# credential-safe-request-execution Specification

## Purpose
TBD - created by archiving change replace-ktorfit-with-request-descriptors. Update Purpose after archive.
## Requirements
### Requirement: Descriptor execution uses a trusted endpoint policy
The Ktor executor SHALL resolve the base origin and allowed method from a host-owned policy keyed by endpoint ID. It SHALL accept only HTTPS requests whose resolved origin is the exact policy origin, whose method equals the policy method, and whose relative path cannot supply a scheme, authority, userinfo, query, or fragment outside the descriptor fields.

#### Scenario: Allowed API descriptor is executed
- **WHEN** a known API endpoint descriptor uses its allowed method and a valid relative path
- **THEN** the executor resolves it against `https://api.fanbox.cc`, encodes its query values, and starts one request

#### Scenario: Homepage descriptor uses the WWW root
- **WHEN** the homepage endpoint descriptor with an empty relative path is executed as GET
- **THEN** the executor accepts that endpoint-specific empty path and sends exact `GET https://www.fanbox.cc/`

#### Scenario: Other empty paths are rejected
- **WHEN** any endpoint other than homepage supplies an empty relative path
- **THEN** execution fails before reading credentials or starting transport work

#### Scenario: Unknown endpoint is rejected
- **WHEN** a descriptor contains an endpoint ID absent from the trusted policy
- **THEN** execution fails before reading credentials or starting transport work

#### Scenario: Method mismatch is rejected
- **WHEN** a descriptor method differs from the method allowed for its endpoint ID
- **THEN** execution fails before reading credentials or starting transport work

#### Scenario: Path cannot replace the trusted origin
- **WHEN** a descriptor path attempts to contain a scheme, authority, userinfo, query, fragment, or another origin
- **THEN** execution fails before reading credentials or starting transport work

### Requirement: Credentials are attached only after validation
The executor SHALL validate the complete request destination and method before resolving cookies or the current CSRF token. For an accepted request it SHALL preserve domain-matched Cookie behavior, request-time CSRF resolution, and existing default FANBOX headers.

#### Scenario: Rejected descriptor cannot observe credentials
- **WHEN** endpoint, origin, method, or path validation fails
- **THEN** neither `FanboxCookieStorage` nor `FanboxTokenStore` is read and no credential-bearing request is created

#### Scenario: Accepted POST uses the latest token
- **WHEN** a valid POST descriptor starts after a CSRF token update completes
- **THEN** the request contains the current token and domain-matched cookies

#### Scenario: Accepted GET preserves authentication
- **WHEN** a valid authenticated GET descriptor is executed
- **THEN** the request contains the same matching cookies and default origin, referer, and user-agent headers as the existing client path

### Requirement: Redirects cannot escape the trusted policy
Descriptor execution SHALL disable unvalidated automatic redirects. GET redirect targets and resulting methods SHALL be revalidated against the original endpoint policy before a redirected request is sent or credentials are attached. Every POST redirect with status 301, 302, 307, or 308 SHALL be rejected before a second credential lookup or request send.

#### Scenario: Cross-origin redirect is rejected
- **WHEN** an allowed descriptor response redirects to an origin outside its endpoint policy
- **THEN** the redirect is rejected before a redirected request or credential lookup occurs

#### Scenario: Redirect method violates policy
- **WHEN** a GET redirect would result in a method that does not equal the endpoint policy method
- **THEN** the redirect is rejected before the next request is sent

#### Scenario: POST redirect is rejected
- **WHEN** an accepted POST receives a 301, 302, 307, or 308 redirect response
- **THEN** the redirect is rejected before a second credential lookup or request is sent

### Requirement: One executor owns normal FANBOX HTTP execution
All descriptor-based API and homepage requests SHALL pass through one owned executor and its raw-response path. The executor SHALL use one raw-body request client with common default headers and SHALL NOT require endpoint-specific ContentNegotiation client variants. The download streaming client SHALL remain a separate bounded path because it consumes arbitrary allowlisted media URLs incrementally.

#### Scenario: Repository operation uses the executor
- **WHEN** any non-download public `Fanbox` operation requires network access
- **THEN** its repository builds one descriptor, executes it through the shared executor, and passes the raw response to the endpoint parser

#### Scenario: Raw execution needs no ContentNegotiation variant
- **WHEN** GET and POST descriptors are executed
- **THEN** both use the same raw-body client and preserve the common origin, referer, user-agent, Cookie, and CSRF configuration before endpoint-specific parsing

#### Scenario: Mixed migration clients share authentication state
- **WHEN** generated APIs and the descriptor executor coexist during migration
- **THEN** every client uses the same injected Cookie storage adapter and CSRF token provider so metadata refreshes and session changes are immediately visible across both paths

#### Scenario: Download remains independent
- **WHEN** `Fanbox.download` streams an API-provided media URL
- **THEN** it continues to use the authenticated media download path rather than converting the URL into a request descriptor
