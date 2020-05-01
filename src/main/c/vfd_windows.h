#ifndef VFD_WINDOWS
#define VFD_WINDOWS

#include <windows.h>
#include <winioctl.h>
#include <io.h>

#include <fcntl.h>
#include <unistd.h>

// for tap device
// copied from openvpn windows tap adapter driver
// https://github.com/OpenVPN/tap-windows6/blob/master/src/tap-windows.h
#define USERMODEDEVICEDIR "\\\\.\\Global\\"
#define SYSDEVICEDIR      "\\Device\\"
#define USERDEVICEDIR     "\\DosDevices\\Global\\"
#define TAP_WIN_SUFFIX    ".tap"
#define TAP_WIN_CONTROL_CODE(request,method) \
  CTL_CODE (FILE_DEVICE_UNKNOWN, request, method, FILE_ANY_ACCESS)
#define TAP_WIN_IOCTL_SET_MEDIA_STATUS      TAP_WIN_CONTROL_CODE (6, METHOD_BUFFERED)

#define NETWORK_CONNECTIONS_KEY "SYSTEM\\CurrentControlSet\\Control\\Network\\{4D36E972-E325-11CE-BFC1-08002BE10318}"

#endif
