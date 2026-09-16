# AGENTS.md

## 构建与测试

- 需要 JDK 22（`sourceCompatibility = 22`）。
- Windows 上建议在 Git Bash 中执行 `./gradlew`。

### 必须使用 --no-daemon

本机环境下 gradle daemon 会卡死（症状：构建长时间无输出、不结束）。
所有 gradle 命令都加 `--no-daemon`。如果已经卡住，先执行 `./gradlew --stop`
停掉 daemon，再用 `--no-daemon` 重跑。参考耗时：增量编译 + 单测约 15~40 秒，
超过 5 分钟无输出即可判定为卡死。

### 编译

```bash
# 编译某个模块（base / core / extended / lib / app ...）
./gradlew --no-daemon :extended:compileJava

# 编译测试代码（会连带编译所有依赖模块）
./gradlew --no-daemon :test:compileTestJava
```

### 跑测试

```bash
# 跑单个测试类
./gradlew --no-daemon :test:runSingleTest -Dcase=io.vproxy.test.cases.TestSwitch

# 只跑单个方法（可选）
./gradlew --no-daemon :test:runSingleTest -Dcase=io.vproxy.test.cases.TestSwitch -Dmethod=transparentIpRange

# 跑全部测试（runTest 会先 clean，较慢；也可单独跑 runSuite 或 runCI）
./gradlew --no-daemon :test:runTest
```

注意：

- 不要用 `./gradlew :test:test --tests Xxx`。`test` 任务的 filter 固定为
  `io.vproxy.test.VSuite` 和 `io.vproxy.ci.CI`，`--tests` 与它冲突会报
  "No tests found for given includes"，单测请用上面的 `runSingleTest`。
- 新增测试类需要注册进 `test/src/test/java/io/vproxy/test/VSuite.java`，
  否则 `runSuite` / `runTest` 不会执行它。
