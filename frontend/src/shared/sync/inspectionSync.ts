import axios from 'axios';
import { domainService } from '../api/domain.service';
import { toAppError } from '../errors/AppError';
import { enqueueInspection, queuedInspections, removeQueuedInspection } from '../storage/inspectionOutbox';

let running: Promise<number> | null = null;

/**
 * HU-22 · AC-22.4 · D5: envía las actas firmadas sin señal. Devuelve cuántas se registraron.
 *
 * <p>El servidor responde 201 si el acta es nueva y 200 si ya había llegado (reenvío): en los dos
 * casos se quita de la cola, así que nunca hay pérdidas ni duplicados. Si el servidor la rechaza por
 * datos inválidos queda marcada con el motivo; si el fallo es de red, se reintenta al reconectar.
 */
export function flushInspectionOutbox(): Promise<number> {
  if (running) return running;
  running = (async () => {
    let sent = 0;
    for (const queued of (await queuedInspections()).filter((item) => !item.rejection)) {
      try {
        await domainService.submitInspection({ ...queued.payload, savedOffline: true }, queued.photos);
        await removeQueuedInspection(queued.clientId);
        sent += 1;
      } catch (error) {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined;
        if (status && status >= 400 && status < 500 && status !== 401 && status !== 429) {
          await enqueueInspection({ ...queued, rejection: toAppError(error).message });
        } else {
          // Sin red, sesión vencida o servidor caído: se reintenta en la próxima reconexión.
          break;
        }
      }
    }
    return sent;
  })().finally(() => {
    running = null;
  });
  return running;
}
