#!/bin/bash
MAVEN_VERSION=3.9.14
WRAPPER_DIR="$(dirname "$0")/.mvn/wrapper"
MAVEN_DIR="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_URL="https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -f "$MAVEN_DIR/bin/mvn" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    mkdir -p "$WRAPPER_DIR"
    curl -sL "$MAVEN_URL" -o "$WRAPPER_DIR/maven.zip"
    echo "Extracting..."
    unzip -q "$WRAPPER_DIR/maven.zip" -d "$WRAPPER_DIR"
    rm "$WRAPPER_DIR/maven.zip"
    echo "Maven ready!"
fi

"$MAVEN_DIR/bin/mvn" "$@"
