#include <winsock2.h>
#include <mswsock.h>
#include "vfd_windows.h"
#include "vfd_posix.h"

static LPFN_CONNECTEX ConnectExPtr;

static inline LPFN_CONNECTEX GetConnectEx() {
    if (ConnectExPtr != NULL) {
        return ConnectExPtr;
    }
    SOCKET s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    GUID guid = WSAID_CONNECTEX;
    int numBytes = 0;
    WSAIoctl(s, SIO_GET_EXTENSION_FUNCTION_POINTER, &guid, sizeof(guid),
             &ConnectExPtr, sizeof(ConnectExPtr), &numBytes, NULL, NULL);
    // should succeed
    return ConnectExPtr;
}

#include "io_vproxy_vfd_windows_WindowsNative.impl.h"

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
        snprintf(connection_string, sizeof(connection_string), "%s\\%s\\Connection",
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

BOOL plugCableToTabDevice(void* env, HANDLE handle) {
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
    status = plugCableToTabDevice(env, handle);
    if (!status) {
        CloseHandle(handle);
        return -1;
    }
    env->return_ = (jlong) handle;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_read
  (PNIEnv_int* env, int64_t handleJ, void* directBuffer, int32_t off, int32_t len, int64_t ovJ) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (void*) directBuffer;
    DWORD n = 0;
    HANDLE handle = (HANDLE) handleJ;
    OVERLAPPED* ov = (OVERLAPPED*) ovJ;
    BOOL status = ReadFile(handle, buf + off, len, NULL, ov);
    if (!status && GetLastError() == ERROR_IO_PENDING) {
        DWORD waitStatus = WaitForSingleObject(ov->hEvent, INFINITE);
        if (waitStatus == WAIT_FAILED) {
            return throwIOExceptionBasedOnErrnoWithPrefix(env, "wait failed when reading");
        } else if (waitStatus != WAIT_OBJECT_0) {
            if (waitStatus == WAIT_ABANDONED) {
                return throwIOException(env, "WAIT_ABANDONED when reading");
            } else if (waitStatus == WAIT_TIMEOUT) {
                return throwIOException(env, "WAIT_TIMEOUT when reading");
            } else {
                return throwIOException(env, "unknown WAIT error when reading");
            }
        }
        status = GetOverlappedResult(handle, ov, &n, TRUE);
        if (status) {
            unsigned long long offset = ((unsigned long long) (ov->Offset)) |
                                       (((unsigned long long) (ov->OffsetHigh)) << 32);
            offset += n;
            ov->Offset = offset & 0xffffffff;
            ov->OffsetHigh = (offset >> 32) & 0xffffffff;
        }
    }
    if (!status) {
        return throwIOExceptionBasedOnErrnoWithPrefix(env, "read failed");
    }
    env->return_ = (jint) n;
    return 0;
}

JNIEXPORT int JNICALL Java_io_vproxy_vfd_windows_WindowsNative_write
  (PNIEnv_int* env, int64_t handleJ, void * directBuffer, int32_t off, int32_t len, int64_t ovJ) {
    if (len == 0) {
        env->return_ = 0;
        return 0;
    }
    byte* buf = (void*) directBuffer;
    DWORD n = 0;
    HANDLE handle = (HANDLE) handleJ;
    OVERLAPPED* ov = (OVERLAPPED*) ovJ;
    BOOL status = WriteFile(handle, buf + off, len, NULL, ov);
    if (!status && GetLastError() == ERROR_IO_PENDING) {
        DWORD waitStatus = WaitForSingleObject(ov->hEvent, INFINITE);
        if (waitStatus == WAIT_FAILED) {
            return throwIOExceptionBasedOnErrnoWithPrefix(env, "wait failed when writing");
        } else if (waitStatus != WAIT_OBJECT_0) {
            if (waitStatus == WAIT_ABANDONED) {
                return throwIOException(env, "WAIT_ABANDONED when writing");
            } else if (waitStatus == WAIT_TIMEOUT) {
                return throwIOException(env, "WAIT_TIMEOUT when writing");
            } else {
                return throwIOException(env, "unknown WAIT error when writing");
            }
        }
        status = GetOverlappedResult(handle, ov, &n, TRUE);
        if (status) {
            unsigned long long offset = ((unsigned long long) (ov->Offset)) |
                                       (((unsigned long long) (ov->OffsetHigh)) << 32);
            offset += n;
            ov->Offset = offset & 0xffffffff;
            ov->OffsetHigh = (offset >> 32) & 0xffffffff;
        }
    }
    if (!status) {
        return throwIOExceptionBasedOnErrnoWithPrefix(env, "write failed");
    }
    env->return_ = (jint) n;
    return 0;
}
