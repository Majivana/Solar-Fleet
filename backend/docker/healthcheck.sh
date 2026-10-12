#!/usr/bin/env bash
# Container health probe without curl: asks the readiness endpoint over a raw TCP connection.
set -e
exec 3<>/dev/tcp/127.0.0.1/${PORT:-8080}
printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
grep -q '"status":"UP"' <&3
