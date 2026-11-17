# greydrive-native

Optional native acceleration for GreyDrive, loaded by `com.greydrive.nativebridge.NativeLibrary`.

## What is accelerated

| Function | Java fallback | Notes |
|---|---|---|
| `thumbnail(src, dst, edge)` | Thumbnailator / ImageIO | Decodes, resizes and JPEG-encodes in Rust |

The function takes file paths (never large byte arrays), so nothing is copied across JNI.
Any error or panic returns `false`; `PreviewService` then falls back to Thumbnailator.

Measured on Linux x86_64, 4000x3000 JPEG -> 320 px preview:

| | time |
|---|---|
| Thumbnailator (Java) | ~267 ms |
| Rust (image + Lanczos3) | ~70 ms |

SHA-256 was benchmarked too and intentionally **not** moved to Rust: Java 21 already uses
SHA-NI intrinsics and was equal or slightly faster than the `sha2` crate.

## Build

```bash
./native/build.sh
```

This produces:

```
src/main/resources/native/linux-x86_64/libgreydrive_native.so
src/main/resources/native/windows-x86_64/greydrive_native.dll
```

Then package the server as usual (`mvn package`). The libraries are loaded from the jar at
startup and extracted to a temp directory keyed by content hash.

## Disable / troubleshooting

```
-Dgreydrive.native.mode=off
```

or set `GREYDRIVE_NATIVE_MODE=off`. The startup log prints either
`Native acceleration enabled: ...` or `Native acceleration not available (...)`.
