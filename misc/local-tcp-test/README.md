# vswitch TCP 栈本地测试

针对 `core/src/main/java/io/vproxy/vswitch/node/TcpStack.java`（vswitch TCP 栈节点）
的 POC 驱动本地集成测试：非 PSH 数据接收、大流量内容完整性。

测试基于 `test/src/test/java/io/vproxy/poc/SwitchTCP.kt` POC：它在 vswitch 的
用户态 TCP 栈上启动一个 HTTP 服务，通过 TUN 设备对外提供
`169.254.99.254:80`（IPv4）与 `[fd00::99:fe]:80`（IPv6）两个端点。

## 一、环境布置

以下步骤在 WSL（`wsl -d Ubuntu-WSL2`）内执行，Windows 侧无需安装任何东西。

### 1. 安装 JDK 22

项目 `sourceCompatibility = '22'`，且自带 gradle 8.8 **不支持 JDK 25+**
（会报 `Unsupported class file major version 69`），apt 源里又没有 22，
所以从 Adoptium 下载解压：

```bash
curl -fsSL -o /tmp/jdk22.tar.gz \
  'https://api.adoptium.net/v3/binary/latest/22/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk'
mkdir -p ~/jdks && tar -xzf /tmp/jdk22.tar.gz -C ~/jdks
# 把 jdk 的 bin 目录放进 PATH（脚本通过 `which java` 反推 JAVA_HOME），
# 例如在 ~/.profile 中加入：
#   export PATH="$HOME/jdks/jdk-22.0.2+9/bin:$PATH"
```

### 2. 构建并提取 POC 运行时 classpath

```bash
cd /mnt/d/wsl-workspace/opensource/vproxy
bash misc/local-tcp-test/prepare_classpath.sh
```

该脚本做两件事：

1. 构建 classpath 所引用的 `:base:jar` 和 `:core:jar`
   （注意：**只跑 `compileJava` 不够**，classpath 里是 jar 不是 classes 目录，
   代码改动后必须重新执行本脚本，否则跑的是旧字节码）；
2. 通过临时 gradle init 脚本把 `SwitchTCP` 任务的运行时 classpath
   写入 `~/swtcp_cp.txt`。

预期输出：

```
classpath with 87 entries written to /home/<user>/swtcp_cp.txt
```

PNI 原生库（`base/src/main/c/libpni.so`）仓库里已预编译好 Linux x64 版本，
无需额外构建。

### 3. 权限要求

启动 POC 需要 root（创建 TUN 设备、配置 IP）。WSL 默认用户可用 `sudo`。
**测试客户端（curl / python）不需要 root。**

## 二、脚本说明

| 文件 | 作用 | 需要环境 |
|---|---|---|
| `prepare_classpath.sh` | 构建 base/core jar 并提取 POC classpath | JDK 22 + gradle |
| `run_swtcp.sh` | 以 root 启动 SwitchTCP POC（前台运行） | root，先跑过 prepare |
| `test_nonpsh.py` | 用 `TCP_CORK` 让内核发出不带 PSH 的中间分片，验证非 PSH 数据被正确接收 | POC 已启动 |

## 三、使用方法与预期输出

### 1. 启动 POC

```bash
# 终端 1（root 后台启动，日志在 /root/swtcp.log）
sudo bash -c 'nohup bash misc/local-tcp-test/run_swtcp.sh > /root/swtcp.log 2>&1 &'
sleep 10
# POC 的 Linux 配置脚本缺少 up 操作，TUN 设备起来后需要手动拉起
sudo ip link set tun17 up     # 设备名一般是 tun17，以日志里的 "tun device added: tunXX" 为准
```

验证存活（预期输出 `world`）：

```bash
curl -s http://169.254.99.254/hello
curl -s "http://[fd00::99:fe]/hello"
```

### 2. 基础功能 + 内容完整性（curl）

```bash
rm -f /tmp/large
curl -s -o /tmp/large http://169.254.99.254/large   # 下载 30MB，约 30s
ls -l /tmp/large                                     # 31457280 字节
curl -s -X POST http://169.254.99.254/validate --data-binary @/tmp/large
```

预期最后一行输出 `OK`（30MB 字节级校验，往返双向都过 TCP 栈）。
本机环境吞吐约 0.9~1.0 MB/s（用户态栈 + TUN 的固有水平，非瓶颈判定依据）。

### 3. 非 PSH 数据接收

```bash
python3 misc/local-tcp-test/test_nonpsh.py
```

预期输出：

```
HTTP/1.1 200 OK
content-length: 7

world

NON-PSH TEST: PASS
```

## 四、停止与清理

推荐直接杀进程：

```bash
sudo pkill -9 -f SwitchTCP
```

（不要用 `pkill -f "jdks/jdk-22.0.2+9/bin/java"` 之类的模式：`+` 在 pkill
的正则里是量词，匹配不到字面路径。）

进程退出后 TUN 设备（tunXX）由内核自动销毁。

也可以让 POC 自己退（回复后等 N 秒退出）：

```bash
curl -s -X POST http://169.254.99.254/exit --data '1'
```

注意：`/exit` 会在 event loop 线程内调用 `exitProcess`，实测 TUN 设备会被
销毁、但 JVM 有可能僵住不完全退出，此时仍需上面的 `pkill` 收尾。

## 五、注意事项

- **改了 base/core 代码后**，必须重跑 `prepare_classpath.sh` 再测，否则
  跑的是旧 jar（gradle 的 `compileJava` 不会重建 jar）。
- WSL 重启会清空 `/tmp`，`~/swtcp_cp.txt` 在家目录不受影响；
  若家目录文件丢失，重跑 `prepare_classpath.sh` 即可。
- 脚本会优先使用环境变量 `JAVA_HOME`，否则用 `which java`（经 `readlink -f`）
  反推 JDK 根目录，因此需要 java 在 PATH 上；
  classpath 文件默认 `$HOME/swtcp_cp.txt`，可用 `SWTCP_CP_FILE=...` 覆盖。
- `sudo` 运行时（如 `run_swtcp.sh`）sudo 的 secure_path 通常看不到用户 PATH
  里的 java，需显式传递：
  `sudo env JAVA_HOME="$JAVA_HOME" bash misc/local-tcp-test/run_swtcp.sh`。
- POC 会自动寻找未被占用的 tun 设备名（tun17、tun18……），
  `ip link set` 时以启动日志里的 `tun device added: tunXX` 为准。
- 从 Windows 侧 Git Bash 调用 wsl 时，`/mnt/...` 之类的路径参数会被
  MSYS 转换破坏，建议统一用 `wsl -d Ubuntu-WSL2 -- bash -c '...'` 包裹。
