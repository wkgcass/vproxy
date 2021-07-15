package vproxy.app.app.cmd

import vproxy.base.Config
import vproxy.base.util.LogType
import vproxy.base.util.Logger
import vproxy.base.util.exception.XException
import vproxy.base.util.functional.ConsumerEx
import java.util.*

abstract class Commands protected constructor() {
  protected val resources = ArrayList<Res>()

  @Throws(Exception::class)
  fun execute(cmd: Command): CmdResult {
    var res: Res? = null
    for (resx in resources) {
      if (resx.type == cmd.resource.type) {
        res = resx
        break
      }
    }
    if (res == null) {
      throw XException("unexpected resource " + cmd.resource.type.fullname)
    }
    var actType: ActType? = null
    if (cmd.preposition != null) {
      if (cmd.action == Action.a) {
        if (cmd.preposition == Preposition.to) {
          actType = ActType.addto
        }
      } else if (cmd.action == Action.r) {
        if (cmd.preposition == Preposition.from) {
          actType = ActType.removefrom
        }
      }
      if (actType == null) {
        throw XException("unexpected action and preposition: " + cmd.action.fullname + " " + cmd.preposition.name)
      }
    } else {
      actType = when (cmd.action) {
        Action.l -> ActType.list
        Action.L -> ActType.listdetail
        Action.a -> ActType.add
        Action.u -> ActType.update
        Action.r -> ActType.remove
        else -> null
      }
      if (actType == null) {
        throw XException("unexpected action: " + cmd.action.fullname)
      }
    }
    val actLs = res.actions[actType] ?: throw XException("unsupported action " + actType.fullname + " for " + cmd.resource.type.fullname)
    var act: ResAct? = null
    val errls = ArrayList<XException>()
    for (a in actLs) {
      // check resource
      try {
        checkParentRes(a, cmd)
        checkPreposition(a, cmd)
      } catch (e: XException) {
        // skip
        errls.add(e)
        continue
      }
      act = a
      break
    }

    if (act == null) {
      throw XException("no rules matched: $errls")
    }

    if (act.action == ActType.list || act.action == ActType.listdetail) {
      // and obviously you cannot specify a name when retrieving a list
      if (cmd.resource.alias != null) {
        throw XException("cannot specify preposition when action is " + Action.l.fullname + " or " + Action.L.fullname)
      }
    } else {
      // for non list operations, i.e. modification operations
      // check whether config is allowed to be modified
      if (Config.configModifyDisabled) {
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

  protected fun execUpdate(execute: (Command) -> Unit): (Command) -> CmdResult {
    return { cmd: Command ->
      execute(cmd)
      CmdResult()
    }
  }

  @Throws(XException::class)
  private fun checkParentRes(act: ResAct, cmd: Command) {
    if (act.relation.parent == null) {
      if (cmd.resource.parentResource != null) {
        throw XException(
          "cannot execute " + act.action.fullname + " on "
              + act.relation.resType.fullname + " inside " + cmd.resource.parentResource.type.fullname
        )
      }
      return
    }
    if (cmd.resource.parentResource == null) {
      throw XException(
        "cannot execute " + act.action.fullname + " on "
            + act.relation.resType.fullname + " on top level"
      )
    }
    val sb = StringBuilder()
    if (recursiveCheckParentRes(act.relation, cmd.resource, sb)) {
      return
    }
    throw XException("cannot execute " + act.action.fullname + " because " + sb)
  }

  @Throws(XException::class)
  private fun checkPreposition(act: ResAct, cmd: Command) {
    if (act.targetRelation == null) {
      if (cmd.prepositionResource != null) {
        throw XException(
          "cannot execute " + act.action.fullname + " on " + cmd.resource
              + " while " + " " + cmd.prepositionResource.type.fullname + " is specified"
        )
      }
      return
    }
    if (cmd.prepositionResource == null) {
      throw XException(
        "cannot execute " + act.action.fullname + " on " + act.relation.resType.fullname
            + " without " + act.targetRelation.resType
      )
    }
    val sb = StringBuilder()
    if (recursiveCheckParentRes(act.targetRelation, cmd.prepositionResource, sb)) {
      return
    }
    throw XException(
      "cannot execute " + act.action.fullname + " because "
          + "`" + cmd.preposition.name + "` " + sb
    )
  }

  private fun recursiveCheckParentRes(expected: ResRelation?, actual: Resource?, recorder: StringBuilder): Boolean {
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

  @Throws(XException::class)
  private fun checkRequiredParams(act: ResAct, cmd: Command) {
    for (param in act.requiredParams) {
      if (!cmd.args.containsKey(param)) {
        throw XException("missing parameter " + param.fullname)
      }
    }
  }

  private fun checkRedundantParamsAndFlags(act: ResAct, cmd: Command) {
    for (param in cmd.args.keys) {
      if (!act.params.containsKey(param)) {
        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected parameter " + param.fullname)
      }
    }
    for (flag in cmd.flags) {
      if (!act.flags.containsKey(flag)) {
        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "unexpected flag " + flag.fullname)
      }
    }
  }

  @Throws(Exception::class)
  private fun checkParameters(act: ResAct, cmd: Command) {
    for (param in cmd.args.keys) {
      val p = act.params[param] ?: /*extra param*/continue
      p.checkFunc(cmd)
    }
  }

  @Throws(Exception::class)
  @Suppress("UNUSED_PARAMETER")
  private fun checkFlags(act: ResAct, cmd: Command) {
    // do nothing for now
  }

  @Suppress("EnumEntryName", "unused")
  enum class ActType constructor(val action: Action, val fullname: String, val preposition: Boolean = false) {
    list(Action.l, Action.l.fullname),
    listdetail(Action.L, Action.L.fullname),
    add(Action.a, Action.a.fullname),
    update(Action.u, Action.u.fullname),
    remove(Action.r, Action.r.fullname),
    addto(Action.a, "add-to", true),
    removefrom(Action.r, "remove-from", true);
  }

  class ResRelation(val resType: ResourceType, val parent: ResRelation? = null)

  class Res constructor(
    val type: ResourceType,
    additionalCheck: ConsumerEx<Command, Exception>?,
    actions: (AddHelper<ResAct>) -> Unit,
  ) {
    val actions: MutableMap<ActType, List<ResAct>>
    val additionalCheck: ConsumerEx<Command, Exception>?

    constructor(type: ResourceType, actions: (AddHelper<ResAct>) -> Unit) :
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
    val check: ((Command) -> Unit)? = null,
    val exec: (Command) -> CmdResult,
  ) {
    val params: MutableMap<Param, ResActParam>
    val requiredParams: MutableList<Param>
    val flags: MutableMap<Flag, ResActFlag>

    constructor(
      relation: ResourceType,
      action: ActType,
      targetRelation: ResRelation? = null,
      params: (AddHelper<ResActParam>) -> Unit = {},
      flags: (AddHelper<ResActFlag>) -> Unit = {},
      check: ((Command) -> Unit)? = null,
      exec: (Command) -> CmdResult,
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

      this.params = EnumMap(Param::class.java)
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
      this.flags = EnumMap(Flag::class.java)
      for (flag in flagsLs) {
        this.flags[flag.flag] = flag
      }
    }
  }

  class ResActParam(val param: Param, val required: Boolean = false, val checkFunc: (Command) -> Unit = {})

  class ResActFlag(val flag: Flag)

  companion object {
    const val required: Boolean = true
  }
}
