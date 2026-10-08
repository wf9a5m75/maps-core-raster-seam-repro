#!/usr/bin/env bash
# No sibling SDK checkout required. Build OMM 4.0.0 with the one-file patch.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
checkout="${1:-$root/build/maps-core}"
ndk="$sdk/ndk/${NDK_VERSION:-27.1.12297006}"
cmake="$sdk/cmake/${CMAKE_VERSION:-3.22.1}/bin"
if [[ ! -d "$checkout/.git" && ! -f "$checkout/.git" ]]; then
    git clone https://github.com/openmobilemaps/maps-core.git "$checkout"
    git -C "$checkout" checkout 82c1fac
fi
git -C "$checkout" submodule update --init --recursive
patch="$root/patches/flat-masked-raster-overlap.patch"
if ! git -C "$checkout" apply --reverse --check "$patch" 2>/dev/null; then
    git -C "$checkout" apply --check "$patch"
    git -C "$checkout" apply "$patch"
fi
native_build="$root/build/native"
"$cmake/cmake" -S "$checkout/android" -B "$native_build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_MAKE_PROGRAM="$cmake/ninja"
"$cmake/cmake" --build "$native_build" --target mapscore -j 8
cp "$native_build/libmapscore.so" "$native_build/libmapscore-stripped.so"
host="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
"$ndk/toolchains/llvm/prebuilt/$host/bin/llvm-strip" --strip-unneeded "$native_build/libmapscore-stripped.so"
"$root/gradlew" -p "$root" :app:assembleDebug
"$root/scripts/package-native-apk.sh" "$native_build/libmapscore-stripped.so"
