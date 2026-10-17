#!/bin/sh
# Compila las clases modificadas de src/ contra el chromispos.jar original
# y genera dist/chromispos.jar. Requiere JDK 11 o superior.
set -e
cd "$(dirname "$0")"
rm -rf build
mkdir -p build/classes dist
find src -name '*.java' > build/sources.txt
javac --release 11 -g -nowarn -encoding UTF-8 -proc:none \
    -cp original/chromispos.jar -d build/classes @build/sources.txt
# Recursos no-Java dentro de src/ (si los hay) se copian tal cual
(cd src && find . -type f ! -name '*.java' -exec sh -c 'mkdir -p "../build/classes/$(dirname "$1")" && cp "$1" "../build/classes/$1"' _ {} \;)
java tools/PatchJar.java original/chromispos.jar build/classes dist/chromispos.jar
