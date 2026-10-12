#!/bin/sh
# Minimal Maven wrapper: downloads the Maven version pinned in .mvn/wrapper/maven-wrapper.properties
# (once, into $MAVEN_USER_HOME or ~/.m2/wrapper), verifies it, and runs it. Needs only sh, curl or wget, tar.
# To switch to Apache's official wrapper at any time run:  mvn wrapper:wrapper -Dmaven=3.9.11
set -eu

BASE_DIR=$(cd "$(dirname "$0")" && pwd -P)
PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPS" ] || { echo "mvnw: missing $PROPS" >&2; exit 1; }

prop() { sed -n "s/^$1=//p" "$PROPS" | tail -n 1 | tr -d '\r'; }
URL=$(prop distributionUrl)
PIN=$(prop distributionSha256Sum)
[ -n "$URL" ] || { echo "mvnw: distributionUrl is not set" >&2; exit 1; }

NAME=$(basename "$URL" .tar.gz)
NAME=${NAME%-bin}
KEY=$(printf '%s' "$URL" | cksum | cut -d' ' -f1)
DIST_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/$NAME/$KEY"
MVN="$DIST_HOME/$NAME/bin/mvn"

fetch() { # url dest
  if command -v curl >/dev/null 2>&1; then curl -fsSL --retry 3 -o "$2" "$1"
  elif command -v wget >/dev/null 2>&1; then wget -q -O "$2" "$1"
  else echo "mvnw: curl or wget is required to download Maven" >&2; exit 1; fi
}

if [ ! -x "$MVN" ]; then
  echo "mvnw: downloading $NAME ..." >&2
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT INT TERM
  fetch "$URL" "$TMP/maven.tar.gz"
  if [ -n "$PIN" ]; then
    ACTUAL=$( (sha256sum "$TMP/maven.tar.gz" 2>/dev/null || shasum -a 256 "$TMP/maven.tar.gz") | cut -d' ' -f1)
    [ "$ACTUAL" = "$PIN" ] || { echo "mvnw: SHA-256 mismatch for downloaded Maven" >&2; exit 1; }
  else
    fetch "$URL.sha512" "$TMP/maven.sha512"
    EXPECTED=$(cut -d' ' -f1 "$TMP/maven.sha512" | tr -d '\r\n ')
    ACTUAL=$( (sha512sum "$TMP/maven.tar.gz" 2>/dev/null || shasum -a 512 "$TMP/maven.tar.gz") | cut -d' ' -f1)
    [ "$ACTUAL" = "$EXPECTED" ] || { echo "mvnw: SHA-512 mismatch for downloaded Maven" >&2; exit 1; }
  fi
  mkdir -p "$DIST_HOME"
  tar -xzf "$TMP/maven.tar.gz" -C "$DIST_HOME"
  [ -x "$MVN" ] || { echo "mvnw: unexpected archive layout, $MVN not found" >&2; exit 1; }
fi

exec "$MVN" "$@"
