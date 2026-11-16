import type { Infraction, SanctionKind, SanctionStage } from '../../shared/api/domain.service';

export const INFRACTION_LABEL: Record<Infraction, string> = {
  PROHIBITED_GEAR: 'Uso de artes o métodos de pesca prohibidos',
  FISHING_WITHOUT_PERMIT: 'Pesca sin permiso vigente',
  PROTECTED_AREA: 'Pesca en área o periodo prohibido (veda o AMP)',
  UNDERSIZED_CATCH: 'Captura de ejemplares por debajo de la talla mínima',
  UNAUTHORIZED_GEAR: 'Uso de artes no autorizadas en el permiso',
  UNREGISTERED_VESSEL: 'Embarcación no habilitada para la pesca',
  OTHER: 'Otra infracción a la normatividad pesquera',
};

/** AC-23.2 · Orden de las etapas para el indicador de avance. */
export const STAGES: { key: SanctionStage; label: string; hint: string }[] = [
  { key: 'OPENED', label: 'Apertura', hint: 'Infracción tipificada y notificación' },
  { key: 'DEFENSE', label: 'Descargos', hint: '15 días hábiles' },
  { key: 'EVIDENCE', label: 'Pruebas', hint: '30 días hábiles' },
  { key: 'DECISION', label: 'Decisión', hint: 'Recurso: 10 días hábiles' },
  { key: 'APPEAL', label: 'Recurso', hint: 'Si el investigado recurre' },
  { key: 'CLOSED', label: 'Cerrado', hint: '' },
];

export const SANCTION_LABEL: Record<SanctionKind, string> = {
  WARNING: 'Amonestación',
  FINE: 'Multa (SMMLV)',
  CONFISCATION: 'Decomiso',
  PERMIT_SUSPENSION: 'Suspensión del permiso',
  DISMISSED: 'Archivo sin sanción',
};
