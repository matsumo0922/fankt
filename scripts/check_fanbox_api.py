#!/usr/bin/env python3

import argparse
import json
import stat
import sys
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urljoin, urlparse, urlunparse


API_BASE_URL = "https://api.fanbox.cc/"
FANBOX_ROOT_HOST = "fanbox.cc"
DEFAULT_SESSION_FILE = Path("/tmp/fankt-fanboxsessid")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check a FANBOX API endpoint without printing response data.",
    )
    parser.add_argument(
        "endpoint",
        help="API path or full URL under fanbox.cc.",
    )
    parser.add_argument(
        "--param",
        action="append",
        default=[],
        metavar="KEY=VALUE",
        help="Query parameter. May be specified more than once.",
    )
    parser.add_argument(
        "--session-file",
        type=Path,
        default=DEFAULT_SESSION_FILE,
        help=f"File containing only FANBOXSESSID (default: {DEFAULT_SESSION_FILE}).",
    )
    parser.add_argument(
        "--csrf-file",
        type=Path,
        help="Optional file containing an X-CSRF-Token value.",
    )
    parser.add_argument(
        "--impersonate",
        default="chrome136",
        help="curl_cffi browser fingerprint (default: chrome136).",
    )
    return parser.parse_args()


def load_secret(path: Path, name: str) -> str:
    if not path.is_file():
        raise ValueError(f"{name} file does not exist: {path}")

    mode = stat.S_IMODE(path.stat().st_mode)
    if mode & 0o077:
        raise ValueError(f"{name} file must have mode 600: {path} has mode {mode:o}")

    value = path.read_text(encoding="utf-8").strip()
    if not value:
        raise ValueError(f"{name} file is empty: {path}")

    return value


def parse_param(value: str) -> tuple[str, str]:
    key, separator, param_value = value.partition("=")
    if not separator or not key:
        raise ValueError(f"Invalid --param value: {value!r}; expected KEY=VALUE")

    return key, param_value


def build_url(endpoint: str, params: list[str]) -> str:
    url = urljoin(API_BASE_URL, endpoint)
    parsed = urlparse(url)
    hostname = parsed.hostname or ""
    is_fanbox_host = hostname == FANBOX_ROOT_HOST or hostname.endswith(
        f".{FANBOX_ROOT_HOST}",
    )
    if parsed.scheme != "https" or not is_fanbox_host:
        raise ValueError("Endpoint must use HTTPS on fanbox.cc or its subdomains")
    if parsed.username or parsed.password or parsed.fragment:
        raise ValueError("Endpoint must not contain credentials or a fragment")

    query = parse_qsl(parsed.query, keep_blank_values=True)
    query.extend(parse_param(param) for param in params)

    return urlunparse(parsed._replace(query=urlencode(query)))


def describe_json(payload: object) -> str:
    if not isinstance(payload, dict):
        return f"json_type={type(payload).__name__}"

    body = payload.get("body")
    body_keys = sorted(body) if isinstance(body, dict) else None
    return (
        f"top_level_keys={sorted(payload)} "
        f"body_type={type(body).__name__} body_keys={body_keys}"
    )


def import_requests():
    try:
        from curl_cffi import requests
    except ImportError as error:
        raise RuntimeError(
            "curl_cffi is required; install it with: python3 -m pip install curl_cffi",
        ) from error

    return requests


def main() -> int:
    args = parse_args()

    try:
        url = build_url(args.endpoint, args.param)
        session_id = load_secret(args.session_file, "FANBOXSESSID")
        csrf_token = load_secret(args.csrf_file, "CSRF token") if args.csrf_file else None
        requests = import_requests()
    except (RuntimeError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2

    session = requests.Session(impersonate=args.impersonate)
    session.cookies.set("FANBOXSESSID", session_id, domain=".fanbox.cc")
    session.headers.update(
        {
            "origin": "https://www.fanbox.cc",
            "referer": "https://www.fanbox.cc/",
        },
    )
    if csrf_token:
        session.headers["x-csrf-token"] = csrf_token

    try:
        response = session.get(url, allow_redirects=False)
    except requests.RequestsError as error:
        print(f"Request failed: {error}", file=sys.stderr)
        return 1
    finally:
        session.close()

    content_type = response.headers.get("content-type", "")
    print(f"status={response.status_code} content_type={content_type}")

    if "application/json" not in content_type:
        return 0 if 200 <= response.status_code < 300 else 1

    try:
        print(describe_json(response.json()))
    except (json.JSONDecodeError, ValueError) as error:
        print(f"Invalid JSON response: {error}", file=sys.stderr)
        return 1

    return 0 if 200 <= response.status_code < 300 else 1


if __name__ == "__main__":
    raise SystemExit(main())
