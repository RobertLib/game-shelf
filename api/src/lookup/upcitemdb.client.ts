import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { Env } from '../config/env.js';
import { LookupUnavailableError } from './lookup-unavailable.error.js';

const TRIAL_URL = 'https://api.upcitemdb.com/prod/trial/lookup';
const PAID_URL = 'https://api.upcitemdb.com/prod/v1/lookup';
const TIMEOUT_MS = 8_000;

/** A product from UPCitemdb: a shop listing, not game data. */
export interface UpcProduct {
  title: string;
  brand: string | null;
  category: string | null;
  description: string | null;
  images: string[];
}

interface UpcItemDbResponse {
  items?: {
    title?: string;
    brand?: string;
    category?: string;
    description?: string;
    images?: string[];
  }[];
}

/** Client of https://www.upcitemdb.com – maps EAN / UPC codes to product listings. */
@Injectable()
export class UpcItemDbClient {
  private readonly userKey: string | undefined;

  constructor(config: ConfigService<Env, true>) {
    this.userKey =
      config.get('UPCITEMDB_USER_KEY', { infer: true }) || undefined;
  }

  /** The product with this code, or `null` when UPCitemdb does not know it. */
  async lookup(barcode: string): Promise<UpcProduct | null> {
    const url = new URL(this.userKey ? PAID_URL : TRIAL_URL);
    url.searchParams.set('upc', barcode);
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (this.userKey) {
      headers.user_key = this.userKey;
      headers.key_type = '3scale';
    }

    let body: UpcItemDbResponse;
    try {
      const response = await fetch(url, {
        headers,
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      // 404 NOT_FOUND, 400 INVALID_UPC
      if (response.status === 404 || response.status === 400) return null;
      if (!response.ok) {
        throw new LookupUnavailableError(
          `UPCitemdb responded with ${response.status}`,
        );
      }
      body = (await response.json()) as UpcItemDbResponse;
    } catch (e) {
      if (e instanceof LookupUnavailableError) throw e;
      throw new LookupUnavailableError(
        `UPCitemdb request failed: ${String(e)}`,
      );
    }

    const item = body.items?.find((i) => i.title?.trim());
    if (!item?.title) return null;
    return {
      title: item.title.trim(),
      brand: item.brand?.trim() || null,
      category: item.category?.trim() || null,
      description: item.description?.trim() || null,
      images: item.images ?? [],
    };
  }
}
