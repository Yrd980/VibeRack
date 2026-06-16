#!/usr/bin/env python3
"""Probe LCSC search page responses with standard application headers.

This script is for diagnostics only. It prints whether the response contains
the Next.js data payload currently expected by the Android parser.
"""

from __future__ import annotations

import argparse
import base64
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_HEADERS = {
    "User-Agent": "VibeRack/1.4.0 (Android; LCSC catalog probe)",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Cache-Control": "no-cache",
    "Pragma": "no-cache",
    "Referer": "https://so.szlcsc.com/",
    "Connection": "keep-alive",
}


VERIFICATION_TOKENS = ("_xvasu", "_xvtsc", "_xvpfs", "_xvpts")
VERIFICATION_KEY = "tg09It3*9h"


def build_url(keyword: str) -> str:
    query = urllib.parse.urlencode({"k": keyword})
    return f"https://so.szlcsc.com/global.html?{query}"


def read_response(
    keyword: str,
    timeout: float,
    extra_headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, str], str]:
    headers = DEFAULT_HEADERS.copy()
    if extra_headers:
        headers.update(extra_headers)

    request = urllib.request.Request(build_url(keyword), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            headers = dict(response.headers.items())
            body = response.read().decode("utf-8", errors="replace")
            return status, headers, body
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        return error.code, dict(error.headers.items()), body


def looks_like_verification_page(body: str) -> bool:
    return any(token in body for token in VERIFICATION_TOKENS)


def extract_js_var(body: str, name: str) -> str:
    match = re.search(rf"var\s+{re.escape(name)}\s*=\s*(.+?);", body)
    if not match:
        raise ValueError(f"missing verification variable: {name}")

    value = match.group(1).strip()
    quoted_match = re.fullmatch(r"""["'](.*)["']""", value)
    return quoted_match.group(1) if quoted_match else value


def rc4(key: str, value: str) -> bytes:
    key_bytes = key.encode("utf-8")
    value_bytes = value.encode("utf-8")
    state = list(range(256))
    index_b = 0

    for index_a in range(256):
        index_b = (index_b + state[index_a] + key_bytes[index_a % len(key_bytes)]) % 256
        state[index_a], state[index_b] = state[index_b], state[index_a]

    index_a = 0
    index_b = 0
    output = bytearray()
    for byte in value_bytes:
        index_a = (index_a + 1) % 256
        index_b = (index_b + state[index_a]) % 256
        state[index_a], state[index_b] = state[index_b], state[index_a]
        output.append(byte ^ state[(state[index_a] + state[index_b]) % 256])

    return bytes(output)


def build_verification_cookie(body: str) -> str:
    xvasu = extract_js_var(body, "_xvasu")
    xvpts = extract_js_var(body, "_xvpts")
    xvpfs = extract_js_var(body, "_xvpfs")
    cookie_name = f"{xvpfs}{xvasu}"
    cookie_value = base64.b64encode(rc4(VERIFICATION_KEY, f"{xvpts}:{xvasu}")).decode("ascii")
    return f"{cookie_name}={cookie_value}"


def read_response_with_verification(
    keyword: str,
    timeout: float,
) -> tuple[int, dict[str, str], str, str | None]:
    status, headers, body = read_response(keyword, timeout)
    if status != 203 or not looks_like_verification_page(body):
        return status, headers, body, None

    verification_cookie = build_verification_cookie(body)
    status, headers, body = read_response(
        keyword,
        timeout,
        extra_headers={"Cookie": verification_cookie},
    )
    return status, headers, body, verification_cookie


def main() -> int:
    parser = argparse.ArgumentParser(description="Probe LCSC search response shape.")
    parser.add_argument("keyword", nargs="?", default="rp2040", help="Search keyword.")
    parser.add_argument("--timeout", type=float, default=10.0, help="Request timeout seconds.")
    parser.add_argument("--preview", type=int, default=500, help="Body preview length.")
    args = parser.parse_args()

    try:
        status, headers, body, verification_cookie = read_response_with_verification(
            args.keyword,
            args.timeout,
        )
    except Exception as error:  # noqa: BLE001 - diagnostic script should print the exact failure.
        print(f"request_failed={type(error).__name__}: {error}", file=sys.stderr)
        return 2

    has_next_data = 'id="__NEXT_DATA__"' in body or "id='__NEXT_DATA__'" in body
    looks_like_verification = looks_like_verification_page(body)

    print(f"url={build_url(args.keyword)}")
    print(f"status={status}")
    print(f"content_type={headers.get('Content-Type', '')}")
    print(f"server={headers.get('Server', '')}")
    print(f"body_bytes={len(body.encode('utf-8'))}")
    print(f"has_next_data={has_next_data}")
    print(f"looks_like_verification={looks_like_verification}")
    print(f"verification_retry={verification_cookie is not None}")
    if verification_cookie is not None:
        cookie_name = verification_cookie.split("=", 1)[0]
        print(f"verification_cookie={cookie_name}=<redacted>")
    print("headers_sent:")
    for key, value in DEFAULT_HEADERS.items():
        print(f"  {key}: {value}")
    if verification_cookie is not None:
        print(f"  Cookie: {cookie_name}=<redacted>")
    print("body_preview:")
    print(body[: args.preview])

    return 0 if has_next_data else 1


if __name__ == "__main__":
    raise SystemExit(main())
