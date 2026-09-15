#!/bin/sh
# Compiles and runs the mock API server. No dependencies beyond a JDK.
#
# This machine's default `java`/`javac` on PATH may be very old (this repo has hit that
# before) — if you have a working JDK 17+ elsewhere (e.g. Android Studio ships one),
# point JAVA_HOME at it before running this script:
#   JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./run.sh
set -e
cd "$(dirname "$0")"

JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

mkdir -p out
"$JAVAC" -d out src/mockserver/*.java
exec "$JAVA" -cp out mockserver.Main "$@"
