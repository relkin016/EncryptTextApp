#!/usr/bin/env bash
# Собирает подписанный release APK (R8 + shrinkResources, без x86) и проверяет результат.
# Запускается из корня проекта на сборочной ноде.
# Результат: dist/EncryptText-X.Y.Z.apk + dist/EncryptText-X.Y.Z.apk.sha256
set -euo pipefail
cd "$(dirname "$0")/.."
TOOLS="${TOOLS:-$HOME/tools}"
export JAVA_HOME="$TOOLS/jdk" ANDROID_HOME="$TOOLS/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"
echo "sdk.dir=$ANDROID_HOME" > local.properties

[ -f keystore/keystore.properties ] || { echo "нет keystore/keystore.properties — APK будет неподписанным" >&2; exit 1; }

# Нода: 16 потоков / 32 ГБ — нагружаем полностью.
workers="${GRADLE_WORKERS:-$(nproc)}"
./gradlew --no-daemon --max-workers="$workers" \
  -Dorg.gradle.jvmargs="-Xmx${GRADLE_XMX:-12g} -XX:+UseParallelGC -Dfile.encoding=UTF-8" \
  "${@:-:app:assembleRelease}"

version="$(bash ci/version.sh)"
src=app/build/outputs/apk/release/app-release.apk
mkdir -p dist
apk="dist/EncryptText-$version.apk"
cp "$src" "$apk"

apksigner="$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -1)apksigner"
"$apksigner" verify --print-certs "$apk" | grep -m1 'certificate SHA-256'

listing="$(jar tf "$apk")"
if grep -q '^lib/x86' <<<"$listing"; then
  echo "в release попали x86-библиотеки" >&2; exit 1
fi

(cd dist && sha256sum "$(basename "$apk")" > "$(basename "$apk").sha256")
cat "$apk.sha256"
ls -l "$apk"
