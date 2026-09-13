#!/bin/bash
# launcher for the SwitchTCP POC (run as root; creates a TUN device)
# usage: sudo bash run_swtcp.sh
# NOTE: the POC's Linux setup script does not bring the TUN interface up,
#       run `ip link set <tunDev> up` manually after it starts (see README.md)
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
DIR=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$DIR/../.." && pwd)
CP_FILE=${SWTCP_CP_FILE:-$HOME/swtcp_cp.txt}
if [ ! -f "$CP_FILE" ]; then
    # fall back to a user home (scripts are often run via sudo where $HOME is /root)
    CP_FILE=$(ls /home/*/swtcp_cp.txt 2>/dev/null | head -n 1)
fi

cd "$REPO" || exit 1
CP=$(tr -d '\n\r' < "$CP_FILE")
exec "$JAVA_HOME/bin/java" -enableassertions \
  -Djava.library.path=base/src/main/c \
  -Dvfd=posix \
  -cp "$CP" \
  io.vproxy.poc.SwitchTCP
