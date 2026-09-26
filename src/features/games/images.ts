import { decode } from 'base64-arraybuffer';
import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

import { GAME_IMAGES_BUCKET, supabase } from '@/lib/supabase';

const FULL_MAX_EDGE = 1600;
const THUMB_MAX_EDGE = 480;
const SIGNED_URL_TTL_SECONDS = 60 * 60 * 24;

type SourceImage = { uri: string; width: number; height: number };

async function renderJpeg(source: SourceImage, maxEdge: number) {
  const context = ImageManipulator.manipulate(source.uri);
  if (!source.width || !source.height) {
    context.resize({ width: maxEdge });
  } else if (Math.max(source.width, source.height) > maxEdge) {
    context.resize(source.width >= source.height ? { width: maxEdge } : { height: maxEdge });
  }
  const image = await context.renderAsync();
  try {
    const result = await image.saveAsync({ format: SaveFormat.JPEG, compress: 0.82, base64: true });
    if (!result.base64) throw new Error('Could not encode the image.');
    return { base64: result.base64, width: result.width, height: result.height };
  } finally {
    image.release();
    context.release();
  }
}

async function uploadJpeg(path: string, base64: string) {
  const { error } = await supabase.storage.from(GAME_IMAGES_BUCKET).upload(path, decode(base64), {
    contentType: 'image/jpeg',
    // Paths are never reused, so the files can be cached forever.
    cacheControl: '31536000',
    upsert: false,
  });
  if (error) throw error;
}

function randomId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Resizes a picked photo into a full-size image and a small thumbnail and
 * uploads both into the user's folder: "<userId>/<gameId>/<id>.jpg".
 */
export async function uploadGameImage(userId: string, gameId: string, source: SourceImage) {
  const id = randomId();
  const storagePath = `${userId}/${gameId}/${id}.jpg`;
  const thumbPath = `${userId}/${gameId}/${id}_thumb.jpg`;

  const full = await renderJpeg(source, FULL_MAX_EDGE);
  const thumb = await renderJpeg(source, THUMB_MAX_EDGE);

  await uploadJpeg(storagePath, full.base64);
  try {
    await uploadJpeg(thumbPath, thumb.base64);
  } catch (error) {
    await removeStorageFiles([storagePath]);
    throw error;
  }

  return { storagePath, thumbPath, width: full.width, height: full.height };
}

export async function removeStorageFiles(paths: string[]) {
  if (paths.length === 0) return;
  const { error } = await supabase.storage.from(GAME_IMAGES_BUCKET).remove(paths);
  // A leftover file is harmless (the bucket is private), so don't fail the
  // user's action over it.
  if (error) console.warn('Could not remove image files', error.message);
}

/** Returns a map of storage path → signed URL. Missing entries mean the file could not be signed. */
export async function signImagePaths(paths: string[]) {
  const urls = new Map<string, string>();
  const unique = [...new Set(paths)];
  if (unique.length === 0) return urls;

  const { data, error } = await supabase.storage
    .from(GAME_IMAGES_BUCKET)
    .createSignedUrls(unique, SIGNED_URL_TTL_SECONDS);
  if (error) {
    console.warn('Could not load image URLs', error.message);
    return urls;
  }
  for (const entry of data) {
    if (entry.path && entry.signedUrl) urls.set(entry.path, entry.signedUrl);
  }
  return urls;
}
