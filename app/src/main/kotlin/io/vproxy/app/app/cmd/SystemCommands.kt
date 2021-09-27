package io.vproxy.app.app.cmd

import io.vproxy.app.app.cmd.handle.param.AddrHandle
import io.vproxy.app.app.cmd.handle.param.URLHandle
import io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle
import io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle
import io.vproxy.app.app.cmd.handle.resource.PluginHandle
import io.vproxy.app.app.cmd.handle.resource.RespControllerHandle
import io.vproxy.app.process.Shutdown
import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class SystemCommands private constructor() : Commands() {
  companion object {
    val Instance = SystemCommands()
  }

  init {
    val it = AddHelper(resources)
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.config) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.config,
        action = ActType.list,
      ) {
        val config = _root_ide_package_.io.vproxy.app.process.Shutdown.currentConfig()
        val lines = listOf(config.split("\n"))
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(config, lines, config)
      }
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.respcontroller) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, true) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.pass, true)
        }
      ) {
        _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.add(it)
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.list,
      ) {
        val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.listdetail
      ) {
        val rcRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.details()
        val rcRefStrList = rcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(rcRefList, rcRefStrList, utilJoinList(rcRefList))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.remove
      ) {
        _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.removeAndStop(it)
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
      }
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.httpcontroller) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, true) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
        }
      ) {
        _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.add(it)
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.list
      ) {
        val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.listdetail
      ) {
        val hcRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.details()
        val hcRefStrList = hcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(hcRefList, hcRefStrList, utilJoinList(hcRefList))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.remove
      ) {
        _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.removeAndStop(it)
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
      }
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller,
        action = ActType.list,
      ) {
        val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller,
        action = ActType.listdetail,
      ) {
        val dcRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.details()
        val dcRefStrList = dcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(dcRefList, dcRefStrList, utilJoinList(dcRefList))
      }
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.url, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.URLHandle.get(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.cls, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.args)
        }
      ) {
        _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.PluginHandle.add(it)
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.list,
      ) {
        val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.PluginHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.listdetail,
      ) {
        val pluginRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.PluginHandle.details()
        val pluginRefStrList = pluginRefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(pluginRefList, pluginRefStrList, utilJoinList(pluginRefList))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.update,
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.enable)
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.disable)
        },
        check = {
          if (it.flags.contains(_root_ide_package_.io.vproxy.app.app.cmd.Flag.enable) && it.flags.contains(_root_ide_package_.io.vproxy.app.app.cmd.Flag.disable)) {
            throw Exception("cannot set enable and disable at the same time")
          }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.PluginHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.remove,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.PluginHandle.unload(it) }
      )
    }
  }
}
