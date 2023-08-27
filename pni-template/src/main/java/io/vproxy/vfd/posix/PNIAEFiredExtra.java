package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.Include;
import io.vproxy.pni.annotation.Name;
import io.vproxy.pni.annotation.Struct;

import java.lang.foreign.MemorySegment;

@Struct(skip = true)
@Name("aeFiredExtra")
@Include("ae.h")
public class PNIAEFiredExtra {
    MemorySegment ud;
    int mask;
}
