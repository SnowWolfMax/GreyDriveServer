//! Optional native acceleration for GreyDrive.
//!
//! Everything here is a pure function over file paths, so the JVM never has to
//! copy large buffers across the JNI boundary. Any error or panic is reported
//! as `false`, and the Java layer falls back to Thumbnailator.

use std::fs::File;
use std::io::{BufWriter, Write};
use std::panic;

use image::codecs::jpeg::JpegEncoder;
use image::{ImageReader, Limits};
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

/// `com.greydrive.nativebridge.NativeImage.thumbnail(String, String, int)`
#[no_mangle]
pub extern "system" fn Java_com_greydrive_nativebridge_NativeImage_thumbnail(
    mut env: JNIEnv,
    _class: JClass,
    source: JString,
    target: JString,
    edge: jint,
) -> jboolean {
    let source: String = match env.get_string(&source) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    let target: String = match env.get_string(&target) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    // A panic must never unwind into the JVM.
    let result = panic::catch_unwind(|| make_thumbnail(&source, &target, edge));
    match result {
        Ok(Ok(())) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

fn make_thumbnail(source: &str, target: &str, edge: jint) -> Result<(), Box<dyn std::error::Error>> {
    let edge = edge.clamp(64, 2048) as u32;

    // Bound untrusted input: refuse decompression bombs instead of exhausting the server.
    let mut limits = Limits::default();
    limits.max_image_width = Some(16_384);
    limits.max_image_height = Some(16_384);
    limits.max_alloc = Some(256 * 1024 * 1024);

    let mut reader = ImageReader::open(source)?.with_guessed_format()?;
    reader.limits(limits);
    let image = reader.decode()?;

    // Keep the aspect ratio and fit inside edge x edge. `thumbnail` uses a fast
    // box filter, which is the right trade-off for cached UI previews.
    let thumbnail = image.thumbnail(edge, edge).to_rgb8();
    let (width, height) = thumbnail.dimensions();

    let file = File::create(target)?;
    let mut writer = BufWriter::new(file);
    let mut encoder = JpegEncoder::new_with_quality(&mut writer, 85);
    encoder.encode(
        thumbnail.as_raw(),
        width,
        height,
        image::ExtendedColorType::Rgb8,
    )?;
    writer.flush()?;
    Ok(())
}
