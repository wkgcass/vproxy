#include "io_vproxy_xdp_NativeXDP.h"

#include <linux/if_link.h>

#include "xdp/vproxy_xdp.h"

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_loadAndAttachBPFProgramToNic
  (JEnv* env, char * filepath_chars, char * prog_chars, char * ifname_chars, uint32_t mode, uint8_t force_attach) {
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

    struct bpf_object* bpfobj = vp_bpfobj_attach_to_if(filepath_chars, prog_chars, ifname_chars, flags);
    if (bpfobj == NULL) {
        return throwIOException(env, "vp_bpfobj_attach_to_if failed");
    }

    env->return_j = (jlong) bpfobj;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_detachBPFProgramFromNic
  (JEnv* env, char * ifname_chars) {
    int err = vp_bpfobj_detach_from_if((char*)ifname_chars);
    if (err) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_findMapByNameInBPF
  (JEnv* env, uint64_t bpfobj_o, char * name_chars) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;

    struct bpf_map* map = vp_bpfobj_find_map_by_name(bpfobj, (char*)name_chars);
    if (map == NULL) {
        return throwIOException(env, "vp_bpfobj_find_map_by_name failed");
    }

    env->return_j = (jlong) map;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_createUMem
  (JEnv* env, uint32_t chunk_size, uint32_t fill_ring_size, uint32_t comp_ring_size,
                              uint32_t frame_size, uint32_t headroom) {
    struct vp_umem_info* umem = vp_umem_create(chunk_size, fill_ring_size, comp_ring_size,
                                               frame_size, headroom);
    if (umem == NULL) {
        return throwIOException(env, "vp_umem_create failed");
    }
    env->return_j = (jlong) umem;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_shareUMem
  (JEnv* env, uint64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*)umem_o;
    env->return_j = (jlong) vp_umem_share(umem);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_getBufferFromUMem
  (JEnv* env, uint64_t umem_o, buf_st* result) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;

    char* buffer = umem->buffer;
    int len = umem->buffer_size;

    result->buffer = buffer;
    result->len = len;
    env->return_p = result;

    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_getBufferAddressFromUMem
  (JEnv* env, uint64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    env->return_j = (jlong) umem->buffer;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_createXSK
  (JEnv* env, char* ifname_chars, uint32_t queue_id, uint64_t umem_o,
                             uint32_t rx_ring_size, uint32_t tx_ring_size,
                             uint32_t mode, uint8_t zero_copy,
                             uint32_t busy_poll_budget,
                             uint8_t rx_gen_csum) {
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

    int flags = 0;
    if (rx_gen_csum) {
        flags |= VP_XSK_FLAG_RX_GEN_CSUM;
    }
    struct vp_xsk_info* xsk = vp_xsk_create((char*)ifname_chars, queue_id, umem,
                                            rx_ring_size, tx_ring_size, xdp_flags, bind_flags,
                                            busy_poll_budget,
                                            flags);
    if (xsk == NULL) {
        return throwIOException(env, "vp_xsk_create failed");
    }

    env->return_j = (jlong) xsk;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_addXSKIntoMap
  (JEnv* env, uint64_t map_o, uint32_t key, uint64_t xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_xsk_add_into_map(map, key, xsk);
    if (ret) {
        return throwIOException(env, "vp_xsk_add_into_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_addMacIntoMap
  (JEnv* env, uint64_t map_o, char* mac, uint64_t xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_mac_add_into_map(map, mac, xsk->ifindex);
    if (ret) {
        return throwIOException(env, "vp_mac_add_into_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_removeMacFromMap
  (JEnv* env, uint64_t map_o, char* mac) {
    struct bpf_map* map = (struct bpf_map*) map_o;

    int ret = vp_mac_remove_from_map(map, mac);
    if (ret) {
        return throwIOException(env, "vp_mac_remove_from_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_getFDFromXSK
  (JEnv* env, uint64_t xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    env->return_i = vp_xsk_socket_fd(xsk);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_fillUpFillRing0
  (uint64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_xdp_fill_ring_fillup(umem);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_fillUpFillRing
  (JEnv* env, uint64_t umem_o) {
    io_vproxy_xdp_NativeXDP_fillUpFillRing0(umem_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_fetchPackets0
  (JEnv* env, uint64_t xsk_o,
   uint32_t capacity,
   uint64_t* umemArray, uint64_t* chunkArray, uint32_t* refArray,
   uint32_t* addrArray, uint32_t* endaddrArray, uint32_t* pktaddrArray, uint32_t* pktlenArray) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    uint32_t idx_rx = -1;
    struct vp_chunk_info* chunk;

    int cnt = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
    if (cnt <= 0) {
        env->return_i = cnt;
        return 0;
    }
    if (cnt > capacity) {
        cnt = capacity;
    }

    for (int i = 0; i < cnt; ++i) {
        vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);

        umemArray[i]    = (size_t) chunk->umem;
        chunkArray[i]   = (size_t) chunk;
        refArray[i]     = chunk->ref;
        addrArray[i]    = chunk->addr;
        endaddrArray[i] = chunk->endaddr;
        pktaddrArray[i] = chunk->pktaddr;
        pktlenArray[i]  = chunk->pktlen;
    }
    env->return_i = cnt;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_rxRelease0
  (jlong xsk_o, jint cnt) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_rx_release(xsk, cnt);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_rxRelease
  (JEnv* env, uint64_t xsk_o, uint32_t cnt) {
    io_vproxy_xdp_NativeXDP_rxRelease0(xsk_o, cnt);
    return 0;
}

inline static jboolean io_vproxy_xdp_NativeXDP_writePacket0
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
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_writePacket
  (JEnv* env, uint64_t xsk_o, uint64_t chunk_o) {
    env->return_z = io_vproxy_xdp_NativeXDP_writePacket0(xsk_o, chunk_o);
    return 0;
}

inline static int io_vproxy_xdp_NativeXDP_writePackets0
  (jlong xsk_o, jint size, jlong* ptrs) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    return vp_xdp_write_pkts(xsk, size, (long*) ptrs);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_writePackets
  (JEnv* env, uint64_t xsk_o, uint32_t size, uint64_t* ptrs) {
    jboolean is_copy = false;
    if (ptrs == NULL) {
        env->return_i = 0;
        return 0;
    }
    int ret = io_vproxy_xdp_NativeXDP_writePackets0(xsk_o, size, ptrs);
    env->return_i = ret;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_completeTx0
  (jlong xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_complete_tx(xsk);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_completeTx
  (JEnv* env, uint64_t xsk_o) {
    io_vproxy_xdp_NativeXDP_completeTx0(xsk_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_fetchChunk0
  (JEnv* env, uint64_t umem_o,
   uint64_t* umemArray, uint64_t* chunkArray, uint32_t* refArray,
   uint32_t* addrArray, uint32_t* endaddrArray, uint32_t* pktaddrArray, uint32_t* pktlenArray) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = vp_chunk_fetch(umem->chunks);
    if (chunk == NULL) {
        env->return_z = JNI_FALSE;
        return 0;
    }

    umemArray[0]    = (size_t) chunk->umem;
    chunkArray[0]   = (size_t) chunk;
    refArray[0]     = chunk->ref;
    addrArray[0]    = chunk->addr;
    endaddrArray[0] = chunk->endaddr;
    pktaddrArray[0] = chunk->pktaddr;
    pktlenArray[0]  = chunk->pktlen;

    env->return_z = JNI_TRUE;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_setChunk0
  (jlong chunk_o, jint pktaddr, jint pktlen, jint csumFlags) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->pktaddr = pktaddr;
    chunk->pktlen = pktlen;
    chunk->csum_flags = csumFlags;
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_setChunk
  (JEnv* env, uint64_t chunk_o, uint32_t pktaddr, uint32_t pktlen, uint32_t csumFlags) {
    io_vproxy_xdp_NativeXDP_setChunk0(chunk_o, pktaddr, pktlen, csumFlags);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_releaseChunk0
  (jlong umem_o, jlong chunk_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    vp_chunk_release(umem->chunks, chunk);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_releaseChunk
  (JEnv* env, uint64_t umem_o, uint64_t chunk_o) {
    io_vproxy_xdp_NativeXDP_releaseChunk0(umem_o, chunk_o);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_addChunkRefCnt0
  (jlong chunk_o) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->ref++;
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_addChunkRefCnt
  (JEnv* env, uint64_t chunk_o) {
    io_vproxy_xdp_NativeXDP_addChunkRefCnt0(chunk_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_releaseXSK
  (JEnv* env, uint64_t xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xsk_close(xsk);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_releaseUMem
  (JEnv* env, uint64_t umem_o, uint8_t release_buffer) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_umem_close(umem, release_buffer);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_NativeXDP_releaseBPFObject
  (JEnv* env, uint64_t bpfobj_o) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;
    vp_bpfobj_release(bpfobj);
    return 0;
}
