#ifndef MSQUIC_USERDATA_H
#define MSQUIC_USERDATA_H

#include <inttypes.h>

#define CXPLAT_CQE_TYPE_USER_EVENT (0xFF0A)

typedef struct MsQuicUserData {
    uint32_t type;
    int      fd;
} MsQuicUserData;

#endif // MSQUIC_USERDATA_H
