package vproxy.app.app.cmd

import vproxy.app.app.cmd.handle.param.*
import vproxy.app.app.cmd.handle.resource.*
import vproxy.base.dns.Cache
import vproxy.base.util.exception.XException
import java.util.stream.Collectors

@Suppress("NestedLambdaShadowedImplicitParameter")
class ModuleCommands : Commands() {
  init {
    val it = AddHelper(resources)
    it + Res(ResourceType.tl) {
      it + ResAct(
        relation = ResourceType.tl,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
          it + ResActParam(Param.ups, required)
          it + ResActParam(Param.aelg)
          it + ResActParam(Param.elg)
          it + ResActParam(Param.inbuffersize) { InBufferSizeHandle.check(it) }
          it + ResActParam(Param.outbuffersize) { OutBufferSizeHandle.check(it) }
          it + ResActParam(Param.timeout) { TimeoutHandle.check(it) }
          it + ResActParam(Param.protocol)
          it + ResActParam(Param.ck)
          it + ResActParam(Param.secg)
        },
        exec = execUpdate { TcpLBHandle.add(it) },
      )
      it + ResAct(
        relation = ResourceType.tl,
        ActType.list,
      ) {
        val tlNames = TcpLBHandle.names()
        CmdResult(tlNames, tlNames, utilJoinList(tlNames))
      }
      it + ResAct(
        relation = ResourceType.tl,
        action = ActType.listdetail,
      ) {
        val tlRefList = TcpLBHandle.details()
        val tlRefStrList = tlRefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(tlRefList, tlRefStrList, utilJoinList(tlRefList))
      }
      it + ResAct(
        relation = ResourceType.tl,
        action = ActType.update,
        params = {
          it + ResActParam(Param.inbuffersize) { InBufferSizeHandle.check(it) }
          it + ResActParam(Param.outbuffersize) { OutBufferSizeHandle.check(it) }
          it + ResActParam(Param.timeout) { TimeoutHandle.check(it) }
          it + ResActParam(Param.ck)
          it + ResActParam(Param.secg)
        },
        exec = execUpdate { TcpLBHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.tl,
        action = ActType.remove,
        exec = execUpdate { TcpLBHandle.remove(it) }
      )
    }
    it + Res(ResourceType.socks5) {
      it + ResAct(
        relation = ResourceType.socks5,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
          it + ResActParam(Param.ups, required)
          it + ResActParam(Param.aelg)
          it + ResActParam(Param.elg)
          it + ResActParam(Param.inbuffersize) { InBufferSizeHandle.check(it) }
          it + ResActParam(Param.outbuffersize) { OutBufferSizeHandle.check(it) }
          it + ResActParam(Param.timeout) { TimeoutHandle.check(it) }
          it + ResActParam(Param.secg)
        },
        flags = {
          it + ResActFlag(Flag.allownonbackend)
          it + ResActFlag(Flag.denynonbackend)
        },
        exec = execUpdate { Socks5ServerHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.socks5,
        action = ActType.list
      ) {
        val socks5Names = Socks5ServerHandle.names()
        CmdResult(socks5Names, socks5Names, utilJoinList(socks5Names))
      }
      it + ResAct(
        relation = ResourceType.socks5,
        action = ActType.listdetail
      ) {
        val socks5RefList = Socks5ServerHandle.details()
        val socks5RefStrList = socks5RefList.stream().map { it.toString() }.collect(Collectors.toList())
        CmdResult(socks5RefList, socks5RefStrList, utilJoinList(socks5RefList))
      }
      it + ResAct(
        relation = ResourceType.socks5,
        action = ActType.update,
        params = {
          it + ResActParam(Param.inbuffersize) { InBufferSizeHandle.check(it) }
          it + ResActParam(Param.outbuffersize) { OutBufferSizeHandle.check(it) }
          it + ResActParam(Param.timeout) { TimeoutHandle.check(it) }
          it + ResActParam(Param.secg)
        },
        flags = {
          it + ResActFlag(Flag.allownonbackend)
          it + ResActFlag(Flag.denynonbackend)
        },
        exec = execUpdate { Socks5ServerHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.socks5,
        action = ActType.remove,
        exec = execUpdate { Socks5ServerHandle.remove(it) }
      )
    }
    it + Res(ResourceType.dns) {
      it + ResAct(
        relation = ResourceType.dns,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
          it + ResActParam(Param.ups, required)
          it + ResActParam(Param.elg)
          it + ResActParam(Param.ttl) { TTLHandle.check(it) }
          it + ResActParam(Param.secg)
        },
        exec = execUpdate { DNSServerHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.dns,
        action = ActType.list,
        exec = {
          val dnsServerNames = DNSServerHandle.names()
          CmdResult(dnsServerNames, dnsServerNames, utilJoinList(dnsServerNames))
        }
      )
      it + ResAct(
        relation = ResourceType.dns,
        action = ActType.listdetail,
        exec = {
          val dnsServerRefList = DNSServerHandle.details()
          val dnsServerRefStrList = dnsServerRefList.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(dnsServerRefStrList, dnsServerRefStrList, utilJoinList(dnsServerRefList))
        }
      )
      it + ResAct(
        relation = ResourceType.dns,
        action = ActType.update,
        params = {
          it + ResActParam(Param.ttl) { TTLHandle.check(it) }
          it + ResActParam(Param.secg)
        },
        exec = execUpdate { DNSServerHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.dns,
        action = ActType.remove,
        exec = execUpdate { DNSServerHandle.remove(it) }
      )
    }
    it + Res(ResourceType.elg) {
      it + ResAct(
        relation = ResourceType.elg,
        action = ActType.add,
        params = {
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        exec = execUpdate { EventLoopGroupHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.elg,
        action = ActType.list,
        exec = {
          val elgNames = EventLoopGroupHandle.names()
          CmdResult(elgNames, elgNames, utilJoinList(elgNames))
        }
      )
      it + ResAct(
        relation = ResourceType.elg,
        action = ActType.listdetail,
        exec = {
          val elgs = EventLoopGroupHandle.details()
          val elgStrs = elgs.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(elgs, elgStrs, utilJoinList(elgs))
        }
      )
      it + ResAct(
        relation = ResourceType.elg,
        action = ActType.remove,
        check = { EventLoopGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { EventLoopGroupHandle.remvoe(it) }
      )
    }
    it + Res(ResourceType.ups) {
      it + ResAct(
        relation = ResourceType.ups,
        action = ActType.add,
        exec = execUpdate { UpstreamHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.ups,
        action = ActType.list,
        exec = {
          val upsNames = UpstreamHandle.names()
          CmdResult(upsNames, upsNames, utilJoinList(upsNames))
        }
      )
      it + ResAct(
        relation = ResourceType.ups,
        action = ActType.listdetail,
        exec = {
          val upsNames = UpstreamHandle.names()
          CmdResult(upsNames, upsNames, utilJoinList(upsNames))
        }
      )
      it + ResAct(
        relation = ResourceType.ups,
        action = ActType.remove,
        check = { UpstreamHandle.preRemoveCheck(it) },
        exec = execUpdate { UpstreamHandle.remove(it) }
      )
    }
    it + Res(ResourceType.sg) {
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.add,
        params = {
          it + ResActParam(Param.timeout, required)
          it + ResActParam(Param.period, required)
          it + ResActParam(Param.up, required)
          it + ResActParam(Param.down, required)
          it + ResActParam(Param.protocol)
          it + ResActParam(Param.meth) { MethHandle.get(it, "") }
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
          it + ResActParam(Param.elg)
        },
        check = { HealthCheckHandle.getHealthCheckConfig(it) },
        exec = execUpdate { ServerGroupHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.ups),
        params = {
          it + ResActParam(Param.weight) { WeightHandle.check(it) }
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        exec = execUpdate { ServerGroupHandle.attach(it) }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.list,
        exec = {
          val sgNames = ServerGroupHandle.names()
          CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.listdetail,
        exec = {
          val refs = ServerGroupHandle.details()
          val refStrList = refs.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(refs, refStrList, utilJoinList(refStrList))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.sg, ResRelation(ResourceType.ups)),
        action = ActType.list,
        exec = {
          val sgNames = ServerGroupHandle.names(it.resource.parentResource)
          CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.sg, ResRelation(ResourceType.ups)),
        action = ActType.listdetail,
        exec = {
          val refs = ServerGroupHandle.details(it.resource.parentResource)
          val refStrList = refs.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(refs, refStrList, utilJoinList(refStrList))
        }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.update,
        params = {
          it + ResActParam(Param.timeout) { HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(Param.period) { HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(Param.up) { HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(Param.down) { HealthCheckHandle.getHealthCheckConfig(it) }
          it + ResActParam(Param.protocol)
          it + ResActParam(Param.meth) { MethHandle.get(it, "") }
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        exec = execUpdate { ServerGroupHandle.update(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.sg, ResRelation(ResourceType.ups)),
        action = ActType.update,
        params = {
          it + ResActParam(Param.weight) { WeightHandle.check(it) }
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        exec = execUpdate { ServerGroupHandle.updateInUpstream(it) }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.remove,
        check = { ServerGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { ServerGroupHandle.remove(it) }
      )
      it + ResAct(
        relation = ResourceType.sg,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.ups),
        exec = execUpdate { ServerGroupHandle.detach(it) }
      )
    }
    it + Res(ResourceType.el) {
      it + ResAct(
        relation = ResourceType.el,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.elg),
        params = {
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        exec = execUpdate { EventLoopHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.el, ResRelation(ResourceType.elg)),
        action = ActType.list,
        exec = {
          val elNames = EventLoopHandle.names(it.resource.parentResource)
          CmdResult(elNames, elNames, utilJoinList(elNames))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.el, ResRelation(ResourceType.elg)),
        action = ActType.listdetail,
        exec = {
          val els = EventLoopHandle.detail(it.resource.parentResource)
          val elStrList = els.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(els, elStrList, utilJoinList(els))
        }
      )
      it + ResAct(
        relation = ResourceType.el,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.elg),
        exec = execUpdate { EventLoopHandle.remove(it) }
      )
    }
    it + Res(ResourceType.svr) {
      it + ResAct(
        relation = ResourceType.svr,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sg),
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
          it + ResActParam(Param.weight) { WeightHandle.check(it) }
        },
        exec = execUpdate { ServerHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.svr, ResRelation(ResourceType.sg)),
        action = ActType.list,
        exec = {
          val serverNames = ServerHandle.names(it.resource.parentResource)
          CmdResult(serverNames, serverNames, utilJoinList(serverNames))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.svr, ResRelation(ResourceType.sg)),
        action = ActType.listdetail,
        exec = {
          val svrRefList = ServerHandle.detail(it.resource.parentResource)
          val svrRefStrList = svrRefList.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(svrRefList, svrRefStrList, utilJoinList(svrRefList))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.svr, ResRelation(ResourceType.sg)),
        action = ActType.update,
        params = {
          it + ResActParam(Param.weight) { WeightHandle.check(it) }
        },
        exec = execUpdate { ServerHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.svr,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sg),
        exec = execUpdate { ServerHandle.remove(it) }
      )
    }
    it + Res(ResourceType.secg) {
      it + ResAct(
        relation = ResourceType.secg,
        action = ActType.add,
        params = {
          it + ResActParam(Param.secgrdefault, required) { SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { SecurityGroupHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.secg,
        action = ActType.list,
        exec = {
          val sgNames = SecurityGroupHandle.names()
          CmdResult(sgNames, sgNames, utilJoinList(sgNames))
        }
      )
      it + ResAct(
        relation = ResourceType.secg,
        action = ActType.listdetail,
        exec = {
          val secg = SecurityGroupHandle.detail()
          val secgStrList = secg.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(secgStrList, secgStrList, utilJoinList(secg))
        }
      )
      it + ResAct(
        relation = ResourceType.secg,
        action = ActType.update,
        params = {
          it + ResActParam(Param.secgrdefault) { SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { SecurityGroupHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.secg,
        action = ActType.remove,
        check = { SecurityGroupHandle.preRemoveCheck(it) },
        exec = execUpdate { SecurityGroupHandle.remove(it) }
      )
    }
    it + Res(ResourceType.secgr) {
      it + ResAct(
        relation = ResourceType.secgr,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.secg),
        params = {
          it + ResActParam(Param.net, required) { NetworkHandle.check(it) }
          it + ResActParam(Param.protocol, required) { ProtocolHandle.check(it) }
          it + ResActParam(Param.portrange, required) { PortRangeHandle.check(it) }
          it + ResActParam(Param.secgrdefault, required) { SecGRDefaultHandle.check(it) }
        },
        exec = execUpdate { SecurityGroupRuleHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.secgr, ResRelation(ResourceType.secg)),
        action = ActType.list,
        exec = {
          val ruleNames = SecurityGroupRuleHandle.names(it.resource.parentResource)
          CmdResult(ruleNames, ruleNames, utilJoinList(ruleNames))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.secgr, ResRelation(ResourceType.secg)),
        action = ActType.listdetail,
        exec = {
          val rules = SecurityGroupRuleHandle.detail(it.resource.parentResource)
          val ruleStrList = rules.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(rules, ruleStrList, utilJoinList(rules))
        }
      )
      it + ResAct(
        relation = ResourceType.secgr,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.secg),
        exec = execUpdate { SecurityGroupRuleHandle.remove(it) }
      )
    }
    it + Res(ResourceType.ck) {
      it + ResAct(
        relation = ResourceType.ck,
        action = ActType.add,
        params = {
          it + ResActParam(Param.cert, required)
          it + ResActParam(Param.key, required)
        },
        exec = execUpdate { CertKeyHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.ck,
        action = ActType.list,
        exec = {
          val names = CertKeyHandle.names()
          CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResourceType.ck,
        action = ActType.listdetail,
        exec = {
          val names = CertKeyHandle.names()
          CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResourceType.ck,
        action = ActType.remove,
        check = { CertKeyHandle.preRemoveCheck(it) },
        exec = execUpdate { CertKeyHandle.remove(it) }
      )
    }
    it + Res(ResourceType.dnscache) {
      it + ResAct(
        relation = ResourceType.dnscache,
        action = ActType.list,
        check = { ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = {
          val cacheCnt = DnsCacheHandle.count()
          CmdResult(cacheCnt, cacheCnt, "" + cacheCnt)
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.dnscache, ResRelation(ResourceType.resolver)),
        action = ActType.listdetail,
        check = { ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = {
          val caches = DnsCacheHandle.detail()
          val cacheStrList = caches.stream().map { c: Cache ->
            listOf(
              c.host,
              c.ipv4.stream().map { it.formatToIPString() }
                .collect(Collectors.toList()),
              c.ipv6.stream().map { it.formatToIPString() }
                .collect(Collectors.toList())
            )
          }.collect(Collectors.toList())
          CmdResult(caches, cacheStrList, utilJoinList(caches))
        }
      )
      it + ResAct(
        relation = ResourceType.dnscache,
        action = ActType.remove,
        check = { ResolverHandle.checkResolver(it.resource.parentResource) },
        exec = execUpdate { DnsCacheHandle.remove(it) }
      )
    }
    it + Res(ResourceType.sw) {
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.add,
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
          it + ResActParam(Param.mactabletimeout) { TimeoutHandle.check(it, Param.mactabletimeout) }
          it + ResActParam(Param.arptabletimeout) { TimeoutHandle.check(it, Param.arptabletimeout) }
          it + ResActParam(Param.elg)
          it + ResActParam(Param.secg)
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        exec = execUpdate { SwitchHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.list,
        exec = {
          val swNames = SwitchHandle.names()
          CmdResult(swNames, swNames, utilJoinList(swNames))
        }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.listdetail,
        exec = {
          val swRefList = SwitchHandle.details()
          val swRefStrList = swRefList.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(swRefList, swRefStrList, utilJoinList(swRefList))
        }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.update,
        params = {
          it + ResActParam(Param.mactabletimeout) { TimeoutHandle.check(it, Param.mactabletimeout) }
          it + ResActParam(Param.arptabletimeout) { TimeoutHandle.check(it, Param.arptabletimeout) }
          it + ResActParam(Param.secg)
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        exec = execUpdate { SwitchHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.remove,
        exec = execUpdate { SwitchHandle.remove(it) }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.addr) { AddrHandle.check(it) }
        },
        flags = {
          it + ResActFlag(Flag.noswitchflag)
        },
        exec = execUpdate { SwitchHandle.attach(it) }
      )
      it + ResAct(
        relation = ResourceType.sw,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        exec = execUpdate { SwitchHandle.detach(it) }
      )
    }
    it + Res(ResourceType.vpc) {
      it + ResAct(
        relation = ResourceType.vpc,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.v4net, required) {
            NetworkHandle.check(it, Param.v4net)
            val net = NetworkHandle.get(it, Param.v4net)
            if (net.ip.address.size != 4) {
              throw XException("invalid argument " + Param.v4net + ": not ipv4 network: " + net)
            }
          }
          it + ResActParam(Param.v6net) {
            NetworkHandle.check(it, Param.v6net)
            val net = NetworkHandle.get(it, Param.v6net)
            if (net.ip.address.size != 16) {
              throw XException("invalid argument " + Param.v6net + ": not ipv6 network: " + net)
            }
          }
          it + ResActParam(Param.anno) { AnnotationsHandle.check(it) }
        },
        check = { VpcHandle.checkVpcName(it.resource) },
        exec = execUpdate { VpcHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        action = ActType.list,
        check = { VpcHandle.checkVpcName(it.resource) },
        exec = {
          val vpcLs = VpcHandle.list(it.resource.parentResource)
          val ls = vpcLs.stream().map { it.vpc }.collect(Collectors.toList())
          CmdResult(vpcLs, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        action = ActType.listdetail,
        check = { VpcHandle.checkVpcName(it.resource) },
        exec = {
          val vpcLs = VpcHandle.list(it.resource.parentResource)
          val ls = vpcLs.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(vpcLs, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = ResourceType.vpc,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        check = { VpcHandle.checkVpcName(it.resource) },
        exec = execUpdate { VpcHandle.remove(it) }
      )
    }
    it + Res(ResourceType.iface) {
      it + ResAct(
        relation = ResRelation(ResourceType.iface, ResRelation(ResourceType.sw)),
        action = ActType.list,
        exec = {
          val cnt = IfaceHandle.count(it.resource.parentResource)
          CmdResult(cnt, cnt, "" + cnt)
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.iface, ResRelation(ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val ifaces = IfaceHandle.list(it.resource.parentResource)
          val ls = ifaces.stream().map { it.toString() + " " + it.paramsToString() }
            .collect(Collectors.toList())
          CmdResult(ifaces, ls, utilJoinList(ls))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.iface, ResRelation(ResourceType.sw)),
        action = ActType.update,
        params = {
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        exec = execUpdate { IfaceHandle.update(it) }
      )
    }
    it + Res(ResourceType.arp) {
      it + ResAct(
        relation = ResRelation(ResourceType.arp, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.list,
        check = { VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val cnt = ArpHandle.count(it.resource.parentResource)
          CmdResult(cnt, cnt, "" + cnt)
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.arp, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.listdetail,
        check = { VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val arpLs = ArpHandle.list(it.resource.parentResource)
          val ls = arpLs.stream().map { it.toString(arpLs) }.collect(Collectors.toList())
          CmdResult(arpLs, ls, utilJoinList(ls))
        }
      )
    }
    it + Res(ResourceType.user) {
      it + ResAct(
        relation = ResourceType.user,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.pass, required)
          it + ResActParam(Param.vni, required) { VniHandle.check(it) }
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        exec = execUpdate { UserHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.user, ResRelation(ResourceType.sw)),
        action = ActType.list,
        exec = {
          val users = UserHandle.names(it.resource.parentResource)
          CmdResult(users, users, utilJoinList(users))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.user, ResRelation(ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val userInfoList = UserHandle.list(it.resource.parentResource)
          val strList = userInfoList.stream().map { it.toString() }
            .collect(Collectors.toList())
          CmdResult(userInfoList, strList, utilJoinList(strList))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.user, ResRelation(ResourceType.sw)),
        action = ActType.update,
        params = {
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        exec = execUpdate { UserHandle.update(it) }
      )
      it + ResAct(
        relation = ResourceType.user,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        exec = execUpdate { UserHandle.remove(it) }
      )
    }
    it + Res(ResourceType.tap) {
      it + ResAct(
        relation = ResourceType.tap,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.vni, required) { VniHandle.check(it) }
          it + ResActParam(Param.postscript)
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        check = {
          if (it.resource.alias.length > 15) {
            throw XException("tap dev name pattern too long: should <= 15")
          }
        },
        exec = execUpdate { TapHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.tap,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        exec = execUpdate { TapHandle.remove(it) }
      )
    }
    it + Res(ResourceType.tun) {
      it + ResAct(
        relation = ResourceType.tun,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.vni, required) { VniHandle.check(it) }
          it + ResActParam(Param.mac, required) { MacHandle.check(it) }
          it + ResActParam(Param.postscript)
          it + ResActParam(Param.mtu) { MTUHandle.check(it) }
          it + ResActParam(Param.flood) { FloodHandle.check(it) }
        },
        check = {
          if (it.resource.alias.length > 15) {
            throw XException("tun dev name pattern too long: should <= 15")
          }
        },
        exec = execUpdate { TunHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.tun,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        exec = execUpdate { TunHandle.remove(it) }
      )
    }
    it + Res(ResourceType.ucli) {
      it + ResAct(
        relation = ResourceType.ucli,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.pass, required)
          it + ResActParam(Param.vni, required) { VniHandle.check(it) }
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
        },
        exec = execUpdate { UserClientHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.ucli,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.addr, required) { AddrHandle.check(it) }
        },
        exec = execUpdate { UserClientHandle.forceRemove(it) }
      )
    }
    it + Res(ResourceType.xdp) {
      it + ResAct(
        relation = ResourceType.xdp,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.nic, required)
          it + ResActParam(Param.bpfmap, required)
          it + ResActParam(Param.umem, required)
          it + ResActParam(Param.queue, required) { QueueHandle.check(it) }
          it + ResActParam(Param.rxringsize) { RingSizeHandle.check(it, Param.rxringsize) }
          it + ResActParam(Param.txringsize) { RingSizeHandle.check(it, Param.txringsize) }
          it + ResActParam(Param.mode) { BPFModeHandle.check(it) }
          it + ResActParam(Param.vni, required) { VniHandle.check(it) }
          it + ResActParam(Param.bpfmapkeyselector) { BPFMapKeySelectorHandle.check(it) }
        },
        flags = {
          it + ResActFlag(Flag.zerocopy)
        },
        exec = execUpdate { XDPHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.xdp,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        exec = execUpdate { XDPHandle.remove(it) }
      )
    }
    it + Res(ResourceType.ip) {
      it + ResAct(
        relation = ResourceType.ip,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        params = {
          it + ResActParam(Param.mac, required) { MacHandle.check(it) }
        },
        check = {
          IpHandle.checkIpName(it.resource)
          VpcHandle.checkVpcName(it.prepositionResource)
        },
        exec = execUpdate { IpHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.ip, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.list,
        check = {
          IpHandle.checkIpName(it.resource)
          VpcHandle.checkVpcName(it.resource.parentResource)
        },
        exec = {
          val names = IpHandle.names(it.resource.parentResource)
          val strNames = names.stream().map { it.formatToIPString() }.collect(Collectors.toList())
          CmdResult(names, strNames, utilJoinList(strNames))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.ip, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.listdetail,
        check = {
          IpHandle.checkIpName(it.resource)
          VpcHandle.checkVpcName(it.resource.parentResource)
        },
        exec = {
          val tuples = IpHandle.list(it.resource.parentResource)
          val strTuples = tuples.stream().map {
            it.ip.formatToIPString() + " -> mac " + it.mac +
                if (it.annotations.isEmpty) "" else " annotations " + it.annotations
          }.collect(Collectors.toList())
          CmdResult(tuples, strTuples, utilJoinList(strTuples))
        }
      )
      it + ResAct(
        relation = ResourceType.ip,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        check = {
          IpHandle.checkIpName(it.resource)
          VpcHandle.checkVpcName(it.prepositionResource)
        },
        exec = execUpdate { IpHandle.remove(it) }
      )
    }
    it + Res(ResourceType.route) {
      it + ResAct(
        relation = ResourceType.route,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        params = {
          it + ResActParam(Param.net, required) { NetworkHandle.check(it) }
          it + ResActParam(Param.vni) { NetworkHandle.check(it) }
          it + ResActParam(Param.via) { NetworkHandle.check(it) }
        },
        check = {
          VpcHandle.checkVpcName(it.prepositionResource)
          RouteHandle.checkCreateRoute(it)
        },
        exec = execUpdate { RouteHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.route, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.list,
        check = { VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val names = RouteHandle.names(it.resource.parentResource)
          CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.route, ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw))),
        action = ActType.listdetail,
        check = { VpcHandle.checkVpcName(it.resource.parentResource) },
        exec = {
          val routes = RouteHandle.list(it.resource.parentResource)
          val strTuples = routes.stream().map { it.toString() }.collect(Collectors.toList())
          CmdResult(routes, strTuples, utilJoinList(strTuples))
        }
      )
      it + ResAct(
        relation = ResourceType.route,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.vpc, ResRelation(ResourceType.sw)),
        check = { VpcHandle.checkVpcName(it.prepositionResource) },
        exec = execUpdate { RouteHandle.remove(it) }
      )
    }
    it + Res(ResourceType.umem) {
      it + ResAct(
        relation = ResourceType.umem,
        action = ActType.addto,
        targetRelation = ResRelation(ResourceType.sw),
        params = {
          it + ResActParam(Param.chunks) { RingSizeHandle.check(it, Param.chunks) }
          it + ResActParam(Param.fillringsize) { RingSizeHandle.check(it, Param.fillringsize) }
          it + ResActParam(Param.compringsize) { RingSizeHandle.check(it, Param.compringsize) }
          it + ResActParam(Param.framesize) { FrameSizeHandle.check(it) }
        },
        exec = execUpdate { UMemHandle.add(it) }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.umem, ResRelation(ResourceType.sw)),
        action = ActType.list,
        exec = {
          val names = UMemHandle.names(it.resource.parentResource)
          CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResRelation(ResourceType.umem, ResRelation(ResourceType.sw)),
        action = ActType.listdetail,
        exec = {
          val umems = UMemHandle.list(it.resource.parentResource)
          val strLs = umems.stream().map { u -> u.toString() }.collect(Collectors.toList())
          CmdResult(umems, strLs, utilJoinList(strLs))
        }
      )
      it + ResAct(
        relation = ResourceType.umem,
        action = ActType.removefrom,
        targetRelation = ResRelation(ResourceType.sw),
        // will check when executing: check = { UMemHandle.preRemoveCheck(it) },
        exec = execUpdate { UMemHandle.remove(it) }
      )
    }
    it + Res(ResourceType.bpfobj) {
      it + ResAct(
        relation = ResourceType.bpfobj,
        action = ActType.add,
        params = {
          it + ResActParam(Param.path, true)
          it + ResActParam(Param.prog, true)
          it + ResActParam(Param.mode) { BPFModeHandle.check(it) }
        },
        flags = {
          it + ResActFlag(Flag.force)
        },
        exec = execUpdate { BPFObjectHandle.add(it) }
      )
      it + ResAct(
        relation = ResourceType.bpfobj,
        action = ActType.list,
        exec = {
          val names = BPFObjectHandle.names()
          CmdResult(names, names, utilJoinList(names))
        }
      )
      it + ResAct(
        relation = ResourceType.bpfobj,
        action = ActType.listdetail,
        exec = {
          val objects = BPFObjectHandle.list()
          val strLs = objects.stream().map { o -> o.toString() }.collect(Collectors.toList())
          CmdResult(objects, strLs, utilJoinList(strLs))
        }
      )
      it + ResAct(
        relation = ResourceType.bpfobj,
        action = ActType.remove,
        exec = execUpdate { BPFObjectHandle.remove(it) }
      )
    }
  } // end init

  private fun utilJoinList(ls: List<*>): String {
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
