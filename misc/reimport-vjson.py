#!/usr/bin/env python

import os

def modify(n):
    if not n.endswith('.kt') and not n.endswith('.java'):
        return
    print ('handling ' + n)
    f = open(n, 'r')
    s = f.read()
    f.close()
    s = s.replace("package vjson", "package io.vproxy.dep.vjson")
    s = s.replace("import vjson", "import io.vproxy.dep.vjson")
    s = s.replace(": vjson.util.TrustedFlag", ": io.vproxy.dep.vjson.util.TrustedFlag")
    f = open(n, 'w')
    f.write(s)
    f.flush()
    f.close()

def handle(name):
    ls = os.listdir(name)
    for n in ls:
        if os.path.isdir(name + '/' + n):
            handle(name + '/' + n)
        else:
            modify(name + '/' + n)

handle("./dep/src/main/java/io/vproxy/dep/vjson")
