#include "vproxy_xdp.h"

#include <errno.h>
#include <string.h>
#include <net/if.h>
#include <linux/if_link.h>

struct bpf_object* load_bpf_object_file(const char* filename, int ifindex) {
    struct bpf_prog_load_attr prog_load_attr = {
        .prog_type = BPF_PROG_TYPE_XDP,
    };
    prog_load_attr.file = filename;

    int foo;
    struct bpf_object* obj;
    int err = bpf_prog_load_xattr(&prog_load_attr, &obj, &foo);
    if (err) {
        fprintf(stderr, "ERR: bpf_prog_load_xattr failed: %d %s\n",
                -err, strerror(-err));
        return NULL;
    }

    return obj;
}

int xdp_link_attach(int ifindex, __u32 xdp_flags, int prog_fd) {
    int err = bpf_set_link_xdp_fd(ifindex, prog_fd, xdp_flags);
    if (err == -EEXIST && !(xdp_flags & XDP_FLAGS_UPDATE_IF_NOEXIST)) {
        /* Force mode didn't work, probably because a program of the
         * opposite type is loaded. Let's unload that and try loading
         * again
         */

        __u32 old_flags = xdp_flags;

        xdp_flags &= ~XDP_FLAGS_MODES;
        xdp_flags |= (old_flags & XDP_FLAGS_SKB_MODE) ? XDP_FLAGS_DRV_MODE : XDP_FLAGS_SKB_MODE;
        err = bpf_set_link_xdp_fd(ifindex, -1, xdp_flags);
        if (!err)
            err = bpf_set_link_xdp_fd(ifindex, prog_fd, old_flags);
    }
    if (err < 0) {
        switch(-err) {
            case EBUSY:
            case EEXIST:
                fprintf(stderr, "XDP already loaded on device\n");
                break;
            case EOPNOTSUPP:
                fprintf(stderr, "Native-XDP not supported, use skb mode instead\n");
                break;
            default:
                fprintf(stderr, "bpf_set_link_xdp_fd failed: %d %s\n",
                        -err, strerror(-err));
        }
        return 1;
    }
    return 0;
}
