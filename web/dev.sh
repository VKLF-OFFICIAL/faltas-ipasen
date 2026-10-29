#!/usr/bin/env bash
# Prepara web/ para servirla en local: compila shared (Kotlin/JS) y copia los lectores.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
cd "$ROOT"
./gradlew -q :shared:jsBrowserProductionLibraryDistribution
mkdir -p web/shared web/readers
cp -r shared/build/dist/js/productionLibrary/. web/shared/
cp scraper/readers/readers.mjs web/readers/
echo "Listo. Sirve web/ con: npx --yes http-server web -p 8000 -c-1"
