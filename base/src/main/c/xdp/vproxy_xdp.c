#include "vproxy_xdp.h"

#include <errno.h>
#include <net/if.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

extern struct bpf_object* load_bpf_object_file(const char* filename, int ifindex);
extern int xdp_link_attach(int ifindex, __u32 xdp_flags, int prog_fd);

struct bpf_object* vp_bpfobj_attach_to_if(char* filepath, char* prog, char* ifname, int attach_flags) {
    int ifindex = if_nametoindex((const char*)ifname);
    if (ifindex <= 0) {
        fprintf(stderr, "ERR: if_nametoindex(%s) failed: %d %s\n",
                ifname, errno, strerror(errno));
        return NULL;
    }

    struct bpf_object* bpf_obj = NULL;

    bpf_obj = load_bpf_object_file(filepath, 0);
    if (!bpf_obj) {
        fprintf(stderr, "ERR: load_bpf_object_file(%s, 0) failed\n",
                filepath);
        goto err;
    }
    struct bpf_program* bpf_prog = bpf_object__find_program_by_title(bpf_obj, prog);
    if (!bpf_prog) {
        fprintf(stderr, "ERR: bpf_object__find_program_by_title(..., %s) failed\n",
                prog);
        goto err;
    }

    int prog_fd = bpf_program__fd(bpf_prog);
    if (prog_fd <= 0) {
        fprintf(stderr, "ERR: bpf_program__fd(...) failed: %d %s",
                -prog_fd, strerror(-prog_fd));
        goto err;
    }

    int err = xdp_link_attach(ifindex, attach_flags, prog_fd);
    if (err) {
        fprintf(stderr, "ERR: xdp_link_attach(%d, %d, ...) failed: %d %s\n",
                ifindex, attach_flags, -err, strerror(-err));
        goto err;
    }

    return bpf_obj;

err:
    if (bpf_obj != NULL) {
        bpf_object__close(bpf_obj);
    }
    return NULL;
}

struct bpf_map* vp_bpfobj_find_map_by_name(struct bpf_object* bpfobj, char* name) {
    struct bpf_map* ret = bpf_object__find_map_by_name(bpfobj, name);
    if (ret == NULL) {
        fprintf(stderr, "ERR: bpf_object__find_map_by_name(..., %s)\n",
                name);
    }
    return ret;
}

struct vp_umem_info* vp_umem_create(int chunks_size, int fill_ring_size, int comp_ring_size,
                                    uint64_t frame_size, int headroom) {
    if (chunks_size < fill_ring_size) {
        fprintf(stderr, "WARN: chunks_size %d < fill_ring_size %d, set chunks_size to fill_ring_size",
                chunks_size, fill_ring_size);
        chunks_size = fill_ring_size;
    }
    int mem_size = chunks_size * frame_size;

    void* buffer = NULL;
    struct vp_umem_info* umem = NULL;

    if (posix_memalign(&buffer, getpagesize(), mem_size)) {
        fprintf(stderr, "ERROR: Can't allocate buffer memory \"%s\"\n",
                strerror(errno));
        goto err;
    }

    umem = calloc(1, sizeof(struct vp_umem_info) +
                     sizeof(struct vp_chunk_array) +
                     chunks_size * sizeof(struct vp_chunk_info));
    if (!umem) {
        fprintf(stderr, "ERR: allocating umem info failed");
        goto err;
    }

    struct xsk_umem_config umem_config = {
        .fill_size = fill_ring_size,
        .comp_size = comp_ring_size,
        .frame_size = frame_size,
        .frame_headroom = headroom,
        .flags = 0
    };

    int ret = xsk_umem__create(&umem->umem, buffer, mem_size, &umem->fill_ring, &umem->comp_ring, &umem_config);
    if (ret) {
        fprintf(stderr, "ERR: xsk_umem__create failed: %d %s\n",
                -ret, strerror(-ret));
        goto err;
    }

    umem->buffer = buffer;
    umem->buffer_size = mem_size;

    umem->chunks = (struct vp_chunk_array*) (((size_t)umem) + sizeof(struct vp_umem_info));
    umem->chunks->frame_size = frame_size;
    umem->chunks->size = chunks_size;
    umem->chunks->used = 0;
    umem->chunks->idx = 0;
    umem->chunks->array = (struct vp_chunk_info*) (((size_t)umem) + sizeof(struct vp_umem_info) + sizeof(struct vp_chunk_array));
    for (int i = 0; i < chunks_size; ++i) {
        umem->chunks->array[i].umem = umem;
        umem->chunks->array[i].xsk = NULL;
        umem->chunks->array[i].ref = 0;
        umem->chunks->array[i].addr = i * frame_size;
        umem->chunks->array[i].endaddr = (i + 1) * frame_size;
        umem->chunks->array[i].realaddr = (char*) (((size_t)buffer) + umem->chunks->array[i].addr);
        umem->chunks->array[i].pkt = NULL;
    }

    // fillup the fill ring
    vp_xdp_fill_ring_fillup(umem);

    return umem;
err:
    if (umem != NULL) {
        if (umem->umem != NULL) {
            xsk_umem__delete(umem->umem);
        }
        free(umem);
    }
    if (buffer != NULL) {
        free(buffer);
    }
    return NULL;
}

struct vp_umem_info* vp_umem_share(struct vp_umem_info* umem) {
    struct vp_umem_info* ret = calloc(1, sizeof(struct vp_umem_info));
    memcpy(ret, umem, sizeof(struct vp_umem_info));
    memset(&ret->fill_ring, 0, sizeof(struct xsk_ring_prod));
    memset(&ret->comp_ring, 0, sizeof(struct xsk_ring_cons));
    ret->is_shared = 1;
    return ret;
}

struct vp_xsk_info* vp_xsk_create(char* ifname, int queue_id, struct vp_umem_info* umem,
                                  int rx_ring_size, int tx_ring_size, int xdp_flags, int bind_flags,
                                  int busy_poll_budget) {
    int ifindex = if_nametoindex((const char*)ifname);
    if (ifindex <= 0) {
        fprintf(stderr, "ERR: if_nametoindex(%s) failed: %d %s\n",
                ifname, errno, strerror(errno));
        return NULL;
    }

    struct vp_xsk_info* xsk_info = NULL;

    xsk_info = calloc(1, sizeof(struct vp_xsk_info));
    if (!xsk_info) {
        fprintf(stderr, "ERR: allocating xsk info failed\n");
        goto err;
    }

    xsk_info->umem = umem;

    struct xsk_socket_config xsk_cfg;
    xsk_cfg.rx_size = rx_ring_size;
    xsk_cfg.tx_size = tx_ring_size;
    xsk_cfg.libbpf_flags = XSK_LIBBPF_FLAGS__INHIBIT_PROG_LOAD;
    xsk_cfg.xdp_flags = xdp_flags;
    xsk_cfg.bind_flags = bind_flags;
    xsk_cfg.busy_poll_budget = busy_poll_budget;

    int ret;
    if (umem->is_shared) {
        ret = xsk_socket__create_shared(&xsk_info->xsk, ifname, queue_id, umem->umem, &xsk_info->rx, &xsk_info->tx, &umem->fill_ring, &umem->comp_ring, &xsk_cfg);

        vp_xdp_fill_ring_fillup(umem);
    } else {
        ret = xsk_socket__create(&xsk_info->xsk, ifname, queue_id, umem->umem, &xsk_info->rx, &xsk_info->tx, &xsk_cfg);
    }
    if (ret) {
        fprintf(stderr, "ERR: xsk_socket__create failed: %d %s\n",
                -ret, strerror(-ret));
        goto err;
    }

    xsk_info->rx_ring_size = rx_ring_size;
    xsk_info->tx_ring_size = tx_ring_size;
    xsk_info->outstanding_tx = 0;

    return xsk_info;
err:
    if (xsk_info != NULL) {
        if (xsk_info->xsk == NULL) {
            xsk_socket__delete(xsk_info->xsk);
        }
        free(xsk_info);
    }
    return NULL;
}

int vp_xsk_add_into_map(struct bpf_map* map, int key, struct vp_xsk_info* xsk) {
    int xsks_map_fd = bpf_map__fd(map);
    if (xsks_map_fd < 0) {
        fprintf(stderr, "ERR: bpf_map__fd failed: %d %s\n",
                -xsks_map_fd, strerror(-xsks_map_fd));
        return -1;
    }
    int fd = xsk_socket__fd(xsk->xsk);
    if (fd < 0) {
        fprintf(stderr, "ERR: xsks_socket__fd failed: %d %s\n",
                -fd, strerror(-fd));
        return -1;
    }
    int ret = bpf_map_update_elem(xsks_map_fd, &key, &fd, 0);
    if (ret) {
        fprintf(stderr, "ERR: bpf_map_update_elem failed\n");
        return -1;
    }
    return 0;
}

int vp_xsk_socket_fd(struct vp_xsk_info* xsk) {
    return xsk_socket__fd(xsk->xsk);
}

void vp_xsk_close(struct vp_xsk_info* xsk) {
    // drop rx ring
    {
        uint32_t idx_rx = -1;
        struct vp_chunk_info* chunk;
        int recv = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
        if (recv > 0) {
            for (int i = 0; i < recv; ++i) {
                vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
                vp_chunk_release(xsk->umem->chunks, chunk);
            }
            vp_xdp_rx_release(xsk, recv);
        }
    }
    // complete tx
    {
        vp_xdp_complete_tx(xsk);
    }
    // ensure chunks are not associated with this xsk
    for (int i = 0; i < xsk->umem->chunks->size; ++i) {
        struct vp_chunk_info* chunk = &xsk->umem->chunks->array[i];
        if (chunk->xsk == xsk) {
            fprintf(stderr, "releasing chunk %lu(ref:%d) due to xsk closing\n", (size_t)chunk->realaddr, chunk->ref);
            chunk->xsk = NULL;
            vp_chunk_release(xsk->umem->chunks, chunk);
        }
    }

    xsk_socket__delete(xsk->xsk);
    free(xsk);
}

void vp_umem_close(struct vp_umem_info* umem, bool clean_buffer) {
    xsk_umem__delete(umem->umem);
    if (!umem->is_shared && clean_buffer) {
        free(umem->buffer);
    }
    free(umem);
}

void vp_xdp_fill_ring_fillup(struct vp_umem_info* umem) {
    unsigned int stock_frames = xsk_prod_nb_free(&umem->fill_ring, umem->chunks->size - umem->chunks->used);
    if (stock_frames <= 0) {
        return;
    }
    if (stock_frames > umem->chunks->size - umem->chunks->used) {
        stock_frames = umem->chunks->size - umem->chunks->used;
    }
    uint32_t idx_fr = 0;
    xsk_ring_prod__reserve(&umem->fill_ring, stock_frames, &idx_fr);
    int i = 0;
    for (; i < stock_frames; ++i) {
        struct vp_chunk_info* chunk = vp_chunk_fetch(umem->chunks);
        if (chunk == NULL) {
            fprintf(stderr, "cannot fetch chunk to fill the fillring\n");
            break; // no free chunks, cannot fill up. however this should not happen
        }
        *xsk_ring_prod__fill_addr(&umem->fill_ring, idx_fr++) = chunk->addr;
    }
    if (i > 0) {
        xsk_ring_prod__submit(&umem->fill_ring, i);
    }
}

int vp_xdp_fetch_pkt(struct vp_xsk_info* xsk, uint32_t* idx_rx_ptr, struct vp_chunk_info** chunkptr) {
    if ((int)(*idx_rx_ptr) < 0) {
        unsigned int rcvd = xsk_ring_cons__peek(&xsk->rx, xsk->rx_ring_size, idx_rx_ptr);
        if (!rcvd) {
            return 0;
        } else {
            return rcvd;
        }
    }

    uint32_t idx_rx = *idx_rx_ptr;
    uint64_t addr = xsk_ring_cons__rx_desc(&xsk->rx, idx_rx)->addr;
    uint32_t len = xsk_ring_cons__rx_desc(&xsk->rx, idx_rx)->len;
    *idx_rx_ptr = idx_rx + 1;
    char* pkt = xsk_umem__get_data(xsk->umem->buffer, addr);

    struct vp_chunk_info* chunk = vp_chunk_seek(xsk->umem->chunks, addr);
    // assert chunk != null

    chunk->pktaddr = addr;
    chunk->pkt = pkt;
    chunk->pktlen = len;

    *chunkptr = chunk;

    return 0;
}

void vp_xdp_rx_release(struct vp_xsk_info* xsk, int cnt) {
    xsk_ring_cons__release(&xsk->rx, cnt);
}

int vp_xdp_write_pkt(struct vp_xsk_info* xsk, struct vp_chunk_info* chunk) {
    uint32_t tx_idx = 0;
    int ret = xsk_ring_prod__reserve(&xsk->tx, 1, &tx_idx);
    if (ret != 1) {
        return -1;
    }
    xsk_ring_prod__tx_desc(&xsk->tx, tx_idx)->addr = chunk->pktaddr;
    xsk_ring_prod__tx_desc(&xsk->tx, tx_idx)->len = chunk->pktlen;
    xsk_ring_prod__submit(&xsk->tx, 1);
    chunk->xsk = xsk;

    xsk->outstanding_tx++;

    return 0;
}

int vp_xdp_write_pkts(struct vp_xsk_info* xsk, int size, long* chunk_ptrs) {
    uint32_t tx_idx = 0;
    int ret = xsk_ring_prod__reserve(&xsk->tx, size, &tx_idx);
    if (ret <= 0) {
        return 0;
    }
    for (int i = 0; i < ret; ++i) {
        struct vp_chunk_info* chunk = (struct vp_chunk_info*) chunk_ptrs[i];
        xsk_ring_prod__tx_desc(&xsk->tx, tx_idx + i)->addr = chunk->pktaddr;
        xsk_ring_prod__tx_desc(&xsk->tx, tx_idx + i)->len = chunk->pktlen;
        chunk->xsk = xsk;
    }
    xsk_ring_prod__submit(&xsk->tx, ret);

    xsk->outstanding_tx += ret;

    return ret;
}

void vp_xdp_complete_tx(struct vp_xsk_info* xsk) {
    if (!xsk->outstanding_tx) {
        return;
    }

    sendto(xsk_socket__fd(xsk->xsk), NULL, 0, MSG_DONTWAIT, NULL, 0);

    /* Collect/free completed TX buffers */
    uint32_t idx_cr;
    unsigned int completed = xsk_ring_cons__peek(&xsk->umem->comp_ring,
                                                 xsk->tx_ring_size,
                                                 &idx_cr);
    if (completed > 0) {
        for (int i = 0; i < completed; ++i) {
            uint64_t addr = *xsk_ring_cons__comp_addr(&xsk->umem->comp_ring, idx_cr++);
            struct vp_chunk_info* chunk = vp_chunk_seek(xsk->umem->chunks, addr);
            // assert chunk != null
            chunk->xsk = NULL;
            vp_chunk_release(xsk->umem->chunks, chunk);
            xsk_ring_cons__release(&xsk->umem->comp_ring, completed);
        }
        xsk->outstanding_tx -= completed;
    }
}

void vp_bpfobj_release(struct bpf_object* bpf_obj) {
    bpf_object__close(bpf_obj);
}
