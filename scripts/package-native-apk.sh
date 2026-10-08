#!/usr/bin/env bash
# Replace only the arm64 mapscore native library in the stock debug APK.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
tools="$sdk/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
native="${1:?Usage: package-native-apk.sh native-libmapscore.so [output.apk]}"
output="${2:-$root/app/build/outputs/apk/debug/app-patched.apk}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 - "$root/app/build/outputs/apk/debug/app-debug.apk" "$native" "$work/unsigned.apk" <<'PYTHON'
import pathlib, sys, zipfile
source, native, output = sys.argv[1:]
with zipfile.ZipFile(source) as original, zipfile.ZipFile(output, 'w') as patched:
    for info in original.infolist():
        if info.filename.startswith('META-INF/') and info.filename.endswith(('.RSA', '.SF', '.MF')):
            continue
        data = pathlib.Path(native).read_bytes() if info.filename == 'lib/arm64-v8a/libmapscore.so' else original.read(info.filename)
        patched.writestr(info, data)
PYTHON
"$tools/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"
"$tools/apksigner" sign --ks "${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}" \
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --out "$output" "$work/aligned.apk"
"$tools/apksigner" verify "$output"
echo "$output"
