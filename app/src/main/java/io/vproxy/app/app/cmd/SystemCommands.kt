package io.vproxy.app.app.cmd

import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class SystemCommands private constructor() : Commands() {
  companion object {
    val Instance = SystemCommands()
  }

  init {
    val it = AddHelper(resources)
    it + Res(ResourceType.config) {
      it + ResAct(
        relation = ResourceType.config,
        action = ActType.list,
      ) {
        val config = io.vproxy.app.process.Shutdown.currentConfig()
        val lines = listOf(config.split("\n"))
        CmdResult(config, lines, config)
      }
    }
    it + Res(ResourceType.respcontroller) {
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, true) { io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(Param.pass, true)
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.listdetail
      ) {
        val rcRefList = io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.details()
        val rcRefStrList = rcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(rcRefList, rcRefStrList, utilJoinList(rcRefList))
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.remove
      ) {
        io.vproxy.app.app.cmd.handle.resource.RespControllerHandle.removeAndStop(it)
        CmdResult()
      }
    }
    it + Res(ResourceType.httpcontroller) {
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, true) { io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.list
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.listdetail
      ) {
        val hcRefList = io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.details()
        val hcRefStrList = hcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(hcRefList, hcRefStrList, utilJoinList(hcRefList))
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.remove
      ) {
        io.vproxy.app.app.cmd.handle.resource.HttpControllerHandle.removeAndStop(it)
        CmdResult()
      }
    }
    it + Res(ResourceType.dockernetworkplugincontroller) {
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.listdetail,
      ) {
        val dcRefList = io.vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle.details()
        val dcRefStrList = dcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(dcRefList, dcRefStrList, utilJoinList(dcRefList))
      }
    }
    it + Res(ResourceType.plugin) {
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.add,
        params = {
          it + ResActParam(Param.url, required) { io.vproxy.app.app.cmd.handle.param.URLHandle.get(it) }
          it + ResActParam(Param.cls, required)
          it + ResActParam(Param.args)
        }
      ) {
        io.vproxy.app.app.cmd.handle.resource.PluginHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.list,
      ) {
        val names = io.vproxy.app.app.cmd.handle.resource.PluginHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.listdetail,
      ) {
        val pluginRefList = io.vproxy.app.app.cmd.handle.resource.PluginHandle.details()
        val pluginRefStrList = pluginRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(pluginRefList, pluginRefStrList, utilJoinList(pluginRefList))
      }
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.update,
        flags = {
          it + ResActFlag(Flag.enable)
          it + ResActFlag(Flag.disable)
        },
        check = {
          if (it.flags.contains(Flag.enable) && it.flags.contains(Flag.disable)) {
            throw Exception("cannot set enable and disable at the same time")
          }
        },
        exec = execUpdate { io.vproxy.app.app.cmd.handle.resource.PluginHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.remove,
        exec = execUpdate { io.vproxy.app.app.cmd.handle.resource.PluginHandle.unload(it) }
      )
    }
  }
}
