package io.vproxy.app.process

import io.vproxy.app.app.cmd.CmdResult
import io.vproxy.app.app.cmd.Command
import vproxy.app.app.cmd.ModuleCommands
import vproxy.app.app.cmd.SystemCommands
import io.vproxy.base.selector.SelectorEventLoop
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.Utils
import io.vproxy.base.util.callback.Callback
import vproxy.lib.common.awaitCallback
import vproxy.lib.common.defaultCoroutineEventLoop
import vproxy.lib.common.launch
import java.io.*

object Loader {
  fun loadCommands(filepath: String, cb: _root_ide_package_.io.vproxy.base.util.callback.Callback<String, Throwable>) {
    val loop = if (_root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.current() == null) {
      defaultCoroutineEventLoop()
    } else {
      _root_ide_package_.io.vproxy.base.selector.SelectorEventLoop.current()
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
    val filepath = _root_ide_package_.io.vproxy.base.util.Utils.filename(filepathx)
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
          _root_ide_package_.io.vproxy.base.util.Logger.alert("loading more commands from $file")
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
          _root_ide_package_.io.vproxy.base.util.Utils.execute(pb, 5 * 1000)
          continue
        }
      }

      assert(_root_ide_package_.io.vproxy.base.util.Logger.lowLevelDebug(_root_ide_package_.io.vproxy.base.util.LogType.BEFORE_PARSING_CMD.toString() + " - " + line))

      if (isSystemCommand) {
        line = line.substring("System: ".length)
      }
      val cmd = try {
        _root_ide_package_.io.vproxy.app.app.cmd.Command.parseStrCmd(line)
      } catch (e: Exception) {
        _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.AFTER_PARSING_CMD, "parse command `$line` failed")
        throw e
      }
      assert(_root_ide_package_.io.vproxy.base.util.Logger.lowLevelDebug(_root_ide_package_.io.vproxy.base.util.LogType.AFTER_PARSING_CMD.toString() + " - " + cmd))
      executeCommand(isSystemCommand, cmd)
    }
  }

  private suspend fun executeCommand(isSystemCommand: Boolean, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    if (isSystemCommand) {
      _root_ide_package_.io.vproxy.base.util.Logger.alert("loading command: System: $cmd")
    } else {
      _root_ide_package_.io.vproxy.base.util.Logger.alert("loading command: $cmd")
    }
    awaitCallback<_root_ide_package_.io.vproxy.app.app.cmd.CmdResult, Throwable> {
      val cmds = if (isSystemCommand) {
        SystemCommands.Instance
      } else {
        ModuleCommands.Instance
      }
      cmd.run(cmds, it)
    }
  }
}
