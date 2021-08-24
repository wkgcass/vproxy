#include "vproxy_xdp.h"

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <linux/if_link.h>
#include <poll.h>

// Usage:
//     hexDump(desc, addr, len, perLine);
//         desc:    if non-NULL, printed as a description before hex dump.
//         addr:    the address to start dumping from.
//         len:     the number of bytes to dump.
//         perLine: number of bytes on each output line.

// hexDump is copied from https://stackoverflow.com/questions/7775991/how-to-get-hexdump-of-a-structure-data
void hexDump (
    const char * desc,
    const void * addr,
    const int len,
    const int perLine
) {
    int i;
    unsigned char buff[perLine+1];
    const unsigned char * pc = (const unsigned char *)addr;

    // Output description if given.

    if (desc != NULL) printf ("%s:\n", desc);

    // Length checks.

    if (len == 0) {
        printf("  ZERO LENGTH\n");
        return;
    }
    if (len < 0) {
        printf("  NEGATIVE LENGTH: %d\n", len);
        return;
    }

    // Process every byte in the data.

    for (i = 0; i < len; i++) {
        // Multiple of perLine means new or first line (with line offset).

        if ((i % perLine) == 0) {
            // Only print previous-line ASCII buffer for lines beyond first.

            if (i != 0) printf ("  %s\n", buff);

            // Output the offset of current line.

            printf ("  %04x ", i);
        }

        // Now the hex code for the specific character.

        printf (" %02x", pc[i]);

        // And buffer a printable ASCII character for later.

        if ((pc[i] < 0x20) || (pc[i] > 0x7e)) // isprint() may be better.
            buff[i % perLine] = '.';
        else
            buff[i % perLine] = pc[i];
        buff[(i % perLine) + 1] = '\0';
    }

    // Pad out last line if not exactly perLine characters.

    while ((i % perLine) != 0) {
        printf ("   ");
        i++;
    }

    // And print the final ASCII buffer.

    printf ("  %s\n", buff);
}

int main(int argc, char** argv) {
    if (argc < 2) {
        printf("the first argument should be ifname to attach the ebpf program\n");
        return 1;
    }
    char* ifname = argv[1];

    struct bpf_object* bpfobj = vp_bpfobj_attach_to_if("sample_kern.o", "xdp_sock", ifname, XDP_FLAGS_SKB_MODE);
    if (bpfobj == NULL) {
        return 1;
    }
    struct bpf_map* map = vp_bpfobj_find_map_by_name(bpfobj, "xsks_map");
    if (map == NULL) {
        return 1;
    }

    struct vp_umem_info* umem = vp_umem_create(64, 32, 32, XSK_UMEM__DEFAULT_FRAME_SIZE, 0);
    if (umem == NULL) {
        return 1;
    }
    struct vp_xsk_info* xsk = vp_xsk_create(ifname, 0, umem, 32, 32, XDP_FLAGS_SKB_MODE, XDP_COPY, 0, 0);
    if (xsk == NULL) {
        return 1;
    }
    int ret = vp_xsk_add_into_map(map, 0, xsk);
    if (ret) {
        return 1;
    }

    vp_bpfobj_release(bpfobj);

    printf("ready to poll\n");

    struct pollfd fds[2];
    memset(fds, 0, sizeof(fds));
    fds[0].fd = vp_xsk_socket_fd(xsk);
    fds[0].events = POLLIN;

    int total = 128;
    int cnt = 0;
    printf("this program will recieve %d packets and then exit\n", total);
    while (1) {
        int ret = poll(fds, 1, -1);
        if (ret <= 0 || ret > 1) continue;

        printf("poll triggered\n");

        int32_t idx_rx = -1;
        struct vp_chunk_info* chunk;
        int rcvd = vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
        if (rcvd == 0) {
            continue;
        }
        printf("rcvd = %d\n", rcvd);
        for (int i = 0; i < rcvd; ++i) {
            vp_xdp_fetch_pkt(xsk, &idx_rx, &chunk);
            printf("received packet: cnt=%d addr=%lu pkt=%lu len=%d umem_size=%d umem_used=%d\n",
                   ++cnt, chunk->addr, ((size_t) chunk->pkt) - ((size_t) umem->buffer), chunk->pktlen,
                   umem->chunks->size, umem->chunks->used);
            hexDump("received", chunk->pkt, chunk->pktlen, 16);

            if (chunk->pktlen >= 12) {
                for (int i = 0; i < 6; ++i) {
                    char b = chunk->pkt[i];
                    chunk->pkt[i] = chunk->pkt[6 + i];
                    chunk->pkt[6 + i] = b;
                }
                if (cnt %2 == 0) {
                    printf("echo the packet without copying\n");
                    chunk->ref++;
                    vp_xdp_write_pkt(xsk, chunk);
                } else {
                    printf("copy and echo the packet\n");
                    struct vp_chunk_info* chunk2 = vp_chunk_fetch(umem->chunks);
                    if (chunk2 == NULL) {
                        printf("ERR! umem no enough chunks: size=%d used=%d\n",
                               umem->chunks->size, umem->chunks->used);
                        continue;
                    }
                    chunk2->pktaddr = chunk2->addr;
                    chunk2->pkt = chunk2->realaddr;
                    chunk2->pktlen = chunk->pktlen;
                    memcpy(chunk2->pkt, chunk->pkt, chunk->pktlen);

                    vp_xdp_write_pkt(xsk, chunk2);
                }
            }

            vp_chunk_release(umem->chunks, chunk);

            if (cnt == total) {
                goto out_loop;
            }
        }
        vp_xdp_rx_release(xsk, rcvd);
        vp_xdp_fill_ring_fillup(umem);
        vp_xdp_complete_tx(xsk);
    }
out_loop:
    vp_xsk_close(xsk);
    vp_umem_close(umem, true);

    printf("received %d packets, exit\n", cnt);
    return 0;
}
