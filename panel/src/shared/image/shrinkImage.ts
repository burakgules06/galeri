// Fotografi yuklemeden once tarayicida kucultur: uzun kenar en fazla 1600 px, JPEG. Telefondan cekilen 5-10 MB'lik
// fotograflar ~300-600 KB'a iner (yukleme hizli, sunucu siniri asilmaz). iPhone'un HEIC'i tarayici aciyorsa o da cevrilir.
// Tarayici acamazsa dosya oldugu gibi gider; sunucu yine kontrol eder.
const MAX_SIDE = 1600;
const MAX_BYTES = 900 * 1024;

export async function shrinkImage(file: File): Promise<Blob> {
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return file;
  }
  const ratio = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bitmap.width * ratio));
  canvas.height = Math.max(1, Math.round(bitmap.height * ratio));
  const ctx = canvas.getContext("2d");
  if (!ctx) return file;
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  for (const quality of [0.85, 0.75, 0.65, 0.55]) {
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (blob && (blob.size <= MAX_BYTES || quality === 0.55)) return blob;
  }
  return file;
}
