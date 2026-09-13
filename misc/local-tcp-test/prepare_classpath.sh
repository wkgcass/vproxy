#!/bin/bash
# one-time (or after each rebuild) preparation:
# 1. build the jars the POC classpath refers to (base + core)
# 2. extract the SwitchTCP POC runtime classpath into ~/swtcp_cp.txt
#    via a temporary gradle init script
# usage: JAVA_HOME=/path/to/jdk22 bash prepare_classpath.sh
set -e
# resolve JAVA_HOME: env var, or derive it from the java binary found on PATH
if [ -z "$JAVA_HOME" ]; then
    JAVA_BIN=$(command -v java 2>/dev/null)
    if [ -n "$JAVA_BIN" ]; then
        JAVA_BIN=$(readlink -f "$JAVA_BIN")
        JAVA_HOME=${JAVA_BIN%/bin/java}
    fi
fi
if [ -z "$JAVA_HOME" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "java not found: set JAVA_HOME or put a jdk on PATH; see README.md" >&2
    exit 1
fi
export JAVA_HOME
DIR=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$DIR/../.." && pwd)
CP_FILE=${SWTCP_CP_FILE:-$HOME/swtcp_cp.txt}

cat > /tmp/printcp.gradle <<'EOF'
gradle.projectsEvaluated {
    gradle.rootProject.getTasksByName("SwitchTCP", true).each { t ->
        println "SWITCHTCP_CLASSPATH=" + t.classpath.asPath
    }
}
EOF

cd "$REPO"
./gradlew :base:jar :core:jar --no-daemon -q
./gradlew --init-script /tmp/printcp.gradle help -q --no-daemon 2>/dev/null \
    | grep SWITCHTCP_CLASSPATH | head -1 | sed 's/^SWITCHTCP_CLASSPATH=//' > "$CP_FILE"

ENTRIES=$(tr ':' '\n' < "$CP_FILE" | wc -l)
if [ "$ENTRIES" -lt 10 ]; then
    echo "classpath extraction failed (only $ENTRIES entries), aborting" >&2
    exit 1
fi
echo "classpath with $ENTRIES entries written to $CP_FILE"
