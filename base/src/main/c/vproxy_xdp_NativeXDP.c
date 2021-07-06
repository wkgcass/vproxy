#include "vproxy_xdp_NativeXDP.h"

#include <linux/if_link.h>

#include "xdp/vproxy_xdp.h"
#include "exception.h"

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_loadAndAttachBPFProgramToNic
  (JNIEnv* env, jobject self, jstring filepath, jstring prog, jstring ifname, jint mode, jboolean force_attach) {
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

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_findMapByNameInBPF
  (JNIEnv* env, jobject self, jlong bpfobj_o, jstring name) {
    const char* name_chars = (*env)->GetStringUTFChars(env, name, NULL);
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;

    struct bpf_map* map = vp_bpfobj_find_map_by_name(bpfobj, (char*)name_chars);
    if (map == NULL) {
        throwIOException(env, "vp_bpfobj_find_map_by_name failed");
    }

    (*env)->ReleaseStringUTFChars(env, name, name_chars);

    return (jlong) map;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_createUMem
  (JNIEnv* env, jobject self, jint chunk_size, jint fill_ring_size, jint comp_ring_size,
                              jint frame_size, jint headroom) {
    struct vp_umem_info* umem = vp_umem_create(chunk_size, fill_ring_size, comp_ring_size,
                                               frame_size, headroom);
    if (umem == NULL) {
        throwIOException(env, "vp_umem_create failed");
    }
    return (jlong) umem;
}

JNIEXPORT jobject JNICALL Java_vproxy_xdp_NativeXDP_getBufferFromUMem
  (JNIEnv* env, jobject self, jlong umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;

    char* buffer = umem->buffer;
    int len = umem->buffer_size;

    jobject buf = (*env)->NewDirectByteBuffer(env, buffer, len);
    return buf;
}

JNIEXPORT jlong JNICALL Java_vproxy_xdp_NativeXDP_createXSK
  (JNIEnv* env, jobject self, jstring ifname, jint queue_id, jlong umem_o,
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

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_addXSKIntoMap
  (JNIEnv* env, jobject self, jlong map_o, jint key, jlong xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_xsk_add_into_map(map, key, xsk);
    if (ret) {
        throwIOException(env, "vp_xsk_add_into_map failed");
    }
}

JNIEXPORT jint JNICALL Java_vproxy_xdp_NativeXDP_getFDFromXSK
  (JNIEnv* env, jobject self, jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    return vp_xsk_socket_fd(xsk);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_fillUpFillRing
  (JNIEnv* env, jobject self, jlong umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_xdp_fill_ring_fillup(umem);
}

jmethodID ChunkPrototypeObjectList_add = NULL;

void add_chunk(JNIEnv* env, jobject list, struct vp_chunk_info* chunk) {
    if (ChunkPrototypeObjectList_add == NULL) {
        jclass ChunkPrototypeObjectList = (*env)->FindClass(env, "vproxy/xdp/ChunkPrototypeObjectList");
        ChunkPrototypeObjectList_add = (*env)->GetMethodID(env, ChunkPrototypeObjectList, "add", "(JJIIIII)V");
    }
    (*env)->CallVoidMethod(env, list, ChunkPrototypeObjectList_add,
                           (long)chunk->umem, (long)chunk, (int)chunk->ref, (int)chunk->addr, (int)chunk->endaddr, (int)chunk->pktaddr, (int)chunk->pktlen);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_fetchPackets
  (JNIEnv* env, jobject self, jlong xsk_o, jobject list) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    uint32_t idx_rx = -1;
    struct vp_chunk_info* chunk;

    int cnt = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
    if (cnt <= 0) {
        return;
    }

    for (int i = 0; i < cnt; ++i) {
        vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
        add_chunk(env, list, chunk);
    }
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_rxRelease
  (JNIEnv* env, jobject self, jlong xsk_o, jint cnt) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_rx_release(xsk, cnt);
}

JNIEXPORT jboolean JNICALL Java_vproxy_xdp_NativeXDP_writePacket
  (JNIEnv* env, jobject self, jlong xsk_o, jlong chunk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;

    int ret = vp_xdp_write_pkt(xsk, chunk);
    if (ret) {
        return JNI_FALSE;
    } else {
        return JNI_TRUE;
    }
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_completeTx
  (JNIEnv* env, jobject self, jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_complete_tx(xsk);
}

jmethodID Chunk_set = NULL;

void set_chunk(JNIEnv* env, jobject jchunk, struct vp_chunk_info* chunk) {
    if (Chunk_set == NULL) {
        jclass Chunk = (*env)->FindClass(env, "vproxy/xdp/Chunk");
        Chunk_set = (*env)->GetMethodID(env, Chunk, "set", "(JJIIIII)V");
    }

    (*env)->CallVoidMethod(env, jchunk, Chunk_set,
                           (long)chunk->umem, (long)chunk, (int)chunk->ref, (int)chunk->addr, (int)chunk->endaddr, (int)chunk->pktaddr, (int)chunk->pktlen);
}

JNIEXPORT jboolean JNICALL Java_vproxy_xdp_NativeXDP_fetchChunk
  (JNIEnv* env, jobject self, jlong umem_o, jobject jchunk) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = vp_chunk_fetch(&umem->chunks);
    if (chunk == NULL) {
        return JNI_FALSE;
    }

    set_chunk(env, jchunk, chunk);

    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_setChunk
  (JNIEnv* env, jobject self, jlong chunk_o, jint pktaddr, jint pktlen) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->pktaddr = pktaddr;
    chunk->pktlen = pktlen;
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseChunk
  (JNIEnv* env, jobject self, jlong umem_o, jlong chunk_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    vp_chunk_release(&umem->chunks, chunk);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_addChunkRefCnt
  (JNIEnv* env, jobject self, jlong chunk_o) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->ref++;
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseXSK
  (JNIEnv* env, jobject self, jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xsk_close(xsk);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseUMem
  (JNIEnv* env, jobject self, jlong umem_o, jboolean release_buffer) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_umem_close(umem, release_buffer);
}

JNIEXPORT void JNICALL Java_vproxy_xdp_NativeXDP_releaseBPFObject
  (JNIEnv* env, jobject self, jlong bpfobj_o) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;
    vp_bpfobj_release(bpfobj);
}
