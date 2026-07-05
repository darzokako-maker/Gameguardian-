#!/usr/bin/env sh

# Minimal gradlew script for GitHub Actions inside environments
DIRNAME=$(dirname "$0")
if [ -z "$DIRNAME" ]; then
    DIRNAME="."
fi
APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$DIRNAME" && pwd)

# Cihazdaki veya sunucudaki Java'yı bul
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    fi
fi

if [ -z "$JAVACMD" ] ; then
    JAVACMD="java"
fi

# Gradle jar dosyasını tetikle
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" -Xmx64m "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

