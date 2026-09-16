#!/usr/bin/env bash
# End-to-end test of the vpws agent direct-relay tun (userspace stack) mode.
#
# Flow: start the agent -> configure the host side of the tun device ->
#       direct-access control -> swap system dns -> proxied access test ->
#       always roll back (resolv.conf / tun device / agent process).
#
# Usage (inside WSL, as root):
#   sudo bash misc/local-tcp-test/run_vpws_agent_test.sh
# Env overrides:
#   JAVA_BIN   jdk path (default: /home/*/jdks/jdk-*/bin/java, else java on PATH)
#   TEST_URL   default https://www.youtube.com/
set -u

REPO_ROOT="$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)"
CONF="$REPO_ROOT/misc/local-tcp-test/vpws-agent-tun.conf"
JAR="$REPO_ROOT/build/libs/vproxy.jar"
NATIVE="$REPO_ROOT/base/src/main/c"
LOG=/root/vpws-agent.log

# host side config, keep in sync with the agent conf file
HOST_IP=100.64.0.1/10
DNS_IP=100.64.0.53
TEST_URL="${TEST_URL:-https://www.youtube.com/}"

# the backup lives at a fixed path so that a next run can self-heal
# leftovers of a run that could not roll back (e.g. killed by SIGKILL)
RESOLV_BAK=/root/.vpws-agent-test-resolv.conf.bak

if [[ $EUID -ne 0 ]]; then
  echo "ERROR: must run as root (tun device and /etc/resolv.conf are manipulated)" >&2
  exit 1
fi
if [[ ! -f $CONF ]]; then
  echo "ERROR: config not found: $CONF" >&2
  echo "       copy vpws-agent-tun.conf.example and fill in your server/auth first" >&2
  exit 1
fi
if [[ ! -f $JAR ]]; then
  echo "ERROR: jar not found: $JAR (build it with './gradlew shadowJar' first)" >&2
  exit 1
fi

# locate a jdk
if [[ -z ${JAVA_BIN:-} ]]; then
  for j in /home/*/jdks/jdk-*/bin/java; do
    if [[ -x $j ]]; then JAVA_BIN=$j; break; fi
  done
fi
JAVA_BIN="${JAVA_BIN:-$(command -v java || true)}"
if [[ -z $JAVA_BIN ]]; then
  echo "ERROR: no java found, set JAVA_BIN" >&2
  exit 1
fi

# self-heal leftovers of a previous run that could not roll back (e.g. killed by SIGKILL)
if [[ -f $RESOLV_BAK ]]; then
  echo "[heal] leftover resolv.conf backup from a previous run found, rolling back now"
  cp "$RESOLV_BAK" /etc/resolv.conf
  rm -f "$RESOLV_BAK"
  pkill -9 -f "[v]proxy.jar"
  for d in $(ip -o link show | grep -oP '^[0-9]+: \Ktun[0-9]+' || true); do
    ip link delete "$d"
  done
fi

cp /etc/resolv.conf "$RESOLV_BAK"
TUN_DEV=tun0

cleanup() {
  echo "[cleanup] restoring resolv.conf, removing tun device, stopping agent ..."
  cp "$RESOLV_BAK" /etc/resolv.conf
  ip link delete "$TUN_DEV" 2>/dev/null
  # the bracket pattern avoids matching this script's own command line
  pkill -9 -f "[v]proxy.jar"
  rm -f "$RESOLV_BAK"
}
trap cleanup EXIT
# signals cannot run the cleanup directly and then continue the script;
# turn them into an exit so that the EXIT trap does the rollback exactly once
trap 'exit 130' INT
trap 'exit 143' TERM

echo "[test] direct access (control):"
DIRECT="$(curl -s -o /dev/null --max-time 8 -w "%{http_code} in %{time_total}s" "$TEST_URL" || true)"
echo "  $DIRECT"
if [[ $DIRECT == 200* ]]; then
  echo "  (note: direct access works in this network, the control is inconclusive)"
else
  echo "  direct access failed (expected in blocked networks)"
fi

echo "[run] starting agent (log: $LOG) ..."
pkill -9 -f "[v]proxy.jar"
sleep 1
setsid nohup "$JAVA_BIN" -Deploy=WebSocksProxyAgent -Dvfd=posix \
  -Djava.library.path="$NATIVE" -jar "$JAR" "$CONF" > "$LOG" 2>&1 < /dev/null &

# wait until the relay server is up (max ~20s)
READY=""
for _ in $(seq 1 40); do
  if grep -q "relay-bind-any-port-server started on the userspace stack" "$LOG" 2>/dev/null; then
    READY=1
    break
  fi
  sleep 0.5
done
if [[ -z $READY ]]; then
  echo "ERROR: agent failed to start, last log lines:" >&2
  tail -20 "$LOG" >&2
  exit 1
fi
TUN_DEV="$(grep -oP 'tun device added: \Ktun[0-9]+' "$LOG" | head -1)"
echo "[run] agent ready, tun device: $TUN_DEV"

ip addr add "$HOST_IP" dev "$TUN_DEV" 2>/dev/null
ip link set dev "$TUN_DEV" up

# the stack resolves the tun peer via arp-over-icmp, which drops the first
# return packet by design; ping the dns ip first to warm up the arp entry
echo "[warmup] ping the dns ip to learn the tun arp entry:"
ping -w 2 -c 5 "$DNS_IP" 2>&1 | tail -2 || true

echo "[test] proxied access (system dns -> $DNS_IP):"
echo "nameserver $DNS_IP" > /etc/resolv.conf
CODE="$(curl -s -o /dev/null --max-time 60 -w "%{http_code}" "$TEST_URL")"
echo "  ${CODE}"
if [[ $CODE == 200 ]]; then
  echo "RESULT: PASS"
else
  echo "RESULT: FAIL (see $LOG for dns/proxy logs)"
  exit 1
fi
