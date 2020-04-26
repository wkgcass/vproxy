#!/usr/bin/env python

import sys
import subprocess
import socket

def runCommand(commands):
    p = subprocess.Popen(commands,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate()
    ret = p.returncode
    return (ret, str(out).strip(), str(err).strip())

def getResp(sock):
    x = str(sock.recv(128))
    if x[0] == '-':
        return (False, x.strip())
    elif x[0] == '$':
        arr = x.split('\r\n')
        if len(arr) == 3 and arr[2] == '':
            return (True, arr[1].strip())
        return (False, 'unable to parse ' + x)
    else:
        return (False, 'unable to parse ' + x)

def runVProxyCommand(swaddr, password, commandStr):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        x = swaddr.split(':')
        sock.connect((x[0], int(x[1])))
        sock.send('AUTH ' + password + '\r\n')
        ok, ret = getResp(sock)
        if not ok:
            return (1, '', ret)
        sock.send(commandStr + '\r\n')
        ok, ret = getResp(sock)
        if not ok:
            return (1, '', ret)
        else:
            return (0, ret, 0)
    finally:
        sock.close()

def buildTapName(ns):
    return 'tap' + ns + '0'

def add(swaddr, password, sw, ns, vni, addr, gate):
    # check for netns
    ret, out, err = runCommand(['ip', 'netns', 'show'])
    if ret != 0:
        raise Exception('getting netns list failed: ' + out + ', ' + err)
    exists = (ns + ' (') in out
    if not exists:
        print 'creating netns ' + ns + ' ...'
        # create the namespace
        ret, out, err = runCommand(['ip', 'netns', 'add', ns])
        if ret != 0:
            raise Exception('creating netns failed: ' + out + ', ' + err)
    else:
        print 'netns ' + ns + ' already exists'

    # check for eth0
    ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'link', 'show'])
    if ret != 0:
        raise Exception('checking interfaces in the ns failed: ' + out + ', ' + err)
    exists = ': eth0: ' in out
    if not exists:
        # check for tap
        nic = buildTapName(ns)
        exists = (': ' + nic + ': ') in out
        if not exists:
            # check for tap in the main ns
            ret, out, err = runCommand(['ip', 'link', 'show'])
            if ret != 0:
                raise Exception('checking interface in the main ns failed: ' + out + ', ' + err)
            exists = (': ' + nic + ': ') in out
            if not exists:
                print 'creating ' + nic + ' in main ns ...'
                ret, out, err = runVProxyCommand(swaddr, password,
                    'add tap ' + nic + ' to switch ' + sw + ' vni ' + vni)
                if ret != 0:
                    raise Exception('creating tap ' + nic + ' failed: ' + out + ', ' + err)
                if out != nic:
                    raise Exception('the created tap name is not the same as the input one: ' + out)
            else:
                print nic + ' in main ns already exists'
            # END check for tap in the main ns

            print 'moving ' + nic + ' into ns ' + ns + " ..."
            ret, out, err = runCommand(['ip', 'link', 'set', nic, 'netns', ns])
            if ret != 0:
                raise Exception('moving ' + nic + ' into ns failed: ' + out + ', ' + err)
        else:
            print nic + ' in ' + ns + ' already exists'
        # END check for tap

        print 'renaming ' + nic + ' to eth0 ...'
        ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'link', 'set', nic, 'name', 'eth0'])
        if ret != 0:
            raise Exception('renaming ' + nic + ' to eth0 failed: ' + out + ', ' + err)
    else:
        print 'eth0 in ' + ns + ' already exists'

    # check for ip
    ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'addr', 'show'])
    if ret != 0:
        raise Exception('checking ip in the ns failed: ' + out + ', ' + err)
    exists = addr in out
    if not exists:
        print 'assigning ip address ...'
        ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'addr', 'add', addr, 'dev', 'eth0'])
        if ret != 0:
            raise Exception('assigning ip failed: ' + out + ', ' + err)
    else:
        print 'ip is already assigned'

    # up the nic
    print 'setting eth0 to up ...'
    ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'link', 'set', 'eth0', 'up'])
    if ret != 0:
        raise Exception('setting eth0 to up failed: ' + out + ', ' + err)

    # check for default route
    ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'route', 'show'])
    if ret != 0:
        raise Exception('checking route in the ns failed: ' + out + ', ' + err)
    exists = 'default via ' in out
    if not exists:
        print 'adding default route ...'
        ret, out, err = runCommand(['ip', 'netns', 'exec', ns, 'ip', 'route', 'add', 'default', 'via', gate])
        if ret != 0:
            raise Exception('adding default route in the ns failed: ' + out + ', ' + err)
    else:
        print 'default route is already added'

def delete(swaddr, password, sw, ns):
    # delete the corresponding tap
    nic = buildTapName(ns)
    print 'removing ' + nic + ' ...'
    ret, out, err = runVProxyCommand(swaddr, password,
        'remove tap ' + nic + ' from switch ' + sw)
    if ret != 0:
        print 'WARN: removing tap device: ' + out + ', ' + err

    # check for netns
    print 'deleting netns ' + ns + ' ...'
    ret, out, err = runCommand(['ip', 'netns', 'del', ns])
    if ret != 0:
        print 'WARN: deleting netns: ' + out + ', ' + err

def main(args):
    # args
    swaddr = '127.0.0.1:16309'
    password = '123456'
    op = None
    sw = None
    ns = None
    vni = None
    addr = None
    gate = None

    HELP_STR="""
usage: add ns={} sw={} vni={} addr={} gate={}
       del ns={} sw={}
default:
       swaddr = 127.0.0.1:16309
       pass   = 123456
arguments:
       swaddr vproxy switch configuration address
       pass   vproxy switch configuration password
       ns     name of the netns
       sw     name of the switch to connect to
       vni    vni for the net interface to connect to
       addr   ip address and mask of the net dev in x.x.x.x/x format
       gate   the gateway address
"""

    # read args
    if len(args) == 0:
        print HELP_STR
        return
    if len(args) == 1 and args[0] in ['help', '--help', '-help', '-h']:
        print HELP_STR
        return

    op = args[0]
    args = args[1:]
    if op == 'add':
        for arg in args:
            if arg.startswith('swaddr='):
                swaddr = arg[len('swaddr='):]
            elif arg.startswith('pass='):
                password = arg[len('pass='):]
            elif arg.startswith('sw='):
                sw = arg[len('sw='):]
            elif arg.startswith('ns='):
                ns = arg[len('ns='):]
            elif arg.startswith('vni='):
                vni = arg[len('vni='):]
            elif arg.startswith('addr='):
                addr = arg[len('addr='):]
            elif arg.startswith('gate='):
                gate = arg[len('gate='):]
            else:
                raise Exception('unknown argument: ' + arg)
    elif op == 'del':
        for arg in args:
            if arg.startswith('swaddr='):
                swaddr = arg[len('swaddr='):]
            elif arg.startswith('pass='):
                password = arg[len('pass='):]
            elif arg.startswith('ns='):
                ns = arg[len('ns='):]
            elif arg.startswith('sw='):
                sw = arg[len('sw='):]
            else:
                raise Exception('unknown argument: ' + arg)
    else:
        raise Exception('unknown operation: ' + op)

    # handle
    if op == 'add':
        if sw == None:
            raise Exception('missing argument sw={...}')
        if ns == None:
            raise Exception('missing argument ns={...}')
        if vni == None:
            raise Exception('missing argument vni={...}')
        if addr == None:
            raise Exception('missing argument addr={...}')
        if gate == None:
            raise Exception('missing argument gate={...}')
        if len(ns) > 6:
            raise Exception('ns length should be <= 6')
        print 'swaddr   = ' + swaddr
        print 'password = ' + password
        print 'sw   = ' + sw
        print 'ns   = ' + ns
        print 'vni  = ' + vni
        print 'addr = ' + addr
        print 'gate = ' + gate
        print '=========================='
        add(swaddr, password, sw, ns, vni, addr, gate)
    else:
        if sw == None:
            raise Exception('missing argument sw={...}')
        if ns == None:
            raise Exception('missing argument ns={...}')
        print 'swaddr   = ' + swaddr
        print 'password = ' + password
        print 'sw = ' + sw
        print 'ns = ' + ns
        print '=========================='
        delete(swaddr, password, sw, ns)

try:
    main(sys.argv[1:])
    sys.exit(0)
except Exception, e:
    print e
    sys.exit(1)
