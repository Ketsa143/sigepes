import axios from 'axios';
import { useAuthStore } from '../../modules/auth/store/authStore';
import { reportNetworkFailure, reportNetworkSuccess } from '../hooks/useOnlineStatus';

export const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT_MS ?? 15000),
  headers: { 'Content-Type': 'application/json' },
});

httpClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/**
 * Cliente sin token, para endpoints públicos que no deben saber quién llama: la denuncia anónima
 * (RN-20) se envía por aquí aunque haya sesión iniciada, de modo que el servidor nunca ve la cuenta.
 */
export const publicClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT_MS ?? 15000),
});

/**
 * Alimenta el indicador "En línea / Sin conexión". Un fallo sin respuesta (que no sea una
 * cancelación) indica que no hay conexión. Una respuesta de error sí prueba que el servidor
 * respondió. Las respuestas correctas no cuentan: el service worker puede servir lecturas desde su
 * caché sin red, así que la vuelta a "en línea" la confirma el sondeo de useOnlineStatus.
 */
function trackConnectivity(error: unknown) {
  if (axios.isAxiosError(error)) {
    if (error.response) reportNetworkSuccess();
    else if (error.code !== 'ERR_CANCELED') reportNetworkFailure();
  }
}

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    trackConnectivity(error);
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      useAuthStore.getState().logout();
    }
    return Promise.reject(error);
  },
);

publicClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    trackConnectivity(error);
    return Promise.reject(error);
  },
);
