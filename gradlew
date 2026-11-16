#!/usr/bin/env sh
# FHASIX local Gradle bootstrap for Linux / macOS / Android Termux.
# Uses the official Gradle 9.8.2 binary distribution and caches it under GRADLE_USER_HOME.
set -eu

GRADLE_VERSION="9.8.2"
DIST_NAME="gradle-${GRADLE_VERSION}-bin.zip"
DIST_URL="https://services.gradle.org/distributions/${DIST_NAME}"
GHOME="${GRADLE_USER_HOME:-${HOME}/.gradle}"
CACHE_ROOT="${GHOME}/wrapper/dists/fhasix-gradle-${GRADLE_VERSION}"
GRADLE_HOME_LOCAL="${CACHE_ROOT}/gradle-${GRADLE_VERSION}"

if [ ! -x "${GRADLE_HOME_LOCAL}/bin/gradle" ]; then
  for cmd in curl unzip sha256sum; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      echo "Missing required command: $cmd" >&2
      echo "On Android Termux, install it with: pkg install curl unzip coreutils" >&2
      exit 2
    fi
  done
  mkdir -p "$CACHE_ROOT"
  TMP_DIR="$(mktemp -d "${CACHE_ROOT}/download.XXXXXX")"
  trap 'rm -rf "$TMP_DIR"' EXIT HUP INT TERM
  echo "Downloading official Gradle ${GRADLE_VERSION} (first run only; about 130 MB)..."
  curl --fail --location --retry 3 --retry-delay 2 "$DIST_URL" -o "${TMP_DIR}/${DIST_NAME}"
  echo "Verifying Gradle distribution checksum..."
  EXPECTED="$(curl --fail --location --retry 3 --retry-delay 2 "${DIST_URL}.sha256" | tr -d '\r\n ' )"
  ACTUAL="$(sha256sum "${TMP_DIR}/${DIST_NAME}" | awk '{print $1}')"
  if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "ERROR: Gradle distribution checksum verification failed; refusing to run it." >&2
    exit 3
  fi
  unzip -q "${TMP_DIR}/${DIST_NAME}" -d "$TMP_DIR"
  mv "${TMP_DIR}/gradle-${GRADLE_VERSION}" "$GRADLE_HOME_LOCAL"
  rm -rf "$TMP_DIR"
  trap - EXIT HUP INT TERM
fi

exec "${GRADLE_HOME_LOCAL}/bin/gradle" "$@"
