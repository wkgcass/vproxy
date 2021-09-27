package io.vproxy.app.app.cmd

import vproxy.app.app.cmd.handle.param.*
import vproxy.app.app.cmd.handle.resource.*
import io.vproxy.base.dns.Cache
import io.vproxy.base.util.exception.XException
import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class ModuleCommands private constructor() : Commands() {
  companion object {
    val Instance = ModuleCommands()
  }

  init {
    val it = AddHelper(resources)
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ups, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.aelg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.elg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.inbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.InBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.outbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.OutBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.protocol)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ck)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TcpLBHandle.add(it) },
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl,
        ActType.list,
      ) {
        val tlNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TcpLBHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(tlNames, tlNames, utilJoinList(tlNames))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl,
        action = ActType.listdetail,
      ) {
        val tlRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TcpLBHandle.details()
        val tlRefStrList = tlRefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(tlRefList, tlRefStrList, utilJoinList(tlRefList))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.inbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.InBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.outbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.OutBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ck)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TcpLBHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tl,
        action = ActType.remove,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TcpLBHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ups, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.aelg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.elg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.inbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.InBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.outbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.OutBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.allownonbackend)
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.denynonbackend)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.Socks5ServerHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5,
        action = ActType.list
      ) {
        val socks5Names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.Socks5ServerHandle.names()
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(socks5Names, socks5Names, utilJoinList(socks5Names))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5,
        action = ActType.listdetail
      ) {
        val socks5RefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.Socks5ServerHandle.details()
        val socks5RefStrList = socks5RefList.stream().map { it.toString() }.collect(Collectors.toList())
        _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(socks5RefList, socks5RefStrList, utilJoinList(socks5RefList))
      }
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.inbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.InBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.outbuffersize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.OutBufferSizeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.allownonbackend)
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.denynonbackend)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.Socks5ServerHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.socks5,
        action = ActType.remove,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.Socks5ServerHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ups, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.elg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ttl) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TTLHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DNSServerHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns,
        action = ActType.list,
        exec = {
          val dnsServerNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DNSServerHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(dnsServerNames, dnsServerNames, utilJoinList(dnsServerNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns,
        action = ActType.listdetail,
        exec = {
          val dnsServerRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DNSServerHandle.details()
          val dnsServerRefStrList = dnsServerRefList.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(dnsServerRefStrList, dnsServerRefStrList, utilJoinList(dnsServerRefList))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ttl) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TTLHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DNSServerHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dns,
        action = ActType.remove,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DNSServerHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopGroupHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg,
        action = ActType.list,
        exec = {
          val elgNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopGroupHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(elgNames, elgNames, utilJoinList(elgNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg,
        action = ActType.listdetail,
        exec = {
          val elgs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopGroupHandle.details()
          val elgStrs = elgs.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(elgs, elgStrs, utilJoinList(elgs))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopGroupHandle.remvoe(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups,
        action = ActType.add,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UpstreamHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups,
        action = ActType.list,
        exec = {
          val upsNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UpstreamHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(upsNames, upsNames, utilJoinList(upsNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups,
        action = ActType.listdetail,
        exec = {
          val upsNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UpstreamHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(upsNames, upsNames, utilJoinList(upsNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UpstreamHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UpstreamHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.period, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.up, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.down, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.protocol)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.meth) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MethHandle.get(it, "") }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.elg)
        },
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.HealthCheckHandle.getHealthCheckConfig(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.weight) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.WeightHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.attach(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.list,
        exec = {
          val sgNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.listdetail,
        exec = {
          val refs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.details()
          val refStrList = refs.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(refs, refStrList, utilJoinList(refStrList))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups)),
        action = ActType.list,
        exec = {
          val sgNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups)),
        action = ActType.listdetail,
        exec = {
          val refs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.details(it.resource.parentResource)
          val refStrList = refs.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(refs, refStrList, utilJoinList(refStrList))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.timeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.period) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.up) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.down) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.protocol)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.meth) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MethHandle.get(it, "") }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.update(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups)),
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.weight) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.WeightHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.updateInUpstream(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.remove(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ups),
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerGroupHandle.detach(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.el) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.el,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.el, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg)),
        action = ActType.list,
        exec = {
          val elNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(elNames, elNames, utilJoinList(elNames))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.el, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg)),
        action = ActType.listdetail,
        exec = {
          val els = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopHandle.detail(it.resource.parentResource)
          val elStrList = els.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(els, elStrList, utilJoinList(els))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.el,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.elg),
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.EventLoopHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.weight) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.WeightHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg)),
        action = ActType.list,
        exec = {
          val serverNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(serverNames, serverNames, utilJoinList(serverNames))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg)),
        action = ActType.listdetail,
        exec = {
          val svrRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerHandle.detail(it.resource.parentResource)
          val svrRefStrList = svrRefList.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(svrRefList, svrRefStrList, utilJoinList(svrRefList))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg)),
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.weight) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.WeightHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.svr,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sg),
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ServerHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secgrdefault, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg,
        action = ActType.list,
        exec = {
          val sgNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg,
        action = ActType.listdetail,
        exec = {
          val secg = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.detail()
          val secgStrList = secg.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(secgStrList, secgStrList, utilJoinList(secg))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secgrdefault) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secgr) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secgr,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.net, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.protocol, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.ProtocolHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.portrange, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.PortRangeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secgrdefault, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupRuleHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secgr, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg)),
        action = ActType.list,
        exec = {
          val ruleNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupRuleHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(ruleNames, ruleNames, utilJoinList(ruleNames))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secgr, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg)),
        action = ActType.listdetail,
        exec = {
          val rules = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupRuleHandle.detail(it.resource.parentResource)
          val ruleStrList = rules.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(rules, ruleStrList, utilJoinList(rules))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secgr,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.secg),
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SecurityGroupRuleHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ck) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ck,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.cert, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.key, required)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.CertKeyHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ck,
        action = ActType.list,
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.CertKeyHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ck,
        action = ActType.listdetail,
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.CertKeyHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ck,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.CertKeyHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.CertKeyHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dnscache) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dnscache,
        action = ActType.list,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = {
          val cacheCnt = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DnsCacheHandle.count()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(cacheCnt, cacheCnt, "" + cacheCnt)
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dnscache, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.resolver)),
        action = ActType.listdetail,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = {
          val caches = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DnsCacheHandle.detail()
          val cacheStrList = caches.stream().map { c: _root_ide_package_.io.vproxy.base.dns.Cache ->
            listOf(
              c.host,
              c.ipv4.stream().map { it.formatToIPString() }
                .collect(Collectors.toList()),
              c.ipv6.stream().map { it.formatToIPString() }
                .collect(Collectors.toList())
            )
          }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(caches, cacheStrList, utilJoinList(caches))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.dnscache,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.DnsCacheHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mactabletimeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.mactabletimeout) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.arptabletimeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.arptabletimeout) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.elg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mtu) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MTUHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.flood) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FloodHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.list,
        exec = {
          val swNames = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(swNames, swNames, utilJoinList(swNames))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.listdetail,
        exec = {
          val swRefList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.details()
          val swRefStrList = swRefList.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(swRefList, swRefStrList, utilJoinList(swRefList))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mactabletimeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.mactabletimeout) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.arptabletimeout) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.TimeoutHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.arptabletimeout) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.secg)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mtu) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MTUHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.flood) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FloodHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.remove,
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.remove(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.noswitchflag)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.SwitchHandle.attach(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.v4net, required) {
            _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.v4net)
            val net = _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.get(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.v4net)
            if (net.ip.address.size != 4) {
              throw _root_ide_package_.io.vproxy.base.util.exception.XException("invalid argument " + _root_ide_package_.io.vproxy.app.app.cmd.Param.v4net + ": not ipv4 network: " + net)
            }
          }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.v6net) {
            _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.v6net)
            val net = _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.get(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.v6net)
            if (net.ip.address.size != 16) {
              throw _root_ide_package_.io.vproxy.base.util.exception.XException("invalid argument " + _root_ide_package_.io.vproxy.app.app.cmd.Param.v6net + ": not ipv6 network: " + net)
            }
          }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.list,
        exec = {
          val vpcLs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.list(it.resource.parentResource)
          val ls = vpcLs.stream().map { it.vpc }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(vpcLs, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val vpcLs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.list(it.resource.parentResource)
          val ls = vpcLs.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(vpcLs, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.iface) {
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.iface, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.list,
        exec = {
          val cnt = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IfaceHandle.count(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(cnt, cnt, "" + cnt)
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.iface, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val ifaces = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IfaceHandle.list(it.resource.parentResource)
          val ls = ifaces.stream().map { it.name() + " -> " + it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(ifaces, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.iface, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mtu) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MTUHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.flood) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FloodHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.enable)
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.disable)
        },
        check = {
          if (it.flags.contains(_root_ide_package_.io.vproxy.app.app.cmd.Flag.enable) && it.flags.contains(_root_ide_package_.io.vproxy.app.app.cmd.Flag.disable)) {
            throw _root_ide_package_.io.vproxy.base.util.exception.XException("cannot specify enable and disable at the same time")
          }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IfaceHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.iface,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        exec = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IfaceHandle.remove(it)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
        }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.arp) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.arp,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.ip) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.IpParamHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.iface)
        },
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.checkMacName(it.resource)
          if (!it.args.containsKey(_root_ide_package_.io.vproxy.app.app.cmd.Param.ip) && !(it.args.containsKey(_root_ide_package_.io.vproxy.app.app.cmd.Param.iface))) {
            throw _root_ide_package_.io.vproxy.base.util.exception.XException("at lease one of ip|iface should be specified")
          }
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.prepositionResource)
        },
        exec = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.add(it)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
        }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.arp, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.list,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val cnt = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.count(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(cnt, cnt, "" + cnt)
        }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.arp, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.listdetail,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val arpLs = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.list(it.resource.parentResource)
          val ls = arpLs.stream().map { it.toString(arpLs) }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(arpLs, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.arp,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.checkMacName(it.resource) },
        exec = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.ArpHandle.remove(it)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
        }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.pass, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mtu) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MTUHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.flood) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FloodHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.list,
        exec = {
          val users = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(users, users, utilJoinList(users))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val userInfoList = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserHandle.list(it.resource.parentResource)
          val strList = userInfoList.stream().map { it.toString() }
            .collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(userInfoList, strList, utilJoinList(strList))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mtu) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MTUHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.flood) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FloodHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserHandle.update(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.user,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tap) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tap,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.postscript)
        },
        check = {
          if (it.resource.alias.length > 15) {
            throw _root_ide_package_.io.vproxy.base.util.exception.XException("tap dev name pattern too long: should <= 15")
          }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TapHandle.add(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tun) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.tun,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mac, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MacHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.postscript)
        },
        check = {
          if (it.resource.alias.length > 15) {
            throw _root_ide_package_.io.vproxy.base.util.exception.XException("tun dev name pattern too long: should <= 15")
          }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.TunHandle.add(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ucli) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ucli,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.pass, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.addr, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AddrHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UserClientHandle.add(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.xdp) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.xdp,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.bpfmap)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.umem, required)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.queue, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.QueueHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.rxringsize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RingSizeHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.rxringsize) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.txringsize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RingSizeHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.txringsize) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mode) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.BPFModeHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.busypoll) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.BusyPollHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.bpfmapkeyselector) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.BPFMapKeySelectorHandle.check(it) }
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.zerocopy)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.XDPHandle.add(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vlan) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vlan,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.VniHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VLanAdaptorHandle.add(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mac, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.MacHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.anno) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.AnnotationsHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.routing) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RoutingHandle.check(it) }
        },
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.checkIpName(it.resource)
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.prepositionResource)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.list,
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource)
        },
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.names(it.resource.parentResource)
          val strNames = names.stream().map { it.formatToIPString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, strNames, utilJoinList(strNames))
        }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.listdetail,
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource)
        },
        exec = {
          val tuples = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.list(it.resource.parentResource)
          val strTuples = tuples.stream().map {
            it.ip.formatToIPString() + " -> mac " + it.mac + " routing " + if (it.routing) {
              "on"
            } else {
              "off"
            } +
                if (it.annotations.isEmpty) "" else " annotations " + it.annotations
          }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(tuples, strTuples, utilJoinList(strTuples))
        }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.update,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.routing) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RoutingHandle.check(it) }
        },
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.checkIpName(it.resource)
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource)
        },
        exec = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.update(it)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult()
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.ip,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.checkIpName(it.resource)
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.prepositionResource)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.IpHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.route) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.route,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.net, required) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.vni) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.via) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.NetworkHandle.check(it) }
        },
        check = {
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.prepositionResource)
          _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RouteHandle.checkCreateRoute(it)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RouteHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.route, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.list,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RouteHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResRelation(
          _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.route, ResRelation(
            _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(
              _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw))),
        action = ActType.listdetail,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val routes = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RouteHandle.list(it.resource.parentResource)
          val strTuples = routes.stream().map { it.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(routes, strTuples, utilJoinList(strTuples))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.route,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.vpc, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.VpcHandle.checkVpcName(it.prepositionResource) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.RouteHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.umem) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.umem,
        action = ActType.addto,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.chunks) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RingSizeHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.chunks) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.fillringsize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RingSizeHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.fillringsize) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.compringsize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.RingSizeHandle.check(it, _root_ide_package_.io.vproxy.app.app.cmd.Param.compringsize) }
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.framesize) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.FrameSizeHandle.check(it) }
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UMemHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.umem, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.list,
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UMemHandle.names(it.resource.parentResource)
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.umem, ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val umems = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UMemHandle.list(it.resource.parentResource)
          val strLs = umems.stream().map { u -> u.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(umems, strLs, utilJoinList(strLs))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.umem,
        action = ActType.removefrom,
        targetRelation = ResRelation(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.sw),
        // will check when executing: check = { UMemHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.UMemHandle.remove(it) }
      )
    }
    it + Res(_root_ide_package_.io.vproxy.app.app.cmd.ResourceType.bpfobj) {
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.bpfobj,
        action = ActType.add,
        params = {
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.path)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.prog)
          it + ResActParam(_root_ide_package_.io.vproxy.app.app.cmd.Param.mode) { _root_ide_package_.io.vproxy.app.app.cmd.handle.param.BPFModeHandle.check(it) }
        },
        flags = {
          it + ResActFlag(_root_ide_package_.io.vproxy.app.app.cmd.Flag.force)
        },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.BPFObjectHandle.add(it) }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.bpfobj,
        action = ActType.list,
        exec = {
          val names = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.BPFObjectHandle.names()
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.bpfobj,
        action = ActType.listdetail,
        exec = {
          val objects = _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.BPFObjectHandle.list()
          val strLs = objects.stream().map { o -> o.toString() }.collect(Collectors.toList())
          _root_ide_package_.io.vproxy.app.app.cmd.CmdResult(objects, strLs, utilJoinList(strLs))
        }
      )
      it + ResAct(
        relation = _root_ide_package_.io.vproxy.app.app.cmd.ResourceType.bpfobj,
        action = ActType.remove,
        check = { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.BPFObjectHandle.preRemoveCheck(it) },
        exec = execUpdate { _root_ide_package_.io.vproxy.app.app.cmd.handle.resource.BPFObjectHandle.remove(it) }
      )
    }
  } // end init
}
