package vproxy.app.app.cmd

import vproxy.app.app.cmd.handle.param.URLHandle
import vproxy.app.app.cmd.handle.resource.PluginHandle
import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class SystemCommands : Commands() {
  init {
    val it = AddHelper(resources)
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
