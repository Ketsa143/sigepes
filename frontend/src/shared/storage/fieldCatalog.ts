import type { Gear, Vessel } from '../api/domain.service';

/**
 * HU-22 · Catálogos que el inspector necesita en alta mar: embarcaciones y artes. Se guardan cada vez
 * que se cargan con señal y se usan cuando no la hay (las especies ya tienen su propio almacén).
 */
const VESSELS_KEY = 'sigepes-field-vessels';
const GEARS_KEY = 'sigepes-field-gears';

interface Cached<T> {
  savedAt: string;
  items: T[];
}

function save<T>(key: string, items: T[]): void {
  try {
    localStorage.setItem(key, JSON.stringify({ savedAt: new Date().toISOString(), items } satisfies Cached<T>));
  } catch {
    // Sin espacio o sin almacenamiento: se sigue trabajando con lo que haya.
  }
}

function load<T>(key: string): Cached<T> | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as Cached<T>) : null;
  } catch {
    return null;
  }
}

export const cacheFieldVessels = (vessels: Vessel[]) => save(VESSELS_KEY, vessels);
export const cachedFieldVessels = () => load<Vessel>(VESSELS_KEY);
export const cacheFieldGears = (gears: Gear[]) => save(GEARS_KEY, gears);
export const cachedFieldGears = () => load<Gear>(GEARS_KEY);
