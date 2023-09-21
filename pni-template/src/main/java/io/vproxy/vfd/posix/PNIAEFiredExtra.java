package io.vproxy.vfd.posix;

import io.vproxy.pni.annotation.*;

import java.lang.foreign.MemorySegment;

@Struct(skip = true)
@AlwaysAligned
@Name("aeFiredExtra")
@Include("ae.h")
public class PNIAEFiredExtra {
    MemorySegment ud;
    int mask;
}
