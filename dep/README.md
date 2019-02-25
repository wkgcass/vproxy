# dep

This directory contains dependencies required by vproxy. All files are copied from an opensource offical jdk or jre.  
They are not required if you are currently using a standard jre, but they might be if you are using graalvm native images.

### libsunec.so

Using graalvm rc12 linux

[graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz](https://github.com/oracle/graal/releases/download/vm-1.0.0-rc12/graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz)

```
shasum -a 1 graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz
24cb120af3978ec96a0dc360ba5c412c951dd54f  graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz

md5 graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz
MD5 (graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz) = 690d587d29e62c38380f13d03ea43a52
```

The file is copied from `./graalvm-ce-1.0.0-rc12/jre/lib/amd64/libsunec.so`

```
shasum -a 1 libsunec.so
b193da5cb0219194f98cda1360679f4db8f4540f  libsunec.so

md5 libsunec.so
MD5 (libsunec.so) = c72a1a62ac2acf1f40a59337dd5609ab
```

### libsunec.dylib

Using graalvm rc12 macos

[graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz](https://github.com/oracle/graal/releases/download/vm-1.0.0-rc12/graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz)

```
shasum -a 1 graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz
c780d0af0667d0c907611293dfe0f39b6f48cea1  graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz

md5 graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz
MD5 (graalvm-ce-1.0.0-rc12-macos-amd64.tar.gz) = f4d4c8278bdeac7dac28361c68791184
```

The file is copied from `./graalvm-ce-1.0.0-rc12/Contents/Home/jre/lib/libsunec.dylib`

```
shasum -a 1 libsunec.dylib
751a582efd3ed32f1391160f30753e8507e9db48  libsunec.dylib

md5 libsunec.dylib
MD5 (libsunec.dylib) = 968834adf6e1d1e0a641dfb269b19278
```

### cacerts

Using graalvm rc12 linux

[graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz](https://github.com/oracle/graal/releases/download/vm-1.0.0-rc12/graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz)

```
shasum -a 1 graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz
24cb120af3978ec96a0dc360ba5c412c951dd54f  graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz

md5 graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz
MD5 (graalvm-ce-1.0.0-rc12-linux-amd64.tar.gz) = 690d587d29e62c38380f13d03ea43a52
```

The file is copied from `./graalvm-ce-1.0.0-rc12/jre/lib/security/cacerts`

```
shasum -a 1 cacerts
4d1fc3c4d5d993fec42b5c09e2a6d2a57bd8ae19  cacerts

md5 cacerts
MD5 (cacerts) = 94db07aa60847ca84a19bd98c3af1735
```
