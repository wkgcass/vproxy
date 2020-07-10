function pageMain() {
  var checkboxes = [
    'socks5',
    'httpconnect',
    'ss',
    'dns',
    'pac',
    'gateway',
    'autosign',
    'directrelay',
    'directrelayadvanced',
    'hc',
    'certauth',
  ];
  var data = {
    page: 'functionality',
    socks5: {
      events: {
        mouseover: false,
      },
      enabled: true,
      listen: 1080,
    },
    httpconnect: {
      events: {
        mouseover: false,
      },
      enabled: false,
      listen: 8080,
    },
    ss: {
      events: {
        mouseover: false,
      },
      enabled: false,
      listen: 8379,
      password: '123456'
    },
    dns: {
      events: {
        mouseover: false,
      },
      enabled: false,
      listen: 53,
    },
    pac: {
      events: {
        mouseover: false,
      },
      enabled: false,
      listen: 20080,
    },
    gateway: {
      events: {
        mouseover: false,
      },
      enabled: false,
    },
    autosign: {
      events: {
        mouseover: false,
      },
      enabled: false,
      cacert: '~/vproxy-ca/ca.key',
      cakey: '~/vproxy-ca/ca.crt',
    },
    directrelay: {
      events: {
        mouseover: false,
      },
      enabled: false,
    },
    directrelayadvanced: {
      events: {
        mouseover: false,
      },
      enabled: false,
      network: '100.96.0.0/11',
      listen: '127.0.0.1:8888',
      timeout: 0,
    },
    serverUser: '',
    serverPass: '',
    hc: {
      events: {
        mouseover: false,
      },
      enabled: true,
    },
    certauth: {
      events: {
        mouseover: false,
      },
      enabled: false,
    },
    lastServerGroupIndex: 0,
    serverGroupList: [
      {
        index: 0,
        name: 'DEFAULT',
        lastServerIndex: 0,
        serverList: [
          {
            index: 0,
            protocol: 'websockss',
            kcp: {
              events: {
                mouseover: false,
              },
              enabled: false,
            },
            ip: '',
            port: 443,
          },
        ],
        lastProxyRuleIndex: 3,
        proxyRuleList: [
          {
            index: 0,
            rule: 'pixiv.net',
            white: {
              events: {
                mouseover: false,
              },
              enabled: false,
            }
          },
          {
            index: 1,
            rule: 'baidu.com',
            white: {
              events: {
                mouseover: false,
              },
              enabled: true,
            }
          },
          {
            index: 2,
            rule: '/.*google.*/',
            white: {
              events: {
                mouseover: false,
              },
              enabled: false,
            },
          },
          {
            index: 3,
            rule: '[https://gitee.com/wkgcass/gfwlist/raw/master/gfwlist.txt]',
            white: {
              events: {
                mouseover: false,
              },
              enabled: false,
            },
          },
        ],
        lastDnsRuleIndex: 0,
        dnsRuleList: [],
      },
    ],
    lastHttpsSniErasureRuleIndex: -1,
    httpsSniErasureRuleList: [],
    generateErrors: [],
    generated: '',
    running: false,
    starting: false,
    stopping: false,
    output: '',
  };

  var vue = new Vue({
    el: '#app',
    data: data,
    methods: {
      handleCheckboxMouseEnterEvent: handleCheckboxMouseEnterEvent,
      handleCheckboxMouseLeaveEvent: handleCheckboxMouseLeaveEvent,
      handleCheckboxClick: handleCheckboxClick,
      deleteGroup: deleteGroup,
      addGroup: addGroup,
      deleteServer: deleteServer,
      addServer: addServer,
      deleteRule: deleteRule,
      addRule: addRule,
      deleteDnsRule: deleteDnsRule,
      addDnsRule: addDnsRule,
      deleteHttpsSniErasureRule: deleteHttpsSniErasureRule,
      addHttpsSniErasureRule: addHttpsSniErasureRule,
      doGenerate: doGenerate,
      run: run,
      stop: stop,
    },
    mounted: function() {
      $('.ui.checkbox').checkbox();
      for (var i = 0; i < checkboxes.length; ++i) {
        var x = checkboxes[i];
        if (data[x].enabled) {
          $('#' + x + '-checkbox').checkbox('set checked');
        }
      }
      for (var i = 0; i < data.serverGroupList.length; ++i) {
        var group = data.serverGroupList[i];
        for (var j = 0; j < group.serverList.length; ++j) {
          var svr = group.serverList[j];
          if (svr.kcp.enabled) {
            $('#kcp-g-' + group.index + '-s-' + svr.index + '-checkbox').checkbox('set checked');
          }
        }
        for (var j = 0; j < group.proxyRuleList.length; ++j) {
          var rule = group.proxyRuleList[j];
          if (rule.white.enabled) {
            $('#white-g-' + group.index + '-r-' + rule.index + '-checkbox').checkbox('set checked');
          }
        }
      }
      $('select.dropdown').dropdown();

      $('#download').click(function() {
        var element = document.createElement('a');
        element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(data.generated));
        element.setAttribute('download', 'vpws-agent.conf');

        element.style.display = 'none';
        document.body.appendChild(element);

        element.click();

        document.body.removeChild(element);
      });
    },
  });

  function handleCheckboxMouseEnterEvent(obj) {
    obj.events.mouseover = true;
  }

  function handleCheckboxMouseLeaveEvent(obj) {
    obj.events.mouseover = false;
  }

  function handleCheckboxClick(obj) {
    if (!obj.events.mouseover) {
      return;
    }
    obj.enabled = !obj.enabled;
  }

  function _findGroup(index) {
    for (var i = 0; i < data.serverGroupList.length; ++i) {
      var group = data.serverGroupList[i];
      if (group.index === index) {
        return {
          i: i,
          group: group
        };
      }
    }
    return {
      i: -1
    };
  }

  function deleteGroup(index) {
    console.log('delete group ' + index);

    var groupO = _findGroup(index);
    if (groupO.i === -1) return;
    data.serverGroupList.splice(groupO.i, 1);
  }

  function addGroup() {
    console.log('add group');

    var index = ++data.lastServerGroupIndex;
    data.serverGroupList.push({
      index: index,
      name: '',
      lastServerIndex: -1,
      serverList: [],
      lastProxyRuleIndex: -1,
      proxyRuleList: [],
      lastDnsRuleIndex: -1,
      dnsRuleList: [],
    });
  }

  function deleteServer(groupIndex, serverIndex) {
    console.log('delete server ' + groupIndex + ', ' + serverIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;
    for (var i = 0; i < group.serverList.length; ++i) {
      var svr = group.serverList[i];
      if (svr.index === serverIndex) {
        group.serverList.splice(i, 1);
        return;
      }
    }
  }

  function addServer(groupIndex) {
    console.log('add server ' + groupIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;

    var index = ++group.lastServerIndex;
    group.serverList.push({
      index: index,
      protocol: 'websockss',
      kcp: {
        events: {
          mouseover: false,
        },
        enabled: false,
      },
      ip: '',
      port: 443,
    });
    vue.$nextTick(function() {
      $('#protocol-g-' + groupIndex + '-s-' + index + '-select').dropdown();
      $('#kcp-g-' + groupIndex + '-s-' + index + '-checkbox').checkbox();
    });
  }

  function deleteRule(groupIndex, ruleIndex) {
    console.log('delete rule ' + groupIndex + ", " + ruleIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;
    for (var i = 0; i < group.proxyRuleList.length; ++i) {
      var rule = group.proxyRuleList[i];
      if (rule.index === ruleIndex) {
        group.proxyRuleList.splice(i, 1);
        return;
      }
    }
  }

  function addRule(groupIndex) {
    console.log('add rule ' + groupIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;

    var index = ++group.lastProxyRuleIndex;
    group.proxyRuleList.push({
      index: index,
      rule: '',
      white: {
        events: {
          mouseover: false,
        },
        enabled: false,
      },
    });
    vue.$nextTick(function() {
      $('#white-g-' + groupIndex + '-r-' + index + '-checkbox').checkbox();
    });
  }

  function deleteDnsRule(groupIndex, ruleIndex) {
    console.log('delete dns rule ' + groupIndex + ", " + ruleIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;
    for (var i = 0; i < group.dnsRuleList.length; ++i) {
      var rule = group.dnsRuleList[i];
      if (rule.index === ruleIndex) {
        group.dnsRuleList.splice(i, 1);
        return;
      }
    }
  }

  function addDnsRule(groupIndex) {
    console.log('add dns rule ' + groupIndex);

    var groupO = _findGroup(groupIndex);
    if (groupO.i === -1) return;
    var group = groupO.group;

    var index = ++group.lastDnsRuleIndex;
    group.dnsRuleList.push({
      index: index,
      rule: '',
    });
  }

  function deleteHttpsSniErasureRule(ruleIndex) {
    console.log('delete https-sni-erasure rule ' + ruleIndex);

    for (var i = 0; i < data.httpsSniErasureRuleList.length; ++i) {
      var rule = data.httpsSniErasureRuleList[i];
      if (rule.index === ruleIndex) {
        data.httpsSniErasureRuleList.splice(i, 1);
        return;
      }
    }
  }

  function addHttpsSniErasureRule() {
    console.log('add https-sni-erasure rule');

    var index = ++data.lastHttpsSniErasureRuleIndex;
    data.httpsSniErasureRuleList.push({
      index: index,
      rule: '',
    });
  }

  function _validPortNumber(n) {
    var x = '' + n;
    if (x.indexOf('.') !== -1) {
      return false;
    }
    n = parseInt(n);
    return n >= 1 && n <= 65535;
  }

  function doGenerate() {
    console.log('generate');

    data.page = 'generate';
    data.generateErrors = [];

    var text = '# generated by vpws-ui\n';
    text += '\n';
    if (data.socks5.enabled) {
      if (!_validPortNumber(data.socks5.listen)) {
        data.generateErrors.push('socks5监听端口错误');
      }
      text += 'agent.socks5.listen ' + data.socks5.listen + '\n';
    }
    if (data.httpconnect.enabled) {
      if (!_validPortNumber(data.httpconnect.listen)) {
        data.generateErrors.push('http-connect监听端口错误');
      }
      text += 'agent.httpconnect.listen ' + data.httpconnect.listen + '\n';
    }
    if (data.ss.enabled) {
      if (!_validPortNumber(data.ss.listen)) {
        data.generateErrors.push('shadowsocks监听端口错误');
      }
      if (!data.ss.password) {
        data.generateErrors.push('shadowsocks密码未填写');
      }
      text += 'agent.ss.listen ' + data.ss.listen + '\n';
      text += 'agent.ss.password ' + data.ss.password + '\n';
    }
    if (data.dns.enabled) {
      if (!_validPortNumber(data.dns.listen)) {
        data.generateErrors.push('dns监听端口错误');
      }
      text += 'agent.dns.listen ' + data.dns.listen + '\n';
    }
    if (data.pac.enabled) {
      if (!_validPortNumber(data.pac.listen)) {
        data.generateErrors.push('pac服务监听端口错误');
      }
      text += 'agent.gateway.pac.listen ' + data.pac.listen + '\n';
    }
    if (data.gateway.enabled) {
      text += 'agent.gateway on\n';
    } else {
      text += 'agent.gateway off\n';
    }
    if (data.autosign.enabled) {
      if (!data.autosign.cacert) {
        data.generateErrors.push('CA根证书文件路径未填写');
      }
      if (!data.autosign.cakey) {
        data.generateErrors.push('私钥路径未填写');
      }
      text += 'agent.auto-sign ' + data.autosign.cacert + ' ' + data.autosign.cakey + '\n';
    }
    if (data.directrelay.enabled) {
      text += 'agent.direct-relay on\n';
      if (data.directrelayadvanced.enabled) {
        if (isNaN(parseInt(data.directrelayadvanced.timeout))) {
          data.generateErrors.push('直接中继高级功能的超时时间应当是数字');
        }
        if (data.directrelayadvanced.network === "") {
          data.generateErrors.push('直接中继高级功能的网段未填写');
        }
        if (data.directrelayadvanced.listen === "") {
          data.generateErrors.push('直接中继高级功能的监听地址未填写');
        }
        text += 'agent.direct-relay.network ' + data.directrelayadvanced.network + '\n';
        text += 'agent.direct-relay.listen' + data.directrelayadvanced.listen + '\n';
        text += 'agent.direct-relay.timeout ' + data.directrelayadvanced.timeout + '\n';
      }
    }
    text += '\n';
    if (!data.serverUser) {
      data.generateErrors.push('用户名未填写');
    }
    if (!data.serverPass) {
      data.generateErrors.push('密码未填写');
    }
    text += 'proxy.server.auth ' + data.serverUser + ':' + data.serverPass + '\n';
    text += '\n';
    if (data.hc.enabled) {
      text += 'proxy.server.hc on\n';
    } else {
      text += 'proxy.server.hc off\n';
    }
    if (data.certauth.enabled) {
      text += 'agent.cert.verify on\n';
    } else {
      text += 'agent.cert.verify off\n';
    }
    text += 'agent.pool 4\n';
    text += '\n';
    for (var i = 0; i < data.serverGroupList.length; ++i) {
      var group = data.serverGroupList[i];

      text += 'proxy.server.list.start ' + group.name + '\n';
      for (var j = 0; j < group.serverList.length; ++j) {
        var svr = group.serverList[j];
        if (!svr.ip) {
          data.generateErrors.push('服务器地址未填写: ' + i + ',' + j);
        }
        if (!svr.port) {
          data.generateErrors.push('服务器端口未填写: ' + i + ',' + j);
        }
        text += '    ' + svr.protocol + ':' + (svr.kcp.enabled ? 'kcp:' : '') + '//' + svr.ip + ':' + svr.port + '\n';
      }
      text += 'proxy.server.list.end\n';
      text += '\n';
      text += 'proxy.domain.list.start ' + group.name + '\n';
      for (var j = 0; j < group.proxyRuleList.length; ++j) {
        var rule = group.proxyRuleList[j];
        if (rule.white.enabled) {
          continue;
        }
        if (!rule.rule) {
          data.generateErrors.push('流量代理规则未填写: ' + i + ',' + j);
        }
        text += '    ' + rule.rule + '\n';
      }
      text += 'proxy.domain.list.end\n';
      text += 'no-proxy.domain.list.start ' + group.name + '\n';
      for (var j = 0; j < group.proxyRuleList.length; ++j) {
        var rule = group.proxyRuleList[j];
        if (!rule.white.enabled) {
          continue;
        }
        if (!rule.rule) {
          data.generateErrors.push('流量代理规则未填写: ' + i + ',' + j);
        }
        text += '    ' + rule.rule + '\n';
      }
      text += 'no-proxy.domain.list.end\n';
      if (!data.dns.enabled) {
        if (group.dnsRuleList.length !== 0) {
          data.generateErrors.push('指定了DNS代理规则，但是dns未启用: ' + i);
        }
      }
      text += 'proxy.resolve.list.start ' + group.name + '\n';
      for (var j = 0; j < group.dnsRuleList.length; ++j) {
        var rule = group.dnsRuleList[j];
        if (!rule.rule) {
          data.generateErrors.push('DNS代理规则未填写: ' + i + ',' + j);
        }
        text += '    ' + rule.rule + '\n';
      }
      text += 'proxy.resolve.list.end\n';
      text += '\n';
    }
    text += 'https-sni-erasure.domain.list.start\n';
    for (var i = 0; i < data.httpsSniErasureRuleList.length; ++i) {
      var rule = data.httpsSniErasureRuleList[i];
      if (!rule.rule) {
        data.generateErrors.push('SNI消除规则未填写: ' + i);
      }
      text += '    ' + rule.rule + '\n';
    }
    text += 'https-sni-erasure.domain.list.end\n';
    text += '\n';

    data.generated = text;

    // do some final check
    if (!data.autosign.enabled) {
      if (data.httpsSniErasureRuleList.length !== 0) {
        data.generateErrors.push('指定了SNI消除规则，但是自动证书签发未启用');
      }
    }
    if (!data.socks5.enabled && !data.httpconnect.enabled) {
      if (data.pac.enabled) {
        data.generateErrors.push('pac服务已启用，但是socks5和http-connect均未启用');
      }
    }
    // check ports
    var ports = [];
    var names = [];
    if (data.socks5.enabled) {
      ports.push(data.socks5.listen);
      names.push('socks5');
    }
    if (data.httpconnect.enabled) {
      ports.push(data.httpconnect.listen);
      names.push('http-connect');
    }
    if (data.ss.enabled) {
      ports.push(data.ss.listen);
      names.push('shadowsocks');
    }
    if (data.pac.enabled) {
      ports.push(data.pac.listen);
      names.push('pac');
    }
    for (var i = 0; i < ports.length; ++i) {
      for (var j = i + 1; j < ports.length; ++j) {
        if (i === j) continue;
        // noinspection EqualityComparisonWithCoercionJS
        if (ports[i] == ports[j]) {
          data.generateErrors.push(names[i] + '与' + names[j] + '端口号冲突: ' + ports[i]);
        }
      }
    }
  }

  function run() {
    console.log('run');

    if (data.running) {
      return;
    }
    if (data.starting) {
      return;
    }
    if (data.stopping) {
      return;
    }
    data.starting = true;
    data.output = '';
    setTimeout(function() {
      data.starting = false;
      data.running = true;
    }, 1000);
    // TODO
  }

  function stop() {
    console.log('stop');

    if (!data.running) {
      return;
    }
    if (data.starting) {
      return;
    }
    if (data.stopping) {
      return;
    }
    data.stopping = true;
    setTimeout(function() {
      data.stopping = false;
      data.running = false;
    }, 1000);
    // TODO
  }
}
