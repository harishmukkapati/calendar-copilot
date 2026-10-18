const MAX_DIMENSION = 1600;
const SMALL_ENOUGH_BYTES = 600 * 1024;

/**
 * Shrinks large photos/screenshots in the browser before upload: a 6 MB phone photo
 * becomes ~300 KB, which speeds up both the upload and Gemini's processing.
 * Text stays readable at 1600 px. Falls back to the original file on any problem.
 */
export async function downscaleImage(file: File): Promise<File> {
  if (!/^image\/(png|jpeg)$/.test(file.type) || typeof createImageBitmap !== 'function') {
    return file;
  }
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_DIMENSION / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size <= SMALL_ENOUGH_BYTES) {
      bitmap.close();
      return file;
    }
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = new OffscreenCanvas(width, height);
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return file;
    }
    ctx.fillStyle = '#fff'; // transparent PNG areas would turn black in JPEG
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await canvas.convertToBlob({ type: 'image/jpeg', quality: 0.85 });
    if (blob.size >= file.size) {
      return file;
    }
    const name = file.name.replace(/\.(png|jpe?g)$/i, '') + '.jpg';
    return new File([blob], name, { type: 'image/jpeg' });
  } catch {
    return file;
  }
}
