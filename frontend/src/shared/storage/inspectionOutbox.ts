import type { InspectionInput } from '../api/domain.service';

/**
 * HU-22 · AC-22.4 · D5: actas firmadas en alta mar sin señal.
 *
 * <p>Van en IndexedDB (las fotos son Blob) con el `clientId` que el teléfono asignó al crear el acta.
 * El servidor es idempotente por ese identificador: si el envío se repite, no se duplica.
 */
export interface QueuedInspection {
  clientId: string;
  payload: InspectionInput;
  photos: Blob[];
  /** Para mostrar la cola sin abrir el acta. */
  vesselName: string;
  queuedAt: string;
  /** Motivo por el que el servidor la rechazó (datos inválidos); queda a la vista. */
  rejection?: string;
}

const DB_NAME = 'sigepes-inspections';
const STORE = 'outbox';
export const INSPECTION_OUTBOX_EVENT = 'sigepes-inspection-outbox';

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB no disponible'));
      return;
    }
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'clientId' });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error('No fue posible abrir IndexedDB'));
  });
}

async function withStore<T>(mode: IDBTransactionMode, run: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb();
  try {
    return await new Promise<T>((resolve, reject) => {
      const transaction = db.transaction(STORE, mode);
      const request = run(transaction.objectStore(STORE));
      transaction.oncomplete = () => resolve(request.result);
      transaction.onerror = () => reject(transaction.error ?? new Error('Error de IndexedDB'));
    });
  } finally {
    db.close();
  }
}

function notify() {
  window.dispatchEvent(new Event(INSPECTION_OUTBOX_EVENT));
}

export async function enqueueInspection(inspection: QueuedInspection): Promise<void> {
  await withStore('readwrite', (store) => store.put(inspection));
  notify();
}

export async function queuedInspections(): Promise<QueuedInspection[]> {
  try {
    return await withStore<QueuedInspection[]>('readonly', (store) => store.getAll() as IDBRequest<QueuedInspection[]>);
  } catch {
    return [];
  }
}

export async function removeQueuedInspection(clientId: string): Promise<void> {
  await withStore('readwrite', (store) => store.delete(clientId));
  notify();
}
