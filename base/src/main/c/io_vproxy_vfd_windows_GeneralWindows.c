#include "io_vproxy_vfd_windows_GeneralWindows.h"
#include "vfd_windows.h"

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_tapNonBlockingSupported
  (JEnv* env) {
    env->return_z = JNI_FALSE;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_allocateOverlapped
  (JEnv* env) {
    OVERLAPPED* ov = malloc(sizeof(OVERLAPPED));
    memset(ov, 0, sizeof(OVERLAPPED));
    HANDLE event = CreateEvent(NULL, FALSE, FALSE, NULL);
    if (event == NULL) {
        throwIOExceptionBasedOnLastError(env, "create event failed");
        free(ov);
        env->return_j = 0;
        return;
    }
    ov->hEvent = event;
    env->return_j = (jlong) ov;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_releaseOverlapped
  (JEnv* env, uint64_t ovJ) {
    OVERLAPPED* ov = (OVERLAPPED*) ovJ;
    HANDLE event = ov->hEvent;
    BOOL status = CloseHandle(event);
    if (!status) {
        throwIOExceptionBasedOnLastError(env, "close event failed");
        return;
    }
    free(ov);
}

#define GUID_MAX_LEN 256

BOOL findTapGuidByNameInNetworkPanel(JEnv* env, const char* dev, char* guid) {
    LONG res;
    HKEY network_connections_key;
    res = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       NETWORK_CONNECTIONS_KEY,
                       0,
                       KEY_READ,
                       &network_connections_key);
    if (res != ERROR_SUCCESS) {
        throwIOExceptionBasedOnLastError(env, "failed to open NETWORK_CONNECTIONS_KEY");
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
            throwIOExceptionBasedOnLastError(env, "failed to enumerate on keys");
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
            throwIOExceptionBasedOnLastError(env, "RegQueryValueExW failed");
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

BOOL openTapDevice(JEnv* env, char* guid, HANDLE* outHandle) {
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
        throwIOExceptionBasedOnLastError(env, "open tap device failed");
        return FALSE;
    }
    *outHandle = handle;
    return TRUE;
}

BOOL plugCableToTabDevice(JEnv* env, HANDLE handle) {
    ULONG x = TRUE;
    DWORD len;
    if (DeviceIoControl(handle, TAP_WIN_IOCTL_SET_MEDIA_STATUS, &x, sizeof(x), &x, sizeof(x), &len, NULL)) {
        return TRUE;
    } else {
        throwIOExceptionBasedOnLastError(env, "setting device to CONNECTED failed");
        return FALSE;
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_createTapHandle
  (JEnv* env, char* devChars) {
    BOOL status;
    char guid[GUID_MAX_LEN];
    HANDLE handle;
    status = findTapGuidByNameInNetworkPanel(env, devChars, guid);
    if (!status) {
        env->return_j = 0;
        return;
    }
    status = openTapDevice(env, guid, &handle);
    if (!status) {
        env->return_j = 0;
        return;
    }
    status = plugCableToTabDevice(env, handle);
    if (!status) {
        CloseHandle(handle);
        env->return_j = 0;
        return;
    }
    env->return_j = (jlong) handle;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_closeHandle
  (JEnv* env, uint64_t handleJ) {
    HANDLE handle = (HANDLE) handleJ;
    BOOL status = CloseHandle(handle);
    if (!status) {
        throwIOExceptionBasedOnLastError(env, "close failed");
    }
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_read
  (JEnv* env, uint64_t handleJ, void* directBuffer, uint32_t off, uint32_t len, uint64_t ovJ) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (void*) directBuffer;
    DWORD n = 0;
    HANDLE handle = (HANDLE) handleJ;
    OVERLAPPED* ov = (OVERLAPPED*) ovJ;
    BOOL status = ReadFile(handle, buf + off, len, NULL, ov);
    if (!status && GetLastError() == ERROR_IO_PENDING) {
        DWORD waitStatus = WaitForSingleObject(ov->hEvent, INFINITE);
        if (waitStatus == WAIT_FAILED) {
            throwIOExceptionBasedOnLastError(env, "wait failed when reading");
            return;
        } else if (waitStatus != WAIT_OBJECT_0) {
            if (waitStatus == WAIT_ABANDONED) {
                throwIOException(env, "WAIT_ABANDONED when reading");
            } else if (waitStatus == WAIT_TIMEOUT) {
                throwIOException(env, "WAIT_TIMEOUT when reading");
            } else {
                throwIOException(env, "unknown WAIT error when reading");
            }
            return;
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
        throwIOExceptionBasedOnLastError(env, "read failed");
        return;
    }
    env->return_i = (jint) n;
}

JNIEXPORT void JNICALL Java_io_vproxy_vfd_windows_GeneralWindows_write
  (JEnv* env, uint64_t handleJ, void * directBuffer, uint32_t off, uint32_t len, uint64_t ovJ) {
    if (len == 0) {
        env->return_i = 0;
        return;
    }
    byte* buf = (void*) directBuffer;
    DWORD n = 0;
    HANDLE handle = (HANDLE) handleJ;
    OVERLAPPED* ov = (OVERLAPPED*) ovJ;
    BOOL status = WriteFile(handle, buf + off, len, NULL, ov);
    if (!status && GetLastError() == ERROR_IO_PENDING) {
        DWORD waitStatus = WaitForSingleObject(ov->hEvent, INFINITE);
        if (waitStatus == WAIT_FAILED) {
            throwIOExceptionBasedOnLastError(env, "wait failed when writing");
            return;
        } else if (waitStatus != WAIT_OBJECT_0) {
            if (waitStatus == WAIT_ABANDONED) {
                throwIOException(env, "WAIT_ABANDONED when writing");
            } else if (waitStatus == WAIT_TIMEOUT) {
                throwIOException(env, "WAIT_TIMEOUT when writing");
            } else {
                throwIOException(env, "unknown WAIT error when writing");
            }
            return;
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
        throwIOExceptionBasedOnLastError(env, "write failed");
        return;
    }
    env->return_i = (jint) n;
}
