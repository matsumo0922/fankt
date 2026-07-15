---
name: check-fanbox-api
description: Safely check pixivFANBOX API and download endpoints with a browser-like curl fingerprint. Use when investigating FANBOX connectivity, authentication, Cloudflare 403 responses, post.info failures, FANBOXSESSID validity, or responses from api.fanbox.cc and other fanbox.cc hosts.
---

# Check FANBOX API

Use `scripts/check_fanbox_api.py` from the repository root. The script sends a
single GET request through `curl_cffi`, never prints credentials or response
bodies, and reports the status, Content-Type, and JSON shape when applicable.

## Safety rules

- Read `FANBOXSESSID` from a mode `600` file. Never put it directly in a command.
- Never print, log, hash, or include the secret value in a report.
- Send only the request the user asked for. Do not probe preliminary endpoints.
- Do not retry with another fingerprint or standard `curl` without permission.
- Prefer the default `chrome136` fingerprint. Standard macOS `curl` may receive
  a Cloudflare challenge even when the same session succeeds with `curl_cffi`.
- Treat the session as sensitive even when the user says it is for testing.

## Run a check

Use a path for an endpoint under `https://api.fanbox.cc/`:

```bash
scripts/check_fanbox_api.py post.info \
  --param postId=12244258
```

Use a full URL for another FANBOX host or when the URL already has a query:

```bash
scripts/check_fanbox_api.py \
  'https://downloads.fanbox.cc/path/to/file?size=original' \
  --param download=1
```

Repeat `--param KEY=VALUE` to append query parameters. Existing query parameters
remain intact. Use `--session-file` when the session is not stored at the default
`/tmp/fankt-fanboxsessid` path.

For an endpoint that requires a CSRF token, pass another mode `600` file:

```bash
scripts/check_fanbox_api.py some.endpoint \
  --csrf-file /tmp/fankt-fanboxcsrf
```

Use `--impersonate PROFILE` only when the user explicitly requests another
fingerprint.

## Install the dependency

If `curl_cffi` is unavailable, ask before installing it. Keep the dependency out
of the repository:

```bash
python3 -m pip install \
  --target /tmp/fankt-fanbox-api-check/deps \
  curl_cffi
```

Then prefix checks with:

```bash
PYTHONPATH=/tmp/fankt-fanbox-api-check/deps \
scripts/check_fanbox_api.py post.info --param postId=12244258
```

## Interpret results

- `2xx`: The endpoint is reachable. For JSON, verify the reported key shape.
- `401 application/json`: The request reached FANBOX, but authentication failed.
- `403 application/json`: FANBOX rejected access or the resource is unavailable
  to the authenticated account.
- `403 text/html` with a Cloudflare page: The edge challenge rejected the client;
  this alone does not prove that `FANBOXSESSID` is invalid.
- Non-JSON `2xx`: A download or page request succeeded; do not print its body.

Report the exact request count, fingerprint, status, Content-Type, and whether
JSON structure was present. Keep credentials and response content out of the
report.
