package com.greydrive.nativebridge;

import java.nio.file.Path;

/** Optional native image thumbnail encoder. Returns {@code false} when unavailable. */
public final class NativeImage {

    private NativeImage() {
    }

    public static boolean tryThumbnail(Path source, Path target, int edge) {
        if (!NativeLibrary.isAvailable()) {
            return false;
        }
        try {
            return thumbnail(source.toString(), target.toString(), edge);
        } catch (Throwable error) {
            NativeLibrary.disable(error);
            return false;
        }
    }

    private static native boolean thumbnail(String source, String target, int edge);
}
