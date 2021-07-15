#include "vproxy_xdp_NativeXDP.h"

#include <linux/if_link.h>

#include "xdp/vproxy_xdp.h"
#include "exception.h"

#ifdef DEBUG
#define SHOW_CRITICAL
#endif
#ifdef NO_SHOW_CRITICAL
#undef SHOW_CRITICAL
#endif

#ifndef USE_CRITICAL
#define USE_CRITICAL
#elif USE_CRITICAL == 0
#undef USE_CRITICAL
#endif

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_loadAndAttachBPFProgramToNic0
  (JNIEnv* env, jclass self, jstring filepath, jstring prog, jstring ifname, jint mode, jboolean force_attach) {
    const char* filepath_chars = (*env)->GetStringUTFChars(env, filepath, NULL);
    const char* prog_chars = (*env)->GetStringUTFChars(env, prog, NULL);
    const char* ifname_chars = (*env)->GetStringUTFChars(env, ifname, NULL);

    int flags = 0;
    switch (mode) {
        case (1 << 3):
            flags |= XDP_FLAGS_HW_MODE;
            break;
        case (1 << 2):
            flags |= XDP_FLAGS_DRV_MODE;
            break;
        default:
            flags |= XDP_FLAGS_SKB_MODE;
            break;
    }
    if (!force_attach) {
        flags |= XDP_FLAGS_UPDATE_IF_NOEXIST;
    }

    struct bpf_object* bpfobj = vp_bpfobj_attach_to_if((char*)filepath_chars, (char*)prog_chars, (char*)ifname_chars, flags);
    if (bpfobj == NULL) {
        throwIOException(env, "vp_bpfobj_attach_to_if failed");
    }

    (*env)->ReleaseStringUTFChars(env, filepath, filepath_chars);
    (*env)->ReleaseStringUTFChars(env, prog, prog_chars);
    (*env)->ReleaseStringUTFChars(env, ifname, ifname_chars);

    return (jlong) bpfobj;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_findMapByNameInBPF0
  (JNIEnv* env, jclass self, jlong bpfobj_o, jstring name) {
    const char* name_chars = (*env)->GetStringUTFChars(env, name, NULL);
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;

    struct bpf_map* map = vp_bpfobj_find_map_by_name(bpfobj, (char*)name_chars);
    if (map == NULL) {
        throwIOException(env, "vp_bpfobj_find_map_by_name failed");
    }

    (*env)->ReleaseStringUTFChars(env, name, name_chars);

    return (jlong) map;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_createUMem0
  (JNIEnv* env, jclass self, jint chunk_size, jint fill_ring_size, jint comp_ring_size,
                              jint frame_size, jint headroom) {
    struct vp_umem_info* umem = vp_umem_create(chunk_size, fill_ring_size, comp_ring_size,
                                               frame_size, headroom);
    if (umem == NULL) {
        throwIOException(env, "vp_umem_create failed");
    }
    return (jlong) umem;
}

JNIEXPORT jobject JNICALL Java_vproxy_xdp_NativeXDP_getBufferFromUMem0
  (JNIEnv* env, jclass self, jlong umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;

    char* buffer = umem->buffer;
    int len = umem->buffer_size;

    jobject buf = (*env)->NewDirectByteBuffer(env, buffer, len);
    return buf;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_getBufferAddressFromUMem0
  (JNIEnv* env, jclass self, jlong umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    return (jlong) umem->buffer;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_createXSK0
  (JNIEnv* env, jclass self, jstring ifname, jint queue_id, jlong umem_o,
                              jint rx_ring_size, jint tx_ring_size,
                              jint mode, jboolean zero_copy) {
    const char* ifname_chars = (*env)->GetStringUTFChars(env, ifname, NULL);
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;

    int xdp_flags = 0;
    switch (mode) {
        case (1 << 2):
            xdp_flags |= XDP_FLAGS_DRV_MODE;
            break;
        case (1 << 3):
            xdp_flags |= XDP_FLAGS_HW_MODE;
            break;
        default:
            xdp_flags |= XDP_FLAGS_SKB_MODE;
    }

    int bind_flags = XDP_USE_NEED_WAKEUP;
    if (zero_copy) {
        bind_flags |= XDP_ZEROCOPY;
    } else {
        bind_flags |= XDP_COPY;
    }

    struct vp_xsk_info* xsk = vp_xsk_create((char*)ifname_chars, queue_id, umem,
                                            rx_ring_size, tx_ring_size, xdp_flags, bind_flags);
    if (xsk == NULL) {
        throwIOException(env, "vp_xsk_create failed");
    }

    (*env)->ReleaseStringUTFChars(env, ifname, ifname_chars);

    return (jlong) xsk;
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_addXSKIntoMap0
  (JNIEnv* env, jclass self, jlong map_o, jint key, jlong xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_xsk_add_into_map(map, key, xsk);
    if (ret) {
        throwIOException(env, "vp_xsk_add_into_map failed");
    }
}

JNIEXPORT jint JNICALL Java_vproxy_xdp_NativeXDP_getFDFromXSK0
  (JNIEnv* env, jclass self, jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    return vp_xsk_socket_fd(xsk);
}

inline static void vproxy_xdp_NativeXDP_fillUpFillRing0
  (jlong umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_xdp_fill_ring_fillup(umem);
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_fillUpFillRing0
  (JNIEnv* env, jclass self, jlong umem_o) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_fillUpFillRing0\n");
#endif
    vproxy_xdp_NativeXDP_fillUpFillRing0(umem_o);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_fillUpFillRing0
  (jlong umem_o) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_fillUpFillRing0\n");
#endif
    vproxy_xdp_NativeXDP_fillUpFillRing0(umem_o);
}
#endif

JNIEXPORT jint JNICALL Java_vproxy_xdp_NativeXDP_fetchPackets0
  (JNIEnv* env, jclass self, jlong xsk_o,
   jlongArray umemArray, jlongArray chunkArray, jintArray refArray,
   jintArray addrArray, jintArray endaddrArray, jintArray pktaddrArray, jintArray pktlenArray) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_fetchPackets0\n");
#endif
    int capacity = (*env)->GetArrayLength(env, umemArray);

    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    uint32_t idx_rx = -1;
    struct vp_chunk_info* chunk;

    int cnt = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
    if (cnt <= 0) {
        return cnt;
    }
    if (cnt > capacity) {
        cnt = capacity;
    }

    for (int i = 0; i < cnt; ++i) {
        vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);

        jlong _umem   [] = { (long)chunk->umem    };
        jlong _chunk  [] = { (long)chunk          };
        jint  _ref    [] = { (int) chunk->ref     };
        jint  _addr   [] = { (int) chunk->addr    };
        jint  _endaddr[] = { (int) chunk->endaddr };
        jint  _pktaddr[] = { (int) chunk->pktaddr };
        jint  _pktlen [] = { (int) chunk->pktlen  };

        (*env)->SetLongArrayRegion(env, umemArray,    i, 1, _umem   );
        (*env)->SetLongArrayRegion(env, chunkArray,   i, 1, _chunk  );
        (*env)->SetIntArrayRegion (env, refArray,     i, 1, _ref    );
        (*env)->SetIntArrayRegion (env, addrArray,    i, 1, _addr   );
        (*env)->SetIntArrayRegion (env, endaddrArray, i, 1, _endaddr);
        (*env)->SetIntArrayRegion (env, pktaddrArray, i, 1, _pktaddr);
        (*env)->SetIntArrayRegion (env, pktlenArray,  i, 1, _pktlen );
    }
    return cnt;
}
#ifdef USE_CRITICAL
JNIEXPORT jint JNICALL JavaCritical_vproxy_xdp_NativeXDP_fetchPackets0
  (jlong xsk_o,
   int umemLen,    jlong* umemArray,
   int chunkLen,   jlong* chunkArray,
   int refLen,     jint*  refArray,
   int addrLen,    jint*  addrArray,
   int endaddrLen, jint*  endaddrArray,
   int pktaddrLen, jint*  pktaddrArray,
   int pktlenLen,  jint*  pktlenArray) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_fetchPackets0\n");
#endif
    int capacity = umemLen;

    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    uint32_t idx_rx = -1;
    struct vp_chunk_info* chunk;

    int cnt = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
    if (cnt <= 0) {
        return cnt;
    }
    if (cnt > capacity) {
        cnt = capacity;
    }

    for (int i = 0; i < cnt; ++i) {
        vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);

        umemArray   [i] = (long)chunk->umem   ;
        chunkArray  [i] = (long)chunk         ;
        refArray    [i] = (int) chunk->ref    ;
        addrArray   [i] = (int) chunk->addr   ;
        endaddrArray[i] = (int) chunk->endaddr;
        pktaddrArray[i] = (int) chunk->pktaddr;
        pktlenArray [i] = (int) chunk->pktlen ;
    }
    return cnt;
}
#endif

inline static void vproxy_xdp_NativeXDP_rxRelease0
  (jlong xsk_o, jint cnt) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_rx_release(xsk, cnt);
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_rxRelease0
  (JNIEnv* env, jclass self, jlong xsk_o, jint cnt) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_rxRelease0\n");
#endif
    vproxy_xdp_NativeXDP_rxRelease0(xsk_o, cnt);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_rxRelease0
  (jlong xsk_o, jint cnt) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_rxRelease0\n");
#endif
    vproxy_xdp_NativeXDP_rxRelease0(xsk_o, cnt);
}
#endif

inline static jboolean vproxy_xdp_NativeXDP_writePacket0
  (jlong xsk_o, jlong chunk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;

    int ret = vp_xdp_write_pkt(xsk, chunk);
    if (ret) {
        return JNI_FALSE;
    } else {
        return JNI_TRUE;
    }
}
JNIEXPORT jboolean JNICALL Java_vproxy_xdp_NativeXDP_writePacket0
  (JNIEnv* env, jclass self, jlong xsk_o, jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_writePacket0\n");
#endif
    return vproxy_xdp_NativeXDP_writePacket0(xsk_o, chunk_o);
}
#ifdef USE_CRITICAL
JNIEXPORT jboolean JNICALL JavaCritical_vproxy_xdp_NativeXDP_writePacket0
  (jlong xsk_o, jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_writePacket0\n");
#endif
    return vproxy_xdp_NativeXDP_writePacket0(xsk_o, chunk_o);
}
#endif

inline static void vproxy_xdp_NativeXDP_completeTx0
  (jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_complete_tx(xsk);
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_completeTx0
  (JNIEnv* env, jclass self, jlong xsk_o) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_completeTx0\n");
#endif
    vproxy_xdp_NativeXDP_completeTx0(xsk_o);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_completeTx0
  (jlong xsk_o) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_completeTx0\n");
#endif
    vproxy_xdp_NativeXDP_completeTx0(xsk_o);
}
#endif

JNIEXPORT jboolean JNICALL Java_vproxy_xdp_NativeXDP_fetchChunk0
  (JNIEnv* env, jclass self, jlong umem_o,
   jlongArray umemArray, jlongArray chunkArray, jintArray refArray,
   jintArray addrArray, jintArray endaddrArray, jintArray pktaddrArray, jintArray pktlenArray) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_fetchChunk0\n");
#endif
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = vp_chunk_fetch(&umem->chunks);
    if (chunk == NULL) {
        return JNI_FALSE;
    }

    jlong _umem   [] = { (long)chunk->umem    };
    jlong _chunk  [] = { (long)chunk          };
    jint  _ref    [] = { (int) chunk->ref     };
    jint  _addr   [] = { (int) chunk->addr    };
    jint  _endaddr[] = { (int) chunk->endaddr };
    jint  _pktaddr[] = { (int) chunk->pktaddr };
    jint  _pktlen [] = { (int) chunk->pktlen  };

    (*env)->SetLongArrayRegion(env, umemArray,    0, 1, _umem   );
    (*env)->SetLongArrayRegion(env, chunkArray,   0, 1, _chunk  );
    (*env)->SetIntArrayRegion (env, refArray,     0, 1, _ref    );
    (*env)->SetIntArrayRegion (env, addrArray,    0, 1, _addr   );
    (*env)->SetIntArrayRegion (env, endaddrArray, 0, 1, _endaddr);
    (*env)->SetIntArrayRegion (env, pktaddrArray, 0, 1, _pktaddr);
    (*env)->SetIntArrayRegion (env, pktlenArray,  0, 1, _pktlen );

    return JNI_TRUE;
}
#ifdef USE_CRITICAL
JNIEXPORT jboolean JNICALL JavaCritical_vproxy_xdp_NativeXDP_fetchChunk0
  (jlong umem_o,
   int umemLen,    jlong* umemArray,
   int chunkLen,   jlong* chunkArray,
   int refLen,     jint*  refArray,
   int addrLen,    jint*  addrArray,
   int endaddrLen, jint*  endaddrArray,
   int pktaddrLen, jint*  pktaddrArray,
   int pktlenLen,  jint*  pktlenArray) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_fetchChunk0\n");
#endif
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = vp_chunk_fetch(&umem->chunks);
    if (chunk == NULL) {
        return JNI_FALSE;
    }

    umemArray   [0] = (long)chunk->umem   ;
    chunkArray  [0] = (long)chunk         ;
    refArray    [0] = (int) chunk->ref    ;
    addrArray   [0] = (int) chunk->addr   ;
    endaddrArray[0] = (int) chunk->endaddr;
    pktaddrArray[0] = (int) chunk->pktaddr;
    pktlenArray [0] = (int) chunk->pktlen ;

    return JNI_TRUE;
}
#endif

inline static void vproxy_xdp_NativeXDP_setChunk0
  (jlong chunk_o, jint pktaddr, jint pktlen) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->pktaddr = pktaddr;
    chunk->pktlen = pktlen;
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_setChunk0
  (JNIEnv* env, jclass self, jlong chunk_o, jint pktaddr, jint pktlen) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_setChunk0\n");
#endif
    vproxy_xdp_NativeXDP_setChunk0(chunk_o, pktaddr, pktlen);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_setChunk0
  (jlong chunk_o, jint pktaddr, jint pktlen) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_setChunk0\n");
#endif
    vproxy_xdp_NativeXDP_setChunk0(chunk_o, pktaddr, pktlen);
}
#endif

inline static void vproxy_xdp_NativeXDP_releaseChunk0
  (jlong umem_o, jlong chunk_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    vp_chunk_release(&umem->chunks, chunk);
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseChunk0
  (JNIEnv* env, jclass self, jlong umem_o, jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_releaseChunk0\n");
#endif
    vproxy_xdp_NativeXDP_releaseChunk0(umem_o, chunk_o);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_releaseChunk0
  (jlong umem_o, jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_releaseChunk0\n");
#endif
    vproxy_xdp_NativeXDP_releaseChunk0(umem_o, chunk_o);
}
#endif

inline static void vproxy_xdp_NativeXDP_addChunkRefCnt0
  (jlong chunk_o) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->ref++;
}
JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_addChunkRefCnt0
  (JNIEnv* env, jclass self, jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("normal Java_vproxy_xdp_NativeXDP_addChunkRefCnt0\n");
#endif
    vproxy_xdp_NativeXDP_addChunkRefCnt0(chunk_o);
}
#ifdef USE_CRITICAL
JNIEXPORT void JNICALL JavaCritical_vproxy_xdp_NativeXDP_addChunkRefCnt0
  (jlong chunk_o) {
#ifdef SHOW_CRITICAL
printf("critical JavaCritical_vproxy_xdp_NativeXDP_addChunkRefCnt0\n");
#endif
    vproxy_xdp_NativeXDP_addChunkRefCnt0(chunk_o);
}
#endif

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseXSK0
  (JNIEnv* env, jclass self, jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xsk_close(xsk);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseUMem0
  (JNIEnv* env, jclass self, jlong umem_o, jboolean release_buffer) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_umem_close(umem, release_buffer);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseBPFObject0
  (JNIEnv* env, jclass self, jlong bpfobj_o) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;
    vp_bpfobj_release(bpfobj);
}
