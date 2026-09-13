#!/usr/bin/env python3
"""Send an HTTP request with TCP_CORK so intermediate segments carry no PSH flag.

Linux bulk senders omit PSH on intermediate segments; the vswitch TCP stack used to
drop in-window non-PSH data entirely. This test verifies such data is now accepted.
"""
import socket
import sys

HOST, PORT = "169.254.99.254", 80

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(15)
s.setsockopt(socket.IPPROTO_TCP, socket.TCP_CORK, 1)
s.connect((HOST, PORT))

# request headers (no PSH yet due to cork)
s.sendall(b"GET /hello HTTP/1.1\r\nHost: x\r\n")
s.sendall(b"X-Padding: " + b"a" * 4000 + b"\r\n")  # spans several MSS, still corked (no PSH)
s.sendall(b"\r\n")
s.setsockopt(socket.IPPROTO_TCP, socket.TCP_CORK, 0)  # flush; last segment carries PSH

buf = b""
try:
    while b"world" not in buf:
        chunk = s.recv(4096)
        if not chunk:
            break
        buf += chunk
except socket.timeout:
    pass
print(buf.decode(errors="replace")[:200])
first_line = buf.split(b"\r\n")[0] if buf else b""
if b"200" in first_line and b"world" in buf:
    print("NON-PSH TEST: PASS")
else:
    print("NON-PSH TEST: FAIL")
    sys.exit(1)
