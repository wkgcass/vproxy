#include "vfd_posix.h"
#include "vfd_windows.h"
#include "exception.h"
#include "io_vproxy_vfd_windows_WindowsNative.h"

static LPFN_CONNECTEX ConnectExPtr;

static inline LPFN_CONNECTEX GetConnectEx() {
    if (ConnectExPtr != NULL) {
        return ConnectExPtr;
    }
    SOCKET s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    GUID guid = WSAID_CONNECTEX;
    int numBytes = 0;
    WSAIoctl(s, SIO_GET_EXTENSION_FUNCTION_POINTER, &guid, sizeof(guid),
             &ConnectExPtr, sizeof(ConnectExPtr), (LPDWORD)&numBytes, NULL, NULL);
    // should succeed
    return ConnectExPtr;
}

extern void formatSocketAddressIPv4(v_sockaddr_in* addr, SocketAddressIPv4_st* st);
extern SocketAddressIPv6_st* formatSocketAddressIPv6(void* env, v_sockaddr_in6* addr, SocketAddressIPv6_st* st);

#include "io_vproxy_vfd_windows_WindowsNative.impl.h"
#include "io_vproxy_vfd_windows_IOCP.impl.h"

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_tapNonBlockingSupported
  (PNIEnv_bool* env) {
    env->return_ = JNI_TRUE;
    return 0;
}

#define GUID_MAX_LEN 256

BOOL findTapGuidByNameInNetworkPanel(void* env, const char* dev, char* guid) {
    LONG res;
    HKEY network_connections_key;
    res = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       NETWORK_CONNECTIONS_KEY,
                       0,
                       KEY_READ,
                       &network_connections_key);
    if (res != ERROR_SUCCESS) {
        throwIOExceptionBasedOnErrnoWithPrefix(env, "failed to open NETWORK_CONNECTIONS_KEY");
        return FALSE;
    }
    int i = 0;
    while (1) {
        char enum_name[GUID_MAX_LEN];
        char connection_string[256];
        HKEY connection_key;
        WCHAR name_data[256];
        DWORD name_type;
        const WCHAR name_string[] = L"Name";
        DWORD len = sizeof(enum_name);
        res = RegEnumKeyEx(network_connections_key, i++, enum_name, &len,
                           NULL, NULL, NULL, NULL);
        if (res == ERROR_NO_MORE_ITEMS) {
            break;
        } else if (res != ERROR_SUCCESS) {
            throwIOExceptionBasedOnErrnoWithPrefix(env, "failed to enumerate on keys");
            return FALSE;
        }
        _snprintf(connection_string, sizeof(connection_string), "%s\\%s\\Connection",
                 NETWORK_CONNECTIONS_KEY,
                 enum_name);
        res = RegOpenKeyEx(HKEY_LOCAL_MACHINE, connection_string, 0, KEY_READ, &connection_key);
        if (res != ERROR_SUCCESS) {
            // do not throw error here, simply skip the key
            continue;
        }
        len = sizeof(name_data);
        res = RegQueryValueExW(connection_key, name_string, NULL, &name_type, (LPBYTE) name_data, &len);
        if (res != ERROR_SUCCESS) {
            throwIOExceptionBasedOnErrnoWithPrefix(env, "RegQueryValueExW failed");
            return FALSE;
        }
        if (name_type != REG_SZ) {
            continue;
        }
        int n = WideCharToMultiByte(CP_UTF8, 0, name_data, -1, NULL, 0, NULL, NULL);
        char* name = malloc(n);
        WideCharToMultiByte(CP_UTF8, 0, name_data, -1, name, n, NULL, NULL);
        if (strcmp(dev, name) == 0) {
            // found
            memcpy(guid, enum_name, sizeof(enum_name));
            return TRUE;
        } else {
            free(name);
        }
        // continue loop
    }
    throwIOException(env, "tap not found");
    return FALSE;
}

BOOL openTapDevice(void* env, char* guid, HANDLE* outHandle) {
    char tuntap_device_path[1024];
    sprintf(tuntap_device_path, "%s%s%s", USERMODEDEVICEDIR, guid, TAP_WIN_SUFFIX);
    HANDLE handle = CreateFile(tuntap_device_path,
                    GENERIC_READ | GENERIC_WRITE,
                    0,
                    0,
                    OPEN_EXISTING,
                    FILE_ATTRIBUTE_SYSTEM | FILE_FLAG_OVERLAPPED,
                    0);
    if (handle == INVALID_HANDLE_VALUE) {
        throwIOExceptionBasedOnErrnoWithPrefix(env, "open tap device failed");
        return FALSE;
    }
    *outHandle = handle;
    return TRUE;
}

BOOL plugCableToTapDevice(void* env, HANDLE handle) {
    ULONG x = TRUE;
    DWORD len;
    if (DeviceIoControl(handle, TAP_WIN_IOCTL_SET_MEDIA_STATUS, &x, sizeof(x), &x, sizeof(x), &len, NULL)) {
        return TRUE;
    } else {
        throwIOExceptionBasedOnErrnoWithPrefix(env, "setting device to CONNECTED failed");
        return FALSE;
    }
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_createTapHandle
  (PNIEnv_long* env, char* devChars) {
    BOOL status;
    char guid[GUID_MAX_LEN];
    HANDLE handle;
    status = findTapGuidByNameInNetworkPanel(env, devChars, guid);
    if (!status) {
        return -1;
    }
    status = openTapDevice(env, guid, &handle);
    if (!status) {
        return -1;
    }
    status = plugCableToTapDevice(env, handle);
    if (!status) {
        CloseHandle(handle);
        return -1;
    }
    env->return_ = (void*) handle;
    return 0;
}
