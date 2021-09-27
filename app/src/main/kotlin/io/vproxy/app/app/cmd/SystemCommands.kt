package io.vproxy.app.app.cmd

import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class SystemCommands private constructor() : Commands() {
  companion object {
    val Instance = SystemCommands()
  }

  init {
    val it = AddHelper(resources)
    it + Res(io.vproxy.app.app.cmd.ResourceType.config) {
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.config,
        action = ActType.list,
      ) {
        val config = io.vproxy.app.process.Shutdown.currentConfig()
        val lines = listOf(config.split("\n"))
        io.vproxy.app.app.cmd.CmdResult(config, lines, config)
      }
    }
    it + Res(io.vproxy.app.app.cmd.ResourceType.respcontroller) {
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(io.vproxy.app.app.cmd.Param.addr, true) { io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(io.vproxy.app.app.cmd.Param.pass, true)
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.add(it)
        io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.names()
        io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.listdetail
      ) {
        val rcRefList = io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.details()
        val rcRefStrList = rcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        io.vproxy.app.app.cmd.CmdResult(rcRefList, rcRefStrList, utilJoinList(rcRefList))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.respcontroller,
        action = ActType.remove
      ) {
        io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.removeAndStop(it)
        io.vproxy.app.app.cmd.CmdResult()
      }
    }
    it + Res(io.vproxy.app.app.cmd.ResourceType.httpcontroller) {
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(io.vproxy.app.app.cmd.Param.addr, true) { io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.add(it)
        io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.list
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.names()
        io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.listdetail
      ) {
        val hcRefList = io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.details()
        val hcRefStrList = hcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        io.vproxy.app.app.cmd.CmdResult(hcRefList, hcRefStrList, utilJoinList(hcRefList))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.httpcontroller,
        action = ActType.remove
      ) {
        io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.removeAndStop(it)
        io.vproxy.app.app.cmd.CmdResult()
      }
    }
    it + Res(io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller) {
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.names()
        io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.dockernetworkplugincontroller,
        action = ActType.listdetail,
      ) {
        val dcRefList = io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.details()
        val dcRefStrList = dcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        io.vproxy.app.app.cmd.CmdResult(dcRefList, dcRefStrList, utilJoinList(dcRefList))
      }
    }
    it + Res(io.vproxy.app.app.cmd.ResourceType.plugin) {
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.add,
        params = {
          it + ResActParam(io.vproxy.app.app.cmd.Param.url, required) { io.vproxy.app.app.cmd.handle.param.URLHandle.get(it) }
          it + ResActParam(io.vproxy.app.app.cmd.Param.cls, required)
          it + ResActParam(io.vproxy.app.app.cmd.Param.args)
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.PluginHandle.add(it)
        io.vproxy.app.app.cmd.CmdResult()
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.PluginHandle.names()
        io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.listdetail,
      ) {
        val pluginRefList = io.vproxy.app.app.cmd.handle.resource.PluginHandle.details()
        val pluginRefStrList = pluginRefList.stream().map { it.toString() }.collect(Collectors.toList())
        io.vproxy.app.app.cmd.CmdResult(pluginRefList, pluginRefStrList, utilJoinList(pluginRefList))
      }
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.update,
        flags = {
          it + ResActFlag(io.vproxy.app.app.cmd.Flag.enable)
          it + ResActFlag(io.vproxy.app.app.cmd.Flag.disable)
        },
        check = {
          if (it.flags.contains(io.vproxy.app.app.cmd.Flag.enable) && it.flags.contains(io.vproxy.app.app.cmd.Flag.disable)) {
            throw Exception("cannot set enable and disable at the same time")
          }
        },
        exec = execUpdate { io.vproxy.app.app.cmd.handle.resource.PluginHandle.update(it) }
      )
      it + ResAct(
        relation = io.vproxy.app.app.cmd.ResourceType.plugin,
        action = ActType.remove,
        exec = execUpdate { io.vproxy.app.app.cmd.handle.resource.PluginHandle.unload(it) }
      )
    }
  }
}
