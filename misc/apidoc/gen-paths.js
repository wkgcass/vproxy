var fs = require('fs');
var templateTop = fs.readFileSync('./template-top', 'utf8');
var templateSub = fs.readFileSync('./template-sub', 'utf8');

function RT(name, urlName, shortName, description, noUpdate) {
  this.name = name;
  this.urlName = urlName;
  this.shortName = shortName;
  this.description = description;
  this.noUpdate = noUpdate
}

function RS(rt, name, urlName, shortName, description, noUpdate) {
  this.rt = rt;
  this.name = name;
  this.urlName = urlName;
  this.shortName = shortName;
  this.description = description;
  this.noUpdate = noUpdate
}

var tcpLb = new RT('TcpLb', 'tcp-lb', 'tl', 'tcp loadbalancer', false);
var socks5 = new RT('Socks5Server', 'socks5-server', 'socks5', 'socks5 server', false);
var dns = new RT('DnsServer', 'dns-server', 'dns', 'dns server', false);
var elg = new RT('EventLoopGroup', 'event-loop-group', 'elg', 'a group of event loop(s)', true);
var elInElg = new RS(elg, 'EventLoop', 'event-loop', 'el', 'event loop (thread with a selector)', true);
var ups = new RT('Upstream', 'upstream', 'ups', 'the upstream servers, containing server-group(s)', true);
var sgInUps = new RS(ups, 'ServerGroupInUpstream', 'server-group', 'sg', '', false);
var sg = new RT('ServerGroup', 'server-group', 'sg', 'a group of server(s)', false);
var svrInSg = new RS(sg, 'Server', 'server', 'svr', 'a remote endpoint', false);
var secg = new RT('SecurityGroup', 'security-group', 'secg', 'a group of security-group-rule(s)', false);
var secgr = new RS(secg, 'SecurityGroupRule', 'security-group-rule', 'secgr', 'a rule for accessible or forbidden remote/local address(es)', true);
var sgd = new RT('SmartGroupDelegate', 'smart-group-delegate', 'sgd', 'a binding for a server group and endpoints in a service', true);
var snd = new RT('SmartNodeDelegate', 'smart-node-delegate', 'snd', 'a registered record for an endpoint in a service', true);
var ck = new RT('CertKey', 'cert-key', 'ck', 'a pem format cert(s)/key tuple', true);

var tags = [
  tcpLb, socks5, dns, elg, elInElg, ups, sg, svrInSg, secg, secgr, sgd, snd, ck
];
var resources = [
  tcpLb, socks5, dns,
  elInElg, elg,
  sgInUps, ups,
  svrInSg, sg,
  secgr, secg,
  sgd, snd,
  ck,
];

var tagResults = [];
for (var i = 0; i < tags.length; ++i) {
  var tag = tags[i];
  tagResults.push('' +
    '- name: "' + tag.urlName + '"\n' +
    '  description: "' + tag.description + '"\n' +
    '');
}

var pathResults = [];
for (var i = 0; i < resources.length; ++i) {
  var res = resources[i];
  var template = res.rt ? templateSub : templateTop;
  var result;
  if (res.rt) {
    template = template
      .replace(/\{\{top\-res\-name\}\}/g,        res.rt.name)
      .replace(/\{\{top\-res\-url\-name\}\}/g,   res.rt.urlName)
      .replace(/\{\{top\-res\-short\-name\}\}/g, res.rt.shortName)
  }
  if (res.noUpdate) {
    var idx0 = template.indexOf('# {{{[[[');
    var idx1 = template.indexOf('# ]]]}}}');
    template = template.substring(0, idx0) + template.substring(idx1 + 8);
  }
  result = template
    .replace(/\{\{res\-name\}\}/g,        res.name)
    .replace(/\{\{res\-url\-name\}\}/g,   res.urlName)
    .replace(/\{\{res\-short\-name\}\}/g, res.shortName)
  ;
  pathResults.push(result);
}

console.log("########################");
console.log("# Begin Generated Code #");
console.log("########################");
console.log('tags:');
console.log(tagResults.join(''));
console.log('paths:');
console.log(pathResults.join(''));
console.log("######################");
console.log("# End Generated Code #");
console.log("######################");
