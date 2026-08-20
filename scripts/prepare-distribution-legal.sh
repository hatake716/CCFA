#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LEGAL="$ROOT/app/src/main/assets/legal"
LICENSES="$LEGAL/licenses"
SOURCES="$LEGAL/sources"
RECIPES="$SOURCES/termux-build-recipes"
TERMUX_PACKAGES_COMMIT="5dac893779dc6da03dac1d797aa4bd03a1cc0494"
mkdir -p "$LICENSES" "$SOURCES" "$RECIPES"

fetch() {
  local url="$1" out="$2"
  echo "Fetching $url"
  curl -fL --retry 3 --retry-delay 2 "$url" -o "$out"
  test -s "$out"
}

verify_sha256() {
  local expected="$1" file="$2"
  echo "$expected  $file" | sha256sum -c -
}

fetch "https://www.apache.org/licenses/LICENSE-2.0.txt" "$LICENSES/APACHE-2.0.txt"
fetch "https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt" "$LICENSES/GPL-2.0.txt"
fetch "https://www.gnu.org/licenses/gpl-3.0.txt" "$LICENSES/GPL-3.0.txt"
fetch "https://www.gnu.org/licenses/lgpl-3.0.txt" "$LICENSES/LGPL-3.0.txt"
fetch "https://raw.githubusercontent.com/termux/libandroid-shmem/v0.7/LICENSE" "$LICENSES/BSD-3-Clause-libandroid-shmem.txt"
fetch "https://raw.githubusercontent.com/termux/termux-app/v0.118.0/LICENSE.md" "$LICENSES/TERMUX-TERMINAL-LICENSE.md"
fetch "https://raw.githubusercontent.com/apache/commons-compress/rel/commons-compress-1.27.1/NOTICE.txt" "$LICENSES/COMMONS-COMPRESS-NOTICE.txt"
fetch "https://raw.githubusercontent.com/apache/commons-codec/rel/commons-codec-1.17.1/NOTICE.txt" "$LICENSES/COMMONS-CODEC-NOTICE.txt"
fetch "https://raw.githubusercontent.com/apache/commons-io/rel/commons-io-2.16.1/NOTICE.txt" "$LICENSES/COMMONS-IO-NOTICE.txt"
fetch "https://raw.githubusercontent.com/apache/commons-lang/rel/commons-lang-3.16.0/NOTICE.txt" "$LICENSES/COMMONS-LANG3-NOTICE.txt"

fetch "https://github.com/termux/proot/archive/v5.1.107.91.zip" "$SOURCES/proot-v5.1.107.91.zip"
verify_sha256 "a7bc2fab34bf9a39073e8291f08a662e848c61a67494e59f5f84f5ca10690128" "$SOURCES/proot-v5.1.107.91.zip"

fetch "https://github.com/termux/libandroid-shmem/archive/refs/tags/v0.7.tar.gz" "$SOURCES/libandroid-shmem-v0.7.tar.gz.source"
verify_sha256 "1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867" "$SOURCES/libandroid-shmem-v0.7.tar.gz.source"

fetch "https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz" "$SOURCES/talloc-2.4.3.tar.gz.source"
verify_sha256 "dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd" "$SOURCES/talloc-2.4.3.tar.gz.source"

BASE="https://raw.githubusercontent.com/termux/termux-packages/$TERMUX_PACKAGES_COMMIT/packages"
fetch "$BASE/proot/build.sh" "$RECIPES/proot-build.sh"
fetch "$BASE/libandroid-shmem/build.sh" "$RECIPES/libandroid-shmem-build.sh"
fetch "$BASE/libtalloc/build.sh" "$RECIPES/libtalloc-build.sh"
cp "$ROOT/scripts/prepare-termux-android-proot.sh" "$SOURCES/ccfa-prepare-termux-android-proot.sh"

cat > "$SOURCES/README.txt" <<EOF
CCFA corresponding-source bundle

This directory is intentionally embedded in distributed CCFA APKs so recipients of the
native GPL/LGPL components receive the corresponding upstream source archives alongside
the object code. The CCFA packaging script used to select, verify and alter ELF metadata
is included as ccfa-prepare-termux-android-proot.sh.

Android AAPT can ignore assets ending in .gz. For that reason these exact gzip archives
are stored with a trailing .source suffix inside the APK:

  libandroid-shmem-v0.7.tar.gz.source  -> rename to libandroid-shmem-v0.7.tar.gz
  talloc-2.4.3.tar.gz.source           -> rename to talloc-2.4.3.tar.gz

Renaming does not alter the bytes; SHA-256 is verified before APK packaging.

Pinned Termux package recipe commit:
$TERMUX_PACKAGES_COMMIT

The application itself is distributed in source form in the CCFA repository under the
Apache License 2.0. PRoot remains a separate subprocess/native executable component and
retains its GPL terms. libtalloc and libandroid-shmem retain their respective licenses.
The terminal-module exception notice and Apache Commons family NOTICE files required by
the runtime dependency graph are stored under ../licenses/.

Do not remove this directory from distribution builds.
EOF

(
  cd "$LEGAL"
  find licenses sources -type f -print0 | sort -z | xargs -0 sha256sum > SOURCE-AND-LICENSE-MANIFEST.sha256
)

echo "Prepared distribution legal/source assets: $LEGAL"
