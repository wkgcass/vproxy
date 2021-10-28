#include <linux/bpf.h>
#include <bpf_helpers.h>

struct bpf_map_def SEC("maps") xsks_map = {
    .type = BPF_MAP_TYPE_XSKMAP,
    .max_entries = 128,
    .key_size = sizeof(int),
    .value_size = sizeof(int)
};

struct bpf_map_def SEC("maps") mac_map = {
    .type = BPF_MAP_TYPE_LRU_HASH,
    .max_entries = 4096,
    .key_size = sizeof(char[6]),
    .value_size = sizeof(int)
};

struct bpf_map_def SEC("maps") in_mac_map = {
    .type = BPF_MAP_TYPE_LRU_HASH,
    .max_entries = 4096,
    .key_size = sizeof(char[6]),
    .value_size = sizeof(int)
};

inline int redirect_pkt_count_check(struct xdp_md *ctx) {
    unsigned char* data_end = (unsigned char*) ((long) ctx->data_end);
    unsigned char* data     = (unsigned char*) ((long) ctx->data);
    unsigned char* pos      = data;
    pos += 12;
    if (pos > data_end) {
        return 0;
    }
    int* cnt_ptr = bpf_map_lookup_elem(&in_mac_map, data + 6);
    int cnt;
    if (cnt_ptr == NULL) {
        cnt = 1;
        bpf_map_update_elem(&in_mac_map, data + 6, &cnt, 0);
        return 0;
    }
    cnt = *cnt_ptr;
    *cnt_ptr += 1;
    if (cnt % 65536 == 0) {
        return 0;
    }
    return 1;
}

inline int redirect_pkt_by_mac(struct xdp_md *ctx) {
    unsigned char* data_end = (unsigned char*) ((long) ctx->data_end);
    unsigned char* data     = (unsigned char*) ((long) ctx->data);
    unsigned char* pos      = data;
    pos += 6;
    if (pos > data_end) {
        return XDP_DROP;
    }
    int* output_iface_ptr = bpf_map_lookup_elem(&mac_map, data);
    if (output_iface_ptr != NULL) {
        int output_iface = *output_iface_ptr;
        if (ctx->ingress_ifindex == output_iface) {
            return XDP_DROP;
        }

        if (redirect_pkt_count_check(ctx)) {
            return bpf_redirect(output_iface, 0);
        }
    }
    return XDP_DROP;
}

SEC("xdp_sock") int xdp_sock_prog(struct xdp_md *ctx)
{
    int redirect_result = redirect_pkt_by_mac(ctx);
    if (redirect_result != XDP_DROP) {
        return redirect_result;
    }
    return bpf_redirect_map(&xsks_map, ctx->rx_queue_index, XDP_DROP);
}
