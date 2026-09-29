#!/bin/sh
# Compiles the pure-Java rail/train core and runs the headless simulation tests.
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
javac -d "$OUT" src/main/java/com/ficsitcraft/rail/*.java src/main/java/com/ficsitcraft/train/*.java tools/test/RailSim.java
java -cp "$OUT" RailSim
