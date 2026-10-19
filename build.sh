#!/usr/bin/env bash
# Builds the APK without Gradle (aapt2, javac, d8, zipalign, apksigner). Windows + Git Bash.
# Settings via environment variables (defaults in brackets):
#   JDK_DIR       JDK 17                      [first jdk-17* in ~/tools]
#   ANDROID_SDK   Android SDK                 [%LOCALAPPDATA%/Android/Sdk]
#   BUILD_TOOLS   build-tools version         [36.1.0]
#   PLATFORM      platform for android.jar    [android-36.1]
#   KEYSTORE      signing key (.jks)          [testkey.jks, created automatically]
#   KEY_PASS_FILE file containing the password[testkey.pass]
#   KEY_ALIAS     key alias                   [test]
# Intermediate files go to OUT (outside Dropbox, file locks); result: ppx620start.apk
set -euo pipefail
cd "$(dirname "$0")"
JDK="${JDK_DIR:-$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1)}"
SDK="${ANDROID_SDK:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/${BUILD_TOOLS:-36.1.0}"
JAR="$SDK/platforms/${PLATFORM:-android-36.1}/android.jar"
OUT="${OUT:-$HOME/tools/build/ppx620start}"
KS="${KEYSTORE:-testkey.jks}"
KP="${KEY_PASS_FILE:-testkey.pass}"
ALIAS="${KEY_ALIAS:-test}"
export JAVA_HOME="$(cygpath -w "$JDK")"
export PATH="$JDK/bin:$PATH"
w() { cygpath -w "$1"; }

rm -rf "$OUT" && mkdir -p "$OUT/res" "$OUT/classes" "$OUT/dex" "$OUT/gen"
"$BT/aapt2.exe" compile --dir res -o "$(w "$OUT/res")"
"$BT/aapt2.exe" link -I "$(w "$JAR")" --manifest AndroidManifest.xml -A assets -0 jpg -o "$(w "$OUT/base.apk")" $(for f in "$OUT"/res/*.flat; do w "$f"; done) --min-sdk-version 28 --target-sdk-version 28 --java "$(w "$OUT/gen")"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$(w "$JAR")" -classpath "$(w "$BT/core-lambda-stubs.jar")" -Xlint:-options -d "$(w "$OUT/classes")" $(find src "$OUT/gen" -name '*.java')
"$BT/d8.bat" --release --min-api 28 --lib "$(w "$JAR")" --output "$(w "$OUT/dex")" $(find "$OUT/classes" -name '*.class' | while read f; do w "$f"; done)
python - "$(w "$OUT")" <<'EOF'
import zipfile, shutil, sys, os
o = sys.argv[1]
shutil.copy(os.path.join(o, 'base.apk'), os.path.join(o, 'unsigned.apk'))
with zipfile.ZipFile(os.path.join(o, 'unsigned.apk'), 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(o, 'dex', 'classes.dex'), 'classes.dex')
EOF
"$BT/zipalign.exe" -f -p 4 "$(w "$OUT/unsigned.apk")" "$(w "$OUT/aligned.apk")"
if [ ! -f "$KS" ]; then
  [ "$KS" = testkey.jks ] || { echo "Keystore $KS not found" >&2; exit 1; }
  PW=$(python -c "import secrets;print(secrets.token_hex(12))")
  echo "$PW" > testkey.pass
  keytool -genkeypair -keystore testkey.jks -storepass "$PW" -keypass "$PW" -alias test -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=PPX620 Start Test" >/dev/null
fi
"$BT/apksigner.bat" sign --ks "$(w "$KS")" --ks-key-alias "$ALIAS" --ks-pass "file:$(w "$KP")" --out "$(w "$OUT/ppx620start.apk")" "$(w "$OUT/aligned.apk")"
"$BT/apksigner.bat" verify "$(w "$OUT/ppx620start.apk")"
cp "$OUT/ppx620start.apk" ppx620start.apk
ls -la ppx620start.apk
