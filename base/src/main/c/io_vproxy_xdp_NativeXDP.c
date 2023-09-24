#include "io_vproxy_xdp_XDPNative.h"

#include <linux/if_link.h>
#include "exception.h"
#include "xdp/vproxy_xdp.h"

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_loadAndAttachBPFProgramToNic
  (PNIEnv_long* env, char * filepath_chars, char * prog_chars, char * ifname_chars, int32_t mode, uint8_t force_attach) {
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

    env->return_ = (int64_t) bpfobj;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_detachBPFProgramFromNic
  (PNIEnv_void* env, char * ifname_chars) {
    int err = vp_bpfobj_detach_from_if((char*)ifname_chars);
    if (err) {
        return throwIOExceptionBasedOnErrno(env);
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_findMapByNameInBPF
  (PNIEnv_long* env, int64_t bpfobj_o, char * name_chars) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;

    struct bpf_map* map = vp_bpfobj_find_map_by_name(bpfobj, (char*)name_chars);
    if (map == NULL) {
        return throwIOException(env, "vp_bpfobj_find_map_by_name failed");
    }

    env->return_ = (int64_t) map;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_createUMem
  (PNIEnv_long* env, int32_t chunk_size, int32_t fill_ring_size, int32_t comp_ring_size,
                              int32_t frame_size, int32_t headroom) {
    struct vp_umem_info* umem = vp_umem_create(chunk_size, fill_ring_size, comp_ring_size,
                                               frame_size, headroom);
    if (umem == NULL) {
        return throwIOException(env, "vp_umem_create failed");
    }
    env->return_ = (int64_t) umem;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_shareUMem
  (PNIEnv_long* env, int64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*)umem_o;
    env->return_ = (int64_t) vp_umem_share(umem);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_getBufferFromUMem
  (PNIEnv_buf_byte* env, int64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;

    char* buffer = umem->buffer;
    int len = umem->buffer_size;

    env->return_.buf = buffer;
    env->return_.bufLen = len;

    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_getBufferAddressFromUMem
  (PNIEnv_long* env, int64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    env->return_ = (int64_t) umem->buffer;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_createXSK
  (PNIEnv_long* env, char* ifname_chars, int32_t queue_id, int64_t umem_o,
                             int32_t rx_ring_size, int32_t tx_ring_size,
                             int32_t mode, uint8_t zero_copy,
                             int32_t busy_poll_budget,
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

    env->return_ = (int64_t) xsk;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_addXSKIntoMap
  (PNIEnv_void* env, int64_t map_o, int32_t key, int64_t xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_xsk_add_into_map(map, key, xsk);
    if (ret) {
        return throwIOException(env, "vp_xsk_add_into_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_addMacIntoMap
  (PNIEnv_void* env, int64_t map_o, void* mac, int64_t xsk_o) {
    struct bpf_map* map = (struct bpf_map*) map_o;
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;

    int ret = vp_mac_add_into_map(map, mac, xsk->ifindex);
    if (ret) {
        return throwIOException(env, "vp_mac_add_into_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_removeMacFromMap
  (PNIEnv_void* env, int64_t map_o, void* mac) {
    struct bpf_map* map = (struct bpf_map*) map_o;

    int ret = vp_mac_remove_from_map(map, mac);
    if (ret) {
        return throwIOException(env, "vp_mac_remove_from_map failed");
    }
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_getFDFromXSK
  (PNIEnv_int* env, int64_t xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    env->return_ = vp_xsk_socket_fd(xsk);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_fillUpFillRing0
  (int64_t umem_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_xdp_fill_ring_fillup(umem);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_fillUpFillRing
  (PNIEnv_void* env, int64_t umem_o) {
    io_vproxy_xdp_NativeXDP_fillUpFillRing0(umem_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_fetchPackets0
  (PNIEnv_int* env, int64_t xsk_o,
   int32_t capacity,
   void* _umemArray, void* _chunkArray, void* _refArray,
   void* _addrArray, void* _endaddrArray, void* _pktaddrArray, void* _pktlenArray) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    int64_t* umemArray = _umemArray;
    int64_t* chunkArray = _chunkArray;
    int32_t* refArray = _refArray;
    int32_t* addrArray = _addrArray;
    int32_t* endaddrArray = _endaddrArray;
    int32_t* pktaddrArray = _pktaddrArray;
    int32_t* pktlenArray = _pktlenArray;

    int32_t idx_rx = -1;
    struct vp_chunk_info* chunk;

    int cnt = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
    if (cnt <= 0) {
        env->return_ = cnt;
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
    env->return_ = cnt;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_rxRelease0
  (int64_t xsk_o, int32_t cnt) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_rx_release(xsk, cnt);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_rxRelease
  (PNIEnv_void* env, int64_t xsk_o, int32_t cnt) {
    io_vproxy_xdp_NativeXDP_rxRelease0(xsk_o, cnt);
    return 0;
}

inline static uint8_t io_vproxy_xdp_NativeXDP_writePacket0
  (int64_t xsk_o, int64_t chunk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;

    int ret = vp_xdp_write_pkt(xsk, chunk);
    if (ret) {
        return JNI_FALSE;
    } else {
        return JNI_TRUE;
    }
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_writePacket
  (PNIEnv_bool* env, int64_t xsk_o, int64_t chunk_o) {
    env->return_ = io_vproxy_xdp_NativeXDP_writePacket0(xsk_o, chunk_o);
    return 0;
}

inline static int io_vproxy_xdp_NativeXDP_writePackets0
  (int64_t xsk_o, int32_t size, int64_t* ptrs) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    return vp_xdp_write_pkts(xsk, size, (long*) ptrs);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_writePackets
  (PNIEnv_int* env, int64_t xsk_o, int32_t size, void* _ptrs) {
    int64_t* ptrs = _ptrs;

    uint8_t is_copy = false;
    if (ptrs == NULL) {
        env->return_ = 0;
        return 0;
    }
    int ret = io_vproxy_xdp_NativeXDP_writePackets0(xsk_o, size, ptrs);
    env->return_ = ret;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_completeTx0
  (int64_t xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xdp_complete_tx(xsk);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_completeTx
  (PNIEnv_void* env, int64_t xsk_o) {
    io_vproxy_xdp_NativeXDP_completeTx0(xsk_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_fetchChunk0
  (PNIEnv_bool* env, int64_t umem_o,
   void* _umemArray, void* _chunkArray, void* _refArray,
   void* _addrArray, void* _endaddrArray, void* _pktaddrArray, void* _pktlenArray) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = vp_chunk_fetch(umem->chunks);
    if (chunk == NULL) {
        env->return_ = JNI_FALSE;
        return 0;
    }
    int64_t* umemArray = _umemArray;
    int64_t* chunkArray = _chunkArray;
    int32_t* refArray = _refArray;
    int32_t* addrArray = _addrArray;
    int32_t* endaddrArray = _endaddrArray;
    int32_t* pktaddrArray = _pktaddrArray;
    int32_t* pktlenArray = _pktlenArray;

    umemArray[0]    = (size_t) chunk->umem;
    chunkArray[0]   = (size_t) chunk;
    refArray[0]     = chunk->ref;
    addrArray[0]    = chunk->addr;
    endaddrArray[0] = chunk->endaddr;
    pktaddrArray[0] = chunk->pktaddr;
    pktlenArray[0]  = chunk->pktlen;

    env->return_ = JNI_TRUE;
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_setChunk0
  (int64_t chunk_o, int32_t pktaddr, int32_t pktlen, int32_t csumFlags) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->pktaddr = pktaddr;
    chunk->pktlen = pktlen;
    chunk->csum_flags = csumFlags;
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_setChunk
  (PNIEnv_void* env, int64_t chunk_o, int32_t pktaddr, int32_t pktlen, int32_t csumFlags) {
    io_vproxy_xdp_NativeXDP_setChunk0(chunk_o, pktaddr, pktlen, csumFlags);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_releaseChunk0
  (int64_t umem_o, int64_t chunk_o) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    vp_chunk_release(umem->chunks, chunk);
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_releaseChunk
  (PNIEnv_void* env, int64_t umem_o, int64_t chunk_o) {
    io_vproxy_xdp_NativeXDP_releaseChunk0(umem_o, chunk_o);
    return 0;
}

inline static void io_vproxy_xdp_NativeXDP_addChunkRefCnt0
  (int64_t chunk_o) {
    struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_o;
    chunk->ref++;
}
JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_addChunkRefCnt
  (PNIEnv_void* env, int64_t chunk_o) {
    io_vproxy_xdp_NativeXDP_addChunkRefCnt0(chunk_o);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_releaseXSK
  (PNIEnv_void* env, int64_t xsk_o) {
    struct vp_xsk_info* xsk = (struct vp_xsk_info*) xsk_o;
    vp_xsk_close(xsk);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_releaseUMem
  (PNIEnv_void* env, int64_t umem_o, uint8_t release_buffer) {
    struct vp_umem_info* umem = (struct vp_umem_info*) umem_o;
    vp_umem_close(umem, release_buffer);
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_xdp_XDPNative_releaseBPFObject
  (PNIEnv_void* env, int64_t bpfobj_o) {
    struct bpf_object* bpfobj = (struct bpf_object*) bpfobj_o;
    vp_bpfobj_release(bpfobj);
    return 0;
}
