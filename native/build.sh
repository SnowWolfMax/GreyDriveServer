#!/usr/bin/env bash
# Builds the optional native acceleration library and copies it into the jar resources.
# Requires: Rust toolchain. For the Windows DLL also: rustup target add x86_64-pc-windows-gnu
# and a mingw-w64 linker (x86_64-w64-mingw32-gcc).
set -euo pipefail

cd "$(dirname "$0")/greydrive-native"

cargo build --release
mkdir -p ../../src/main/resources/native/linux-x86_64
cp target/release/libgreydrive_native.so ../../src/main/resources/native/linux-x86_64/

if rustup target list --installed 2>/dev/null | grep -q '^x86_64-pc-windows-gnu$'; then
  cargo build --release --target x86_64-pc-windows-gnu
  mkdir -p ../../src/main/resources/native/windows-x86_64
  cp target/x86_64-pc-windows-gnu/release/greydrive_native.dll ../../src/main/resources/native/windows-x86_64/
else
  echo "warning: x86_64-pc-windows-gnu target not installed; skipping Windows DLL" >&2
fi

echo "native libraries copied to src/main/resources/native/"
