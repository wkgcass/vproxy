#include "io_vproxy_msquic_MsQuicModUpcall.h"

static QUIC_STATUS vproxy_MsQuicUpcall_dispatch(void* Worker, CXPLAT_EVENTQ* EventQ, CXPLAT_THREAD* Thread, void* Context) {
    int ret = JavaCritical_io_vproxy_msquic_MsQuicModUpcall_dispatch(Worker, *EventQ, Thread, Context);
    if (ret) {
        return QUIC_STATUS_INVALID_STATE;
    }
    return QUIC_STATUS_SUCCESS;
}

#include "io_vproxy_msquic_MsQuicMod.impl.h"
