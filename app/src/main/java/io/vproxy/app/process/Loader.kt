package io.vproxy.app.process

import io.vproxy.app.app.cmd.ModuleCommands
import io.vproxy.app.app.cmd.SystemCommands
import io.vproxy.lib.common.awaitCallback
import io.vproxy.lib.common.defaultCoroutineEventLoop
import io.vproxy.lib.common.launch
import java.io.*

object Loader {
  fun loadCommands(filepath: String, cb: io.vproxy.base.util.callback.Callback<String, Throwable>) {
    val loop = if (io.vproxy.base.selector.SelectorEventLoop.current() == null) {
      defaultCoroutineEventLoop()
    } else {
      io.vproxy.base.selector.SelectorEventLoop.current()
    }
    loop.launch {
      try {
        loadCommandsAsync(filepath)
      } catch (t: Throwable) {
        cb.failed(t)
        return@launch
      }
      cb.succeeded("")
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun loadCommandsAsync(filepathx: String) {
    val filepath = io.vproxy.base.util.Utils.filename(filepathx)
    val f = File(filepath)
    val fis = FileInputStream(f)
    val br = BufferedReader(InputStreamReader(fis))
    val lines: MutableList<String> = ArrayList()
    while (true) {
      val l: String = br.readLine() ?: break
      lines.add(l)
    }
    fis.close()
    for (linex in lines) {
      var line = linex.trim()
      if (line.isEmpty()) { // skip empty lines
        continue
      }
      if (line.startsWith("#")) { // comment
        continue
      }
      val isSystemCommand = line.startsWith("System: ")

      if (isSystemCommand) {
        val subline = line.substring("System: ".length).trim()
        if (subline.startsWith("load ")) {
          val file = subline.substring("load ".length).trim()
          io.vproxy.base.util.Logger.alert("loading more commands from $file")
          loadCommandsAsync(file)
          continue
        } else if (subline.startsWith("exec ")) {
          val filename = subline.substring("exec ".length).trim()
          val file = File(filename)
          if (!file.exists()) {
            throw FileNotFoundException(filename)
          }
          if (!file.setExecutable(true)) {
            throw Exception("failed setting executable on $filename")
          }
          val pb = ProcessBuilder(filename)
          io.vproxy.base.util.Utils.execute(pb, 5 * 1000)
          continue
        }
      }

      assert(io.vproxy.base.util.Logger.lowLevelDebug(io.vproxy.base.util.LogType.ALERT.toString() + " - " + line))

      if (isSystemCommand) {
        line = line.substring("System: ".length)
      }
      val cmd = try {
        io.vproxy.app.app.cmd.Command.parseStrCmd(line)
      } catch (e: Exception) {
        io.vproxy.base.util.Logger.warn(io.vproxy.base.util.LogType.ALERT, "parse command `$line` failed")
        throw e
      }
      assert(io.vproxy.base.util.Logger.lowLevelDebug(io.vproxy.base.util.LogType.ALERT.toString() + " - " + cmd))
      executeCommand(isSystemCommand, cmd)
    }
  }

  private suspend fun executeCommand(isSystemCommand: Boolean, cmd: io.vproxy.app.app.cmd.Command) {
    if (isSystemCommand) {
      io.vproxy.base.util.Logger.alert("loading command: System: $cmd")
    } else {
      io.vproxy.base.util.Logger.alert("loading command: $cmd")
    }
    awaitCallback<io.vproxy.app.app.cmd.CmdResult, Throwable> {
      val cmds = if (isSystemCommand) {
        SystemCommands.Instance
      } else {
        ModuleCommands.Instance
      }
      cmd.run(cmds, it)
    }
  }
}
