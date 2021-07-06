#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>

struct bpf_map_def SEC("maps") xsks_map = {
    .type = BPF_MAP_TYPE_XSKMAP,
    .max_entries = 1,
    .key_size = sizeof(int),
    .value_size = sizeof(int)
};

SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
{
    return bpf_redirect_map(&xsks_map, 0, XDP_DROP);
}
