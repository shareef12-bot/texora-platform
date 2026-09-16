#!/usr/bin/env bash
# Run from any service module directory. Fails the build if Lombok is present.
set -e
if mvn dependency:tree | grep -qi lombok; then
  echo "FAIL: Lombok detected in dependency tree. Not allowed in this codebase."
  exit 1
fi
echo "OK: no Lombok."
