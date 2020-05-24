#!/usr/bin/env python

import sys
import os

base_dir = sys.argv[1]
base_dir = os.path.abspath(base_dir)
if base_dir.endswith('/') or base_dir.endswith('\\'):
    base_dir = base_dir[0:-1]

def build(ls, prefix, base):
    if prefix != '':
        ls.append(prefix)
        prefix += '.'
    base += '/'
    files = os.listdir(base)
    for f in files:
        if os.path.isdir(base + f):
            build(ls, prefix + f, base + f)

ls = []
build(ls, '', base_dir)
for f in ls:
    print 'exports ' + f + ';'
