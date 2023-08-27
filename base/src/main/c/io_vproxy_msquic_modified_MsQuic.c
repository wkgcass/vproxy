#include "io_vproxy_msquic_modified_MsQuicUpcall.h"

static QUIC_STATUS vproxy_MsQuicUpcall_dispatch(void* Worker, CXPLAT_EVENTQ* EventQ, CXPLAT_THREAD* Thread) {
    int ret = JavaCritical_io_vproxy_msquic_modified_MsQuicUpcall_dispatch(Worker, *EventQ, Thread);
    if (ret) {
        return QUIC_STATUS_INVALID_STATE;
    }
    return QUIC_STATUS_SUCCESS;
}

#include "io_vproxy_msquic_modified_MsQuic.impl.h"
