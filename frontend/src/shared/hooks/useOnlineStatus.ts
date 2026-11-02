import { useSyncExternalStore } from 'react';

/**
 * Estado de conexión compartido por toda la app.
 *
 * `navigator.onLine` y los eventos `online`/`offline` solo cambian cuando el equipo pierde la red
 * (Wi-Fi apagado, cable desconectado). Con el Wi-Fi conectado pero sin Internet, o con la API
 * caída, siguen diciendo "en línea". Por eso, además de esos eventos:
 * - se sondea la API cada {@link PROBE_INTERVAL_MS} (y al volver a la pestaña o recuperar la red);
 * - el cliente HTTP avisa con {@link reportNetworkFailure} / {@link reportNetworkSuccess} en cuanto
 *   una petición falla por red o recibe respuesta.
 */
const PROBE_INTERVAL_MS = 15_000;
const PROBE_TIMEOUT_MS = 5_000;

let online = typeof navigator === 'undefined' ? true : navigator.onLine;
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | null = null;
let probing = false;

function setOnline(next: boolean) {
  if (next === online) return;
  online = next;
  listeners.forEach((listener) => listener());
}

/** El endpoint de salud vive en la raíz del servidor de la API, fuera de /api/v1. */
function probeUrl(): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';
  return new URL('/actuator/health', new URL(apiBase, window.location.origin)).toString();
}

/**
 * Comprueba que la API responda de verdad: solo un 200 del health check cuenta como conexión. Un
 * 502 de la plataforma (API caída) o un 503 (base de datos caída) cuentan como sin servicio.
 * El health check está fuera de /api, así que el service worker nunca lo responde desde caché.
 */
export async function probeConnection(): Promise<void> {
  if (!navigator.onLine) {
    setOnline(false);
    return;
  }
  if (probing) return;
  probing = true;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), PROBE_TIMEOUT_MS);
  try {
    const response = await fetch(probeUrl(), { method: 'GET', cache: 'no-store', signal: controller.signal });
    setOnline(response.ok);
  } catch {
    setOnline(false);
  } finally {
    clearTimeout(timeout);
    probing = false;
  }
}

/** Una petición a la API falló sin respuesta (red caída, tiempo agotado). */
export function reportNetworkFailure() {
  setOnline(false);
}

/** Una petición a la API recibió respuesta: hay conexión. */
export function reportNetworkSuccess() {
  setOnline(true);
}

const handleOffline = () => setOnline(false);
const handleOnline = () => void probeConnection();
const handleVisibility = () => {
  if (document.visibilityState === 'visible') void probeConnection();
};

function start() {
  window.addEventListener('offline', handleOffline);
  window.addEventListener('online', handleOnline);
  document.addEventListener('visibilitychange', handleVisibility);
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') void probeConnection();
  }, PROBE_INTERVAL_MS);
  void probeConnection();
}

function stop() {
  window.removeEventListener('offline', handleOffline);
  window.removeEventListener('online', handleOnline);
  document.removeEventListener('visibilitychange', handleVisibility);
  if (timer) clearInterval(timer);
  timer = null;
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) start();
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) stop();
  };
}

/** Estado de conexión reactivo; todos los componentes comparten un único sondeo. */
export function useOnlineStatus(): boolean {
  return useSyncExternalStore(subscribe, () => online, () => true);
}
