package vproxy.app.app.cmd

import vproxy.app.app.cmd.handle.param.AddrHandle
import vproxy.app.app.cmd.handle.param.URLHandle
import vproxy.app.app.cmd.handle.resource.DockerNetworkPluginControllerHandle
import vproxy.app.app.cmd.handle.resource.HttpControllerHandle
import vproxy.app.app.cmd.handle.resource.PluginHandle
import vproxy.app.app.cmd.handle.resource.RespControllerHandle
import vproxy.app.process.Shutdown
import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class SystemCommands : Commands() {
  init {
    val it = AddHelper(resources)
    it + Res(ResourceType.config) {
      it + ResAct(
        relation = ResourceType.config,
        action = ActType.list,
      ) {
        val config = Shutdown.currentConfig()
        val lines = listOf(config.split("\n"))
        CmdResult(config, lines, config)
      }
    }
    it + Res(ResourceType.respcontroller) {
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, true) { AddrHandle.check(it) }
          it + ResActParam(Param.pass, true)
        }
      ) {
        RespControllerHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.list,
      ) {
        val names = RespControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.listdetail
      ) {
        val rcRefList = RespControllerHandle.details()
        val rcRefStrList = rcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(rcRefList, rcRefStrList, utilJoinList(rcRefList))
      }
      it + ResAct(
        relation = ResourceType.respcontroller,
        action = ActType.remove
      ) {
        RespControllerHandle.removeAndStop(it)
        CmdResult()
      }
    }
    it + Res(ResourceType.httpcontroller) {
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, true) { AddrHandle.check(it) }
        }
      ) {
        HttpControllerHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.list
      ) {
        val names = HttpControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.listdetail
      ) {
        val hcRefList = HttpControllerHandle.details()
        val hcRefStrList = hcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(hcRefList, hcRefStrList, utilJoinList(hcRefList))
      }
      it + ResAct(
        relation = ResourceType.httpcontroller,
        action = ActType.remove
      ) {
        HttpControllerHandle.removeAndStop(it)
        CmdResult()
      }
    }
    it + Res(ResourceType.dockernetworkplugincontroller) {
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.add,
        params = {
          it + ResActParam(Param.path, true)
        }
      ) {
        DockerNetworkPluginControllerHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.list,
      ) {
        val names = DockerNetworkPluginControllerHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.listdetail,
      ) {
        val dcRefList = DockerNetworkPluginControllerHandle.details()
        val dcRefStrList = dcRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(dcRefList, dcRefStrList, utilJoinList(dcRefList))
      }
      it + ResAct(
        relation = ResourceType.dockernetworkplugincontroller,
        action = ActType.remove,
      ) {
        DockerNetworkPluginControllerHandle.removeAndStop(it)
        CmdResult()
      }
    }
    it + Res(ResourceType.plugin) {
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.add,
        params = {
          it + ResActParam(Param.url, required) { URLHandle.get(it) }
          it + ResActParam(Param.cls, required)
        }
      ) {
        PluginHandle.add(it)
        CmdResult()
      }
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.list,
      ) {
        val names = PluginHandle.names()
        CmdResult(names, names, utilJoinList(names))
      }
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.listdetail,
      ) {
        val pluginRefList = PluginHandle.details()
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
        exec = execUpdate { PluginHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.plugin,
        action = ActType.remove,
        exec = execUpdate { PluginHandle.unload(it) }
      )
    }
  }
}
