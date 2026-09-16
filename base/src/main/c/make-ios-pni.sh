#!/bin/bash
# Build the PNI runtime as an iOS framework, for a statically linked
# embedded iOS JVM. This is the iOS counterpart of make-pni.sh.
#
# Cross compilation:
#   - SDK: `xcrun --sdk $SDK_NAME --show-sdk-path` (override with IOS_SDK)
#   - target triple: arm64-apple-ios$MIN_IOS_VERSION (+ -simulator for
#     SDK_NAME=iphonesimulator)
#
# The executable is named libpni.dylib inside the framework, mirroring the
# desktop dylib, so the unmodified vproxy jar can load it with
# System.loadLibrary("pni") when java.library.path contains the framework
# directory. libvfdposix.framework (make-ios-general.sh) depends on it
# through @rpath/libpni.framework/libpni.dylib.
#
# Output: libpni.framework/ next to this script.

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

GENERATED_PATH="../c-generated"
if [ "${VPROXY_BUILD_GRAAL_NATIVE_IMAGE:-}" == "true" ]; then
    GENERATED_PATH="${GENERATED_PATH}-graal"
    GCC_OPTS="$GCC_OPTS -DPNI_GRAAL=1"
fi

if [[ ! -f "$GENERATED_PATH/pni.c" ]]; then
    echo "missing $GENERATED_PATH/pni.c, run ./gradlew :base:pniGenerate first"
    exit 1
fi

FW_NAME="libpni"
FW="$FW_NAME.framework"
EXEC="libpni.dylib"

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
// $FW_NAME: the PNI runtime, for the embedded iOS JVM. Loaded by the JVM
// with System.loadLibrary; sources: base/src/main/c-generated/pni.c
EOF

cat > "$FW/Modules/module.modulemap" <<EOF
framework module $FW_NAME {
    umbrella header "$FW_NAME.h"
    export *
}
EOF

xcrun clang -std=gnu99 -O2 \
    ${GCC_OPTS:-} \
    --target="$TRIPLE" \
    -isysroot "$IOS_SDK" \
    -dynamiclib -fPIC -Werror \
    -I "$GENERATED_PATH" \
    -install_name "@rpath/$FW/$EXEC" \
    "$GENERATED_PATH/pni.c" \
    -o "$FW/$EXEC"

# the PNI entry points must be exported
nm -gU "$FW/$EXEC" | grep -q "GetPNIFuncInvokeFunc" || {
    echo "symbol check failed: PNI entry points are not exported from $FW/$EXEC"
    exit 1
}

# ad-hoc signature so the binary is directly loadable; Xcode re-signs when
# the framework is embedded into an app
codesign -s - --force "$FW" > /dev/null 2>&1 \
    || echo "warning: failed ad-hoc signing, Xcode will sign it when embedding"

lipo -info "$FW/$EXEC"

echo "OK: `pwd`/$FW"
