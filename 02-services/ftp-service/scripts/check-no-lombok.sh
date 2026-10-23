#!/usr/bin/env bash
# CI guard (Shared Standards B.2). Run from the module directory.
set -u
if mvn -q dependency:tree | grep -i lombok; then
  echo "FAIL: Lombok found in the dependency tree"; exit 1
fi
if grep -rIl --include='*.java' --include='pom.xml' -i 'lombok' src pom.xml | grep -v ArchitectureRulesTest | grep -v '^pom.xml$'; then
  echo "FAIL: Lombok referenced in source"; exit 1
fi
echo "OK: no Lombok"
