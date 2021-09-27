package io.vproxy.app.app.cmd

import io.vproxy.base.Config
import io.vproxy.base.util.LogType
import io.vproxy.base.util.Logger
import io.vproxy.base.util.exception.XException
import io.vproxy.base.util.functional.ConsumerEx
import java.util.*

abstract class Commands protected constructor() {
  protected val resources = ArrayList<Res>()

  @Throws(Exception::class)
  fun execute(cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command): _root_ide_package_.io.vproxy.app.app.cmd.CmdResult {
    var res: Res? = null
    for (resx in resources) {
      if (resx.type == cmd.resource.type) {
        res = resx
        break
      }
    }
    if (res == null) {
      throw _root_ide_package_.io.vproxy.base.util.exception.XException("unexpected resource " + cmd.resource.type.fullname)
    }
    var actType: ActType? = null
    if (cmd.preposition != null) {
      if (cmd.action == _root_ide_package_.io.vproxy.app.app.cmd.Action.add) {
        if (cmd.preposition == _root_ide_package_.io.vproxy.app.app.cmd.Preposition.to) {
          actType = ActType.addto
        }
      } else if (cmd.action == _root_ide_package_.io.vproxy.app.app.cmd.Action.rm) {
        if (cmd.preposition == _root_ide_package_.io.vproxy.app.app.cmd.Preposition.from) {
          actType = ActType.removefrom
        }
      }
      if (actType == null) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException("unexpected action and preposition: " + cmd.action.fullname + " " + cmd.preposition.name)
      }
    } else {
      actType = when (cmd.action) {
        _root_ide_package_.io.vproxy.app.app.cmd.Action.ls -> ActType.list
        _root_ide_package_.io.vproxy.app.app.cmd.Action.ll -> ActType.listdetail
        _root_ide_package_.io.vproxy.app.app.cmd.Action.add -> ActType.add
        _root_ide_package_.io.vproxy.app.app.cmd.Action.mod -> ActType.update
        _root_ide_package_.io.vproxy.app.app.cmd.Action.rm -> ActType.remove
        else -> null
      }
      if (actType == null) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException("unexpected action: " + cmd.action.fullname)
      }
    }
    val actLs = res.actions[actType] ?: throw _root_ide_package_.io.vproxy.base.util.exception.XException("unsupported action " + actType.fullname + " for " + cmd.resource.type.fullname)
    var act: ResAct? = null
    val errls = ArrayList<_root_ide_package_.io.vproxy.base.util.exception.XException>()
    for (a in actLs) {
      // check resource
      try {
        checkParentRes(a, cmd)
        checkPreposition(a, cmd)
      } catch (e: _root_ide_package_.io.vproxy.base.util.exception.XException) {
        // skip
        errls.add(e)
        continue
      }
      act = a
      break
    }

    if (act == null) {
      throw _root_ide_package_.io.vproxy.base.util.exception.XException("no rules matched: $errls")
    }

    if (act.action == ActType.list || act.action == ActType.listdetail) {
      // and obviously you cannot specify a name when retrieving a list
      if (cmd.resource.alias != null) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException("cannot specify preposition when action is " + _root_ide_package_.io.vproxy.app.app.cmd.Action.ls.fullname + " or " + _root_ide_package_.io.vproxy.app.app.cmd.Action.ll.fullname)
      }
    } else {
      // for non list operations, i.e. modification operations
      // check whether config is allowed to be modified
      if (_root_ide_package_.io.vproxy.base.Config.configModifyDisabled) {
        throw Exception("modifying is disabled")
      }
      // the name to operate is required
      if (cmd.resource.alias == null) {
        throw Exception("resource name not specified when " + cmd.action.fullname + "(-ing) the resource")
      }
    }

    if (res.additionalCheck != null) {
      res.additionalCheck!!.accept(cmd)
    }

    checkRequiredParams(act, cmd)
    checkRedundantParamsAndFlags(act, cmd)
    checkParameters(act, cmd)
    checkFlags(act, cmd)

    val check = act.check
    if (check != null) {
      check(cmd)
    }

    // execute
    return act.exec(cmd)
  }

  protected fun execUpdate(execute: (_root_ide_package_.io.vproxy.app.app.cmd.Command) -> Unit): (_root_ide_package_.io.vproxy.app.app.cmd.Command) -> _root_ide_package_.io.vproxy.app.app.cmd.CmdResult {
    return { cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command ->
      execute(cmd)
      _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
    }
  }

  @Throws(_root_ide_package_.io.vproxy.base.util.exception.XException::class)
  private fun checkParentRes(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    if (act.relation.parent == null) {
      if (cmd.resource.parentResource != null) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException(
          "cannot execute " + act.action.fullname + " on "
            + act.relation.resType.fullname + " inside " + cmd.resource.parentResource.type.fullname
        )
      }
      return
    }
    if (cmd.resource.parentResource == null) {
      throw _root_ide_package_.io.vproxy.base.util.exception.XException(
        "cannot execute " + act.action.fullname + " on "
          + act.relation.resType.fullname + " on top level"
      )
    }
    val sb = StringBuilder()
    if (recursiveCheckParentRes(act.relation, cmd.resource, sb)) {
      return
    }
    throw _root_ide_package_.io.vproxy.base.util.exception.XException("cannot execute " + act.action.fullname + " because " + sb)
  }

  @Throws(_root_ide_package_.io.vproxy.base.util.exception.XException::class)
  private fun checkPreposition(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    if (act.targetRelation == null) {
      if (cmd.prepositionResource != null) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException(
          "cannot execute " + act.action.fullname + " on " + cmd.resource
            + " while " + " " + cmd.prepositionResource.type.fullname + " is specified"
        )
      }
      return
    }
    if (cmd.prepositionResource == null) {
      throw _root_ide_package_.io.vproxy.base.util.exception.XException(
        "cannot execute " + act.action.fullname + " on " + act.relation.resType.fullname
          + " without " + act.targetRelation.resType
      )
    }
    val sb = StringBuilder()
    if (recursiveCheckParentRes(act.targetRelation, cmd.prepositionResource, sb)) {
      return
    }
    throw _root_ide_package_.io.vproxy.base.util.exception.XException(
      "cannot execute " + act.action.fullname + " because "
        + "`" + cmd.preposition.name + "` " + sb
    )
  }

  private fun recursiveCheckParentRes(expected: ResRelation?, actual: _root_ide_package_.io.vproxy.app.app.cmd.Resource?, recorder: StringBuilder): Boolean {
    if (expected == null && actual == null) return true
    if (expected == null) { // actual != null
      recorder.append(actual!!.type.fullname).append(" is redundant")
      return false
    }
    if (actual == null) { // expected != null
      recorder.append(expected.resType.fullname).append(" is missing")
      return false
    }
    recorder.append(expected.resType.fullname).append(" in ")
    return recursiveCheckParentRes(expected.parent, actual.parentResource, recorder)
  }

  @Throws(_root_ide_package_.io.vproxy.base.util.exception.XException::class)
  private fun checkRequiredParams(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    for (param in act.requiredParams) {
      if (!cmd.args.containsKey(param)) {
        throw _root_ide_package_.io.vproxy.base.util.exception.XException("missing parameter " + param.fullname)
      }
    }
  }

  private fun checkRedundantParamsAndFlags(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    for (param in cmd.args.keys) {
      if (!act.params.containsKey(param)) {
        _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "unexpected parameter " + param.fullname)
      }
    }
    for (flag in cmd.flags) {
      if (!act.flags.containsKey(flag)) {
        _root_ide_package_.io.vproxy.base.util.Logger.warn(_root_ide_package_.io.vproxy.base.util.LogType.INVALID_EXTERNAL_DATA, "unexpected flag " + flag.fullname)
      }
    }
  }

  @Throws(Exception::class)
  private fun checkParameters(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    for (param in cmd.args.keys) {
      val p = act.params[param] ?: /*extra param*/continue
      p.checkFunc(cmd)
    }
  }

  @Throws(Exception::class)
  @Suppress("UNUSED_PARAMETER")
  private fun checkFlags(act: ResAct, cmd: _root_ide_package_.io.vproxy.app.app.cmd.Command) {
    // do nothing for now
  }

  @Suppress("EnumEntryName", "unused")
  enum class ActType constructor(val action: _root_ide_package_.io.vproxy.app.app.cmd.Action, val fullname: String, val preposition: Boolean = false) {
    list(_root_ide_package_.io.vproxy.app.app.cmd.Action.ls, _root_ide_package_.io.vproxy.app.app.cmd.Action.ls.fullname),
    listdetail(_root_ide_package_.io.vproxy.app.app.cmd.Action.ll, _root_ide_package_.io.vproxy.app.app.cmd.Action.ll.fullname),
    add(_root_ide_package_.io.vproxy.app.app.cmd.Action.add, _root_ide_package_.io.vproxy.app.app.cmd.Action.add.fullname),
    update(_root_ide_package_.io.vproxy.app.app.cmd.Action.mod, _root_ide_package_.io.vproxy.app.app.cmd.Action.mod.fullname),
    remove(_root_ide_package_.io.vproxy.app.app.cmd.Action.rm, _root_ide_package_.io.vproxy.app.app.cmd.Action.rm.fullname),
    addto(_root_ide_package_.io.vproxy.app.app.cmd.Action.add, "add-to", true),
    removefrom(_root_ide_package_.io.vproxy.app.app.cmd.Action.rm, "remove-from", true);
  }

  class ResRelation(val resType: _root_ide_package_.io.vproxy.app.app.cmd.ResourceType, val parent: ResRelation? = null)

  class Res constructor(
    val type: _root_ide_package_.io.vproxy.app.app.cmd.ResourceType,
    additionalCheck: _root_ide_package_.io.vproxy.base.util.functional.ConsumerEx<_root_ide_package_.io.vproxy.app.app.cmd.Command, Exception>?,
    actions: (AddHelper<ResAct>) -> Unit,
  ) {
    val actions: MutableMap<ActType, List<ResAct>>
    val additionalCheck: _root_ide_package_.io.vproxy.base.util.functional.ConsumerEx<_root_ide_package_.io.vproxy.app.app.cmd.Command, Exception>?

    constructor(type: _root_ide_package_.io.vproxy.app.app.cmd.ResourceType, actions: (AddHelper<ResAct>) -> Unit) :
        this(type, null, actions)

    init {
      this.actions = EnumMap(ActType::class.java)
      val ls = ArrayList<ResAct>()
      val helper = AddHelper(ls)
      actions(helper)
      for (act in ls) {
        val foo = if (this.actions.containsKey(act.action)) {
          this.actions[act.action] as MutableList<ResAct>
        } else {
          val bar = ArrayList<ResAct>()
          this.actions[act.action] = bar
          bar
        }
        foo.add(act)
      }
      this.additionalCheck = additionalCheck
    }
  }

  class AddHelper<E>(val ls: MutableList<E>) {
    operator fun plus(e: E) {
      ls.add(e)
    }
  }

  class ResAct(
    val relation: ResRelation,
    val action: ActType,
    val targetRelation: ResRelation? = null,
    params: (AddHelper<ResActParam>) -> Unit = {},
    flags: (AddHelper<ResActFlag>) -> Unit = {},
    val check: ((_root_ide_package_.io.vproxy.app.app.cmd.Command) -> Unit)? = null,
    val exec: (_root_ide_package_.io.vproxy.app.app.cmd.Command) -> _root_ide_package_.io.vproxy.app.app.cmd.CmdResult,
  ) {
    val params: MutableMap<_root_ide_package_.io.vproxy.app.app.cmd.Param, ResActParam>
    val requiredParams: MutableList<_root_ide_package_.io.vproxy.app.app.cmd.Param>
    val flags: MutableMap<_root_ide_package_.io.vproxy.app.app.cmd.Flag, ResActFlag>

    constructor(
      relation: _root_ide_package_.io.vproxy.app.app.cmd.ResourceType,
      action: ActType,
      targetRelation: ResRelation? = null,
      params: (AddHelper<ResActParam>) -> Unit = {},
      flags: (AddHelper<ResActFlag>) -> Unit = {},
      check: ((_root_ide_package_.io.vproxy.app.app.cmd.Command) -> Unit)? = null,
      exec: (_root_ide_package_.io.vproxy.app.app.cmd.Command) -> _root_ide_package_.io.vproxy.app.app.cmd.CmdResult,
    ) : this(ResRelation(relation), action, targetRelation, params, flags, check, exec)

    init {
      // check input
      if (action == ActType.addto || action == ActType.removefrom) {
        if (targetRelation == null) {
          throw IllegalArgumentException("add-to or remove-from must specify the target resource")
        }
      } else {
        if (targetRelation != null) {
          throw IllegalArgumentException("target resource cannot be specified when running action " + action.fullname)
        }
      }

      this.params = EnumMap(_root_ide_package_.io.vproxy.app.app.cmd.Param::class.java)
      requiredParams = ArrayList()

      val paramsLs = ArrayList<ResActParam>()
      val paramsHelper = AddHelper(paramsLs)
      params(paramsHelper)
      for (param in paramsLs) {
        this.params[param.param] = param
        if (param.required) {
          requiredParams.add(param.param)
        }
      }

      val flagsLs = ArrayList<ResActFlag>()
      val flagsHelper = AddHelper(flagsLs)
      flags(flagsHelper)
      this.flags = EnumMap(_root_ide_package_.io.vproxy.app.app.cmd.Flag::class.java)
      for (flag in flagsLs) {
        this.flags[flag.flag] = flag
      }
    }
  }

  class ResActParam(val param: _root_ide_package_.io.vproxy.app.app.cmd.Param, val required: Boolean = false, val checkFunc: (_root_ide_package_.io.vproxy.app.app.cmd.Command) -> Unit = {})

  class ResActFlag(val flag: _root_ide_package_.io.vproxy.app.app.cmd.Flag)

  companion object {
    const val required: Boolean = true
  }

  protected fun utilJoinList(ls: List<*>): String {
    val sb = StringBuilder()
    var isFirst = true
    for (o in ls) {
      if (isFirst) {
        isFirst = false
      } else {
        sb.append("\n")
      }
      sb.append(o)
    }
    return sb.toString()
  }
}
