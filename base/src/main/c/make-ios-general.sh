#!/bin/bash
# Build the vfdposix native library (GeneralPosix + libae) as an iOS
# framework, for a statically linked embedded iOS JVM. This is the iOS
# counterpart of make-general.sh.
#
# Cross compilation:
#   - SDK: `xcrun --sdk $SDK_NAME --show-sdk-path` (override with IOS_SDK)
#   - target triple: arm64-apple-ios$MIN_IOS_VERSION (+ -simulator for
#     SDK_NAME=iphonesimulator)
#
# libpni.framework (make-ios-pni.sh) must be built first: this script links
# against it with -lpni, exactly like make-general.sh does on the desktop.
# The resulting dependency is @rpath/libpni.framework/libpni.dylib; dyld
# resolves it either against the already-loaded libpni (Main loads it first)
# or through the app's standard @executable_path/Frameworks rpath.
#
# The executable is named libvfdposix.dylib inside the framework, mirroring
# the desktop dylib, so the unmodified vproxy jar can load it with
# System.loadLibrary("vfdposix") when java.library.path contains the
# framework directory.
#
# Output: libvfdposix.framework/ next to this script.

set -euo pipefail

cd "$(dirname "$0")"

os=`uname`
if [[ "Darwin" != "$os" ]]; then
    echo "unsupported platform $os, iOS frameworks must be built on macOS"
    exit 1
fi

SDK_NAME="${SDK_NAME:-iphoneos}"
if [[ "$SDK_NAME" == *simulator* ]]; then
    # arm64 simulator slices start at iOS 14.0 (lower values are silently
    # bumped by clang, which would desync the binary from Info.plist)
    MIN_IOS_VERSION="${MIN_IOS_VERSION:-14.0}"
else
    MIN_IOS_VERSION="${MIN_IOS_VERSION:-12.0}"
fi
if [[ -z "${IOS_SDK:-}" ]]; then
    IOS_SDK="`xcrun --sdk "$SDK_NAME" --show-sdk-path`"
fi
if [[ ! -d "$IOS_SDK" ]]; then
    echo "iOS SDK not found: $IOS_SDK"
    exit 1
fi

TRIPLE="arm64-apple-ios$MIN_IOS_VERSION"
BUNDLE_PLATFORM="iPhoneOS"
if [[ "$SDK_NAME" == *simulator* ]]; then
    TRIPLE="$TRIPLE-simulator"
    BUNDLE_PLATFORM="iPhoneSimulator"
fi

LIBAE="../../../../submodules/libae/src"

GENERATED_PATH="../c-generated"
if [ "${VPROXY_BUILD_GRAAL_NATIVE_IMAGE:-}" == "true" ]; then
    GENERATED_PATH="${GENERATED_PATH}-graal"
    GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

# fail fast when the PNI generated code or libae is missing
for f in "$GENERATED_PATH/pni.h" "$GENERATED_PATH/io_vproxy_vfd_posix_PosixNative.h"; do
    if [[ ! -f "$f" ]]; then
        echo "missing $f, run ./gradlew :base:pniGenerate first"
        exit 1
    fi
done
if [[ ! -f "$LIBAE/ae.c" ]]; then
    echo "missing $LIBAE/ae.c, run 'git submodule update --init --recursive' first"
    exit 1
fi

FW_PNI="libpni.framework"
if [[ ! -f "$FW_PNI/libpni.dylib" ]]; then
    echo "missing $FW_PNI, run ./make-ios-pni.sh first (build with the same SDK_NAME/IOS_SDK/MIN_IOS_VERSION)"
    exit 1
fi

FW_NAME="libvfdposix"
FW="$FW_NAME.framework"
EXEC="libvfdposix.dylib"

# the framework is consumed through JNI (the JVM resolves symbols by name at
# runtime), so the umbrella header only documents the payload
rm -rf "$FW"
mkdir -p "$FW/Headers" "$FW/Modules"

cat > "$FW/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleExecutable</key>
	<string>$EXEC</string>
	<key>CFBundleIdentifier</key>
	<string>io.vproxy.$FW_NAME</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>$FW_NAME</string>
	<key>CFBundlePackageType</key>
	<string>FMWK</string>
	<key>CFBundleShortVersionString</key>
	<string>1.0</string>
	<key>CFBundleSupportedPlatforms</key>
	<array>
		<string>$BUNDLE_PLATFORM</string>
	</array>
	<key>CFBundleVersion</key>
	<string>1</string>
	<key>MinimumOSVersion</key>
	<string>$MIN_IOS_VERSION</string>
</dict>
</plist>
EOF

cat > "$FW/Headers/$FW_NAME.h" <<EOF
// $FW_NAME: the vproxy vfdposix native library (GeneralPosix + libae), for
// the embedded iOS JVM. Loaded by the JVM with System.loadLibrary; sources:
// base/src/main/c/io_vproxy_vfd_posix_GeneralPosix.c and
// base/src/main/c-generated
EOF

cat > "$FW/Modules/module.modulemap" <<EOF
framework module $FW_NAME {
    umbrella header "$FW_NAME.h"
    export *
}
EOF

# The iOS SDK does not ship a few macOS-only headers referenced under
# __APPLE__ (utun kernel control + libproc). Generate faithful shims with
# the definitions copied from the macOS SDK so the unmodified sources
# compile; the corresponding libSystem symbols do exist on iOS.
SHIM_DIR="`mktemp -d`"
trap 'rm -rf "$SHIM_DIR"' EXIT
mkdir -p "$SHIM_DIR/sys" "$SHIM_DIR/net"

cat > "$SHIM_DIR/sys/sys_domain.h" <<EOF
// iOS build shim (macOS-only header)
#ifndef _SYS_SYS_DOMAIN_H
#define _SYS_SYS_DOMAIN_H
// PF_SYSTEM (32) and AF_SYSTEM come from <sys/socket.h>, which the iOS SDK
// does ship.
#define SYSPROTO_CONTROL 2 /* kernel control protocol */
#define AF_SYS_CONTROL   2 /* corresponding sub address type */
#endif
EOF

cat > "$SHIM_DIR/sys/kern_control.h" <<EOF
// iOS build shim (macOS-only header)
#ifndef _SYS_KERN_CONTROL_H
#define _SYS_KERN_CONTROL_H
#include <sys/ioccom.h>
#include <sys/types.h>
#define MAX_KCTL_NAME 96
struct ctl_info {
    u_int32_t ctl_id;              /* Kernel Controller ID */
    char      ctl_name[MAX_KCTL_NAME]; /* Kernel Controller Name (a C string) */
};
#define CTLIOCGINFO _IOWR('N', 3, struct ctl_info) /* get id from name */
struct sockaddr_ctl {
    u_char    sc_len;     /* depends on size of bundle ID string */
    u_char    sc_family;  /* AF_SYSTEM */
    u_int16_t ss_sysaddr; /* AF_SYS_CONTROL */
    u_int32_t sc_id;      /* Controller unique identifier */
    u_int32_t sc_unit;    /* Developer private unit number */
    u_int32_t sc_reserved[5];
};
#endif
EOF

cat > "$SHIM_DIR/net/if_utun.h" <<EOF
// iOS build shim (macOS-only header)
#ifndef _NET_IF_UTUN_H
#define _NET_IF_UTUN_H
#define UTUN_CONTROL_NAME "com.apple.net.utun_control"
#define UTUN_OPT_IFNAME   2
#endif
EOF

cat > "$SHIM_DIR/libproc.h" <<EOF
// iOS build shim (macOS-only header): only what zmalloc.c uses. The
// proc_pidinfo symbol is exported by libSystem on iOS.
#ifndef _LIBPROC_H_
#define _LIBPROC_H_
#include <stdint.h>
#include <sys/types.h>
struct proc_regioninfo {
    uint32_t pri_protection;
    uint32_t pri_max_protection;
    uint32_t pri_inheritance;
    uint32_t pri_flags;
    uint64_t pri_offset;
    uint32_t pri_behavior;
    uint32_t pri_user_wired_count;
    uint32_t pri_user_tag;
    uint32_t pri_pages_resident;
    uint32_t pri_pages_shared_now_private;
    uint32_t pri_pages_swapped_out;
    uint32_t pri_pages_dirtied;
    uint32_t pri_ref_count;
    uint32_t pri_shadow_depth;
    uint32_t pri_share_mode;
    uint32_t pri_private_pages_resident;
    uint32_t pri_shared_pages_resident;
    uint32_t pri_obj_id;
    uint32_t pri_depth;
    uint64_t pri_address;
    uint64_t pri_size;
};
#define PROC_PIDREGIONINFO      7
#define PROC_PIDREGIONINFO_SIZE (sizeof(struct proc_regioninfo))
int proc_pidinfo(int pid, int flavor, uint64_t arg, void *buffer, int buffersize);
#endif
EOF

xcrun clang -std=gnu99 -O2 \
    ${GCC_OPTS:-} \
    --target="$TRIPLE" \
    -isysroot "$IOS_SDK" \
    -dynamiclib -fPIC -Werror \
    -I "$SHIM_DIR" \
    -I "$LIBAE" \
    -I "$GENERATED_PATH" \
    -L "$FW_PNI" -lpni \
    -install_name "@rpath/$FW/$EXEC" \
    io_vproxy_vfd_posix_GeneralPosix.c \
    $LIBAE/ae.c $LIBAE/anet.c $LIBAE/zmalloc.c $LIBAE/monotonic.c \
    -o "$FW/$EXEC"

# the exported JNI surface must be present
nm -gU "$FW/$EXEC" | grep -q "Java_io_vproxy_vfd_posix_PosixNative_aeCreateEventLoop" || {
    echo "symbol check failed: JNI entry points are not exported from $FW/$EXEC"
    exit 1
}

# ad-hoc signature so the binary is directly loadable; Xcode re-signs when
# the framework is embedded into an app
codesign -s - --force "$FW" > /dev/null 2>&1 \
    || echo "warning: failed ad-hoc signing, Xcode will sign it when embedding"

lipo -info "$FW/$EXEC"

echo "OK: `pwd`/$FW"
