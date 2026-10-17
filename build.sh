#!/bin/sh
# Compila las clases modificadas y genera:
#   dist/chromispos.jar  (src/ + src-common/ sobre original/chromispos.jar)
#   dist/admin.jar       (src-admin/ + src-common/ sobre original-admin/admin.jar)
# Requiere JDK 11 o superior.
set -e
cd "$(dirname "$0")"
JAVAFX=$(ls tools/javafx-11.0.2/*.jar | tr '\n' ':')

# build_jar <nombre> <jar-original> <classpath-extra> <carpetas-fuente...>
build_jar() {
    name=$1; original=$2; extra=$3; shift 3
    out=build/$name
    rm -rf "$out"
    mkdir -p "$out/classes" dist
    find "$@" -name '*.java' > "$out/sources.txt"
    javac --release 11 -g -nowarn -encoding UTF-8 -proc:none \
        -cp "$original:$extra" -d "$out/classes" @"$out/sources.txt"
    # Recursos no-Java (fxml, xml, ...) se copian tal cual
    for dir in "$@"; do
        (cd "$dir" && find . -type f ! -name '*.java' -exec sh -c 'mkdir -p "$0/$(dirname "$1")" && cp "$1" "$0/$1"' "../$out/classes" {} \;)
    done
    java tools/PatchJar.java "$original" "$out/classes" "dist/$name.jar"
}

build_jar chromispos original/chromispos.jar "" src src-common
build_jar admin original-admin/admin.jar "$JAVAFX" src-admin src-common
