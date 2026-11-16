import { httpClient, publicClient } from './httpClient';
import type { Role } from '../../modules/auth/types/auth.types';
import { cacheSpecies, getCachedSpecies } from '../storage/speciesOfflineStore';
import { cacheQuotas, getCachedQuotas } from '../storage/quotaOfflineStore';
import { cacheZones, getCachedZones } from '../storage/zoneOfflineStore';

export interface Vessel {
  id: string;
  name: string;
  registration: string;
  port: string;
  gears: string[];
  gearIds?: string[];
  status: string;
  ownerId: string;
  ownerName?: string;
  type?: string | null;
  hullType: string;
  enginePower: number;
  capacityTons: number;
}

export interface Gear {
  id: string;
  name: string;
  description?: string | null;
  destructive: boolean;
  status: string;
}

export interface Species {
  id: string;
  commonName: string;
  scientificName: string;
  minSizeCm: number;
  measurementMethod: string;
  region: string;
  imageUrl: string;
  status: string;
  underClosure?: boolean;
}

/** AC-06.4: una fila del historial de versiones de una especie. */
export interface SpeciesVersion {
  version: number;
  commonName: string;
  scientificName: string;
  minSizeCm: number;
  measurementMethod: string;
  region: string;
  imageUrl: string;
  status: string;
  changedByLabel: string;
  changedAt: string;
}

export interface Closure {
  id: string;
  speciesId: string;
  species: string;
  zone: string;
  from: string;
  to: string;
  status: string;
  inForce?: boolean;
  reason?: string | null;
  resolutionNumber: string;
  closureType: string;
  notes?: string | null;
}

/** RF-07: cuota de captura con su saldo. */
export interface Quota {
  id: string;
  speciesId: string;
  species: string;
  zone: string;
  from: string;
  to: string;
  totalKg: number;
  consumedKg: number;
  remainingKg: number;
  consumedPercent: number;
  status: 'ACTIVE' | 'CLOSED';
  vesselId?: string | null;
  vessel?: string | null;
  resolutionNumber?: string | null;
  notes?: string | null;
}

/** HU-17: cálculo de RMS en segundo plano. */
export interface RmsRun {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  speciesCount?: number | null;
  durationMs?: number | null;
  error?: string | null;
  createdAt: string;
  finishedAt?: string | null;
}

export type StockStatus = 'UNDER_EXPLOITED' | 'FULLY_EXPLOITED' | 'OVER_EXPLOITED';

/** HU-17: resultado del modelo de Schaefer de una especie (desde la caché del servidor). */
export interface RmsEstimate {
  speciesId: string;
  species: string | null;
  scientificName: string | null;
  yearsCount: number;
  firstYear: number;
  lastYear: number;
  fitted: boolean;
  indicative: boolean;
  r: number | null;
  kKg: number | null;
  q: number | null;
  msyKg: number | null;
  fMsy: number | null;
  biomassRatio: number | null;
  fishingRatio: number | null;
  r2: number | null;
  stockStatus: StockStatus | null;
  note: string | null;
  computedAt: string;
  series: { year: number; catchKg: number; effort: number; cpue: number; source: 'FAENAS' | 'HISTORICO' }[];
  curve: { effort: number; yieldKg: number }[];
}

export interface RmsProposal {
  id: string;
  speciesId: string;
  species: string | null;
  proposedKg: number;
  msyKg: number;
  indicative: boolean;
  zone: string;
  from: string;
  to: string;
  justification: string | null;
  status: 'PROPOSED' | 'APPROVED' | 'REJECTED';
  proposedBy: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
  quotaId: string | null;
  createdAt: string;
  decidedAt: string | null;
}

/** RF-04: zona restringida (AMP o zona en veda) con su polígono [[lat, lon], ...]. */
export interface RestrictedZone {
  id: string;
  name: string;
  zoneType: 'CLOSURE_ZONE' | 'MPA' | 'OTHER';
  polygon: [number, number][];
  resolutionNumber: string;
  description?: string | null;
  active: boolean;
  /** HU-13: AMP al que pertenece la zona de manejo (nulo en zonas de RF-04). */
  mpaId?: string | null;
  mpaZoneKind?: MpaZoneKind | null;
}

/** HU-13: zona de manejo dentro de un Área Marina Protegida. */
export type MpaZoneKind = 'INTANGIBLE' | 'SUSTAINABLE_USE' | 'BUFFER';

export type MpaCategory = 'NATIONAL_PARK' | 'DMI' | 'SANCTUARY' | 'OTHER';

export interface NamedRef {
  id: string;
  name: string;
}

export interface MpaZone {
  id: string;
  name: string;
  kind: MpaZoneKind;
  polygon: [number, number][];
  description?: string | null;
  allowedGears: NamedRef[];
  allowedSpecies: NamedRef[];
}

/** HU-13: Área Marina Protegida con su zonificación (consulta pública). */
export interface MarineProtectedArea {
  id: string;
  name: string;
  category: MpaCategory;
  legalAct: string;
  authority?: string | null;
  description?: string | null;
  zones: MpaZone[];
}

/** AC-04.1: posición GPS capturada en el dispositivo. El id lo genera el cliente (idempotencia). */
export interface PositionInput {
  id: string;
  latitude: number;
  longitude: number;
  accuracyMeters?: number | null;
  recordedAt: string;
}

export interface TripPosition extends PositionInput {
  insideZoneId?: string | null;
  insideZone?: string | null;
}

export interface ZoneEvent {
  id: string;
  tripId: string;
  zoneId: string;
  zone: string;
  zoneType: string;
  resolutionNumber: string;
  ownerName: string;
  vessel: string;
  latitude: number;
  longitude: number;
  occurredAt: string;
  status: 'OPEN' | 'REVIEWED';
}

export type ReportType = 'CLOSURE_FISHING' | 'UNDERSIZED_CATCH' | 'PROHIBITED_GEAR' | 'MPA_INTRUSION' | 'OTHER';
export type ReportStatus = 'RECEIVED' | 'INVESTIGATING' | 'CLOSED_FOUNDED' | 'CLOSED_UNFOUNDED';

export interface ReportInput {
  clientId: string;
  reportType: ReportType;
  description: string;
  occurredOn: string;
  latitude?: number;
  longitude?: number;
  locationText?: string;
  anonymous: boolean;
}

export interface ReportReceipt {
  trackingCode: string;
  status: ReportStatus;
  anonymous: boolean;
  createdAt: string;
}

export interface ReportTracking {
  trackingCode: string;
  reportType: ReportType;
  status: ReportStatus;
  occurredOn: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReportSummary {
  id: string;
  trackingCode: string;
  reportType: ReportType;
  status: ReportStatus;
  description: string;
  occurredOn: string;
  latitude?: number;
  longitude?: number;
  locationText?: string;
  anonymous: boolean;
  photoCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ReportDetail extends Omit<ReportSummary, 'photoCount'> {
  reporterName?: string;
  resolutionNote?: string;
  photoIds: string[];
  history: { fromStatus: ReportStatus; toStatus: ReportStatus; note: string; changedBy: string; changedAt: string }[];
}

export interface PositionBatchResult {
  stored: number;
  duplicates: number;
  intrusions: ZoneEvent[];
}

export type PermitStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED' | 'EXPIRED';

export interface PermitAttachment {
  kind: 'ID_COPY' | 'VESSEL_OWNERSHIP';
  originalFilename: string;
  contentType: string;
}

export interface Permit {
  id: string;
  number: string;
  type: string;
  holder: string;
  issued: string;
  expires: string;
  status: string;
  ownerId: string;
  reason?: string | null;
  verificationCode?: string;
  expiringSoon?: boolean;
  daysToExpire?: number;
  attachments: PermitAttachment[];
}

export interface Capture {
  id: string;
  speciesId: string;
  species: string;
  quantity: number;
  weightKg: number;
  averageSizeCm: number;
  minSizeCmAtCapture?: number;
}

export interface Trip {
  id: string;
  vesselId: string;
  vessel: string;
  zone: string;
  originPort: string;
  date: string;
  status: string;
  nullDeclaration: boolean;
  captures: Capture[];
  ownerId: string;
  ownerName?: string;
  gear?: string | null;
  permitNumber?: string | null;
  departureTime?: string | null;
  arrivalTime?: string | null;
  notes?: string | null;
  landingLatitude?: number | null;
  landingLongitude?: number | null;
  totalWeightKg?: number;
  /** AC-04.6: recorrido capturado sin conexión, se envía al sincronizar. */
  positions?: PositionInput[];
}

export interface AuditEvent {
  id: string;
  timestamp: string;
  occurredAt?: string;
  actor: string;
  actorId?: string | null;
  action: string;
  actionType: 'CREATE' | 'UPDATE' | 'DELETE' | 'SECURITY';
  module?: string;
  entity: string;
  entityId?: string | null;
  detail: string;
  ip?: string | null;
  beforeState?: string | null;
  afterState?: string | null;
}

export interface AuditFilters {
  module?: string;
  actionType?: string;
  actorId?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export interface UserRow {
  id: string;
  fullName: string;
  email: string;
  nationalId: string;
  role: Role;
  status: string;
}

export interface Stat {
  value: number;
  helper: string;
}

export interface Activity {
  id: string;
  title: string;
  status: string;
  date: string;
}

export interface ComplianceRow {
  label: string;
  status: string;
}

export interface DashboardSummary {
  fullName: string;
  alerts: string[];
  vessels: Stat;
  permits: Stat;
  trips: Stat;
  warnings: Stat;
  recent: Activity[];
  compliance: ComplianceRow[];
}

export type OfficialReportKind = 'LANDINGS' | 'QUOTA_COMPLIANCE' | 'PERMITS' | 'ILLEGAL_REPORTS' | 'ZONE_EVENTS';
export type ExportFormat = 'CSV' | 'PDF';

export interface OfficialReportCatalogEntry {
  kind: OfficialReportKind;
  title: string;
  regionFilter: boolean;
  speciesFilter: boolean;
}

export interface OfficialReportPreview {
  table: {
    kind: OfficialReportKind;
    title: string;
    columns: string[];
    rows: string[][];
    filters: string[];
    footnotes: string[];
    generatedBy: string;
    generatedAt: string;
  };
  totalRows: number;
  truncated: boolean;
  /** AC-18.5: el periodo supera un año y la exportación se generará en segundo plano. */
  background: boolean;
}

export interface ExportJob {
  id: string;
  kind: OfficialReportKind;
  title: string;
  format: ExportFormat;
  from: string;
  to: string;
  status: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  rowCount?: number;
  error?: string;
  createdAt: string;
  finishedAt?: string;
}

export type ExportOutcome = { kind: 'file'; blob: Blob; filename: string } | { kind: 'job'; job: ExportJob };

/** Nombre del archivo desde Content-Disposition (filename* UTF-8 si viene). */
function filenameFrom(disposition: string | undefined, fallback: string): string {
  if (!disposition) return fallback;
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : fallback;
}

export type DocType = 'ID_DOCUMENT' | 'VESSEL_CERTIFICATE' | 'PERMIT' | 'RESOLUTION' | 'OTHER';
export type DocEntityType = 'USER' | 'VESSEL' | 'PERMIT' | 'CLOSURE';
export type DocExpiryStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'NO_EXPIRY';

export interface DocumentVersion {
  version: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  expiresOn?: string;
  note?: string;
  uploadedAt: string;
}

export interface DocumentRecord {
  id: string;
  docType: DocType;
  title: string;
  entityType: DocEntityType;
  entityId: string;
  entityLabel: string;
  ownerName: string;
  currentVersion: number;
  expiresOn?: string;
  expiryStatus: DocExpiryStatus;
  updatedAt: string;
  versions: DocumentVersion[];
}

export type Region = 'PACIFICO' | 'CARIBE' | 'OTRA';

export interface IndicatorFilters {
  from?: string;
  to?: string;
  region?: Region | '';
  speciesId?: string;
}

export interface SpeciesLanding {
  speciesId: string;
  speciesName: string;
  kg: number;
  trips: number;
}

export interface IndicatorsResponse {
  filters: { from: string; to: string; region?: Region; speciesId?: string };
  generatedAt: string;
  totals: {
    landedKg: number;
    closedTrips: number;
    excludedOpenTrips: number;
    activeClosures: number;
    openReports: number;
    zoneEvents: number;
  };
  landingsBySpecies: SpeciesLanding[];
  landingsByRegion: { region: Region; kg: number }[];
  quotas: { id: string; speciesName: string; zone: string; totalKg: number; remainingKg: number; consumedPercent: number }[];
  closures: { speciesName: string; zone: string; until: string }[];
  permitsByStatus: { status: string; count: number }[];
  reportsByStatus: { status: string; count: number }[];
  zoneEvents: { zoneName: string; total: number; open: number }[];
}

export interface OwnIndicators {
  filters: { from: string; to: string; region?: Region; speciesId?: string };
  landedKg: number;
  closedTrips: number;
  openTrips: number;
  landingsBySpecies: SpeciesLanding[];
}

export interface LegalCheck {
  key: string;
  label: string;
  passed: boolean;
  detail: string;
}

export interface EligibleCatch {
  catchId: string;
  speciesName: string;
  weightKg: number;
  availableKg: number;
  legal: boolean;
  checks: LegalCheck[];
}

export type PartyType = 'FISHER' | 'BUYER' | 'TRADER' | 'PROCESSOR' | 'EXPORTER';

export interface ChainEvent {
  sequence: number;
  type: 'CREATED' | 'TRANSFER';
  partyType: PartyType;
  partyName?: string;
  partyTaxId?: string;
  location: string;
  occurredOn: string;
  hash?: string;
}

export interface Lot {
  id: string;
  code: string;
  tripId: string;
  speciesName: string;
  scientificName: string;
  weightKg: number;
  landingDate: string;
  zone: string;
  vesselName: string;
  vesselRegistration: string;
  ownerName: string;
  currentHolder?: string;
  integrityOk: boolean;
  legalChecks: LegalCheck[];
  chain: ChainEvent[];
  createdAt: string;
}

/** AC-09.4: lo que ve cualquier comprador al escanear el QR, sin datos personales. */
export interface PublicLot {
  code: string;
  speciesName: string;
  scientificName: string;
  weightKg: number;
  landingDate: string;
  zone: string;
  vesselRegistration: string;
  legal: boolean;
  integrityOk: boolean;
  legalChecks: LegalCheck[];
  chain: ChainEvent[];
}

export type AlertType = 'CLOSURE' | 'QUOTA' | 'PERMIT' | 'ZONE' | 'REPORT' | 'SECURITY' | 'DOCUMENT' | 'EXPORT' | 'ENVIRONMENT' | 'INSPECTION' | 'SANCTION' | 'MARKET';
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface AppAlert {
  id: string;
  type: AlertType;
  severity: AlertSeverity;
  title: string;
  message: string;
  link?: string;
  read: boolean;
  /** RN-22: las críticas no se pueden archivar. */
  archivable: boolean;
  createdAt: string;
}

export interface AlertInbox {
  unread: number;
  alerts: AppAlert[];
}

/** Cuerpo de una faena pendiente de sincronizar (registro offline). */
export interface PendingTripPayload {
  id: string;
  vesselId: string;
  zone: string;
  originPort: string;
  date: string;
  status: string;
  nullDeclaration: boolean;
  captures: { speciesId: string; quantity: number; weightKg: number; averageSizeCm: number }[];
  landingLatitude: number;
  landingLongitude: number;
}

function indicatorParams(filters: IndicatorFilters) {
  return {
    desde: filters.from || undefined,
    hasta: filters.to || undefined,
    region: filters.region || undefined,
    especieId: filters.speciesId || undefined,
  };
}

/** HU-14: lectura ambiental de una zona de pesca. */
export interface EnvReading {
  observedAt: string;
  sstC: number | null;
  chlorophyllMgM3: number | null;
  waveHeightM: number | null;
  /** Diferencia con la temperatura de referencia de la zona (°C). */
  anomalyC: number | null;
  source: string;
}

export interface EnvZone {
  id: string;
  name: string;
  region: 'PACIFICO' | 'CARIBE' | 'INSULAR';
  latitude: number;
  longitude: number;
  sstBaselineC: number | null;
  latest: EnvReading | null;
  /** Horas desde la última lectura guardada (AC-14.5). */
  ageHours: number | null;
  stale: boolean;
  sstAlert: boolean;
  waveAlert: boolean;
  /** AC-14.5: última clorofila guardada si la lectura más reciente no la trae. */
  lastChlorophyll: { value: number; observedAt: string; ageHours: number } | null;
}

export interface EnvSourceStatus {
  name: string;
  available: boolean;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastError: string | null;
}

export interface EnvOverview {
  zones: EnvZone[];
  sources: EnvSourceStatus[];
  lastImportAt: string | null;
  sstThresholdC: number;
  waveThresholdM: number;
}

export interface EnvSeries {
  zone: EnvZone;
  from: string;
  to: string;
  readings: EnvReading[];
  speciesId: string | null;
  species: string | null;
  catches: { date: string; weightKg: number }[];
}

/** HU-22: acta de inspección tal como se arma y firma en el teléfono. */
export interface InspectionInput {
  clientId: string;
  vesselId: string;
  inspectedAt: string;
  latitude?: number | null;
  longitude?: number | null;
  locationText: string;
  catches: { speciesId: string; sizeCm: number; weightKg?: number | null; quantity?: number | null }[];
  gearIds: string[];
  otherFindings: string[];
  observations?: string;
  responsibleName: string;
  responsibleIdNumber?: string;
  /** Firmas dibujadas en pantalla (data URL PNG). */
  inspectorSignature: string;
  responsibleSignature: string;
  savedOffline: boolean;
}

export type FindingCode = 'SIZE_BELOW_MIN' | 'PROHIBITED_GEAR' | 'UNREGISTERED_GEAR' | 'NO_VALID_PERMIT' | 'VESSEL_NOT_ACTIVE' | 'OTHER';

export interface Inspection {
  id: string;
  code: string;
  clientId: string;
  vesselId: string;
  vesselName: string;
  vesselRegistration: string;
  ownerName: string;
  inspectorName: string;
  permitNumber: string | null;
  latitude: number | null;
  longitude: number | null;
  locationText: string;
  inspectedAt: string;
  result: 'COMPLIANT' | 'WITH_FINDINGS';
  observations: string | null;
  responsibleName: string;
  items: {
    kind: 'CATCH' | 'GEAR'; speciesId: string | null; gearId: string | null; label: string;
    sizeCm: number | null; minSizeCm: number | null; weightKg: number | null; quantity: number | null; finding: boolean;
  }[];
  findings: { id: string; code: FindingCode; label: string; description: string; automatic: boolean }[];
  photos: number;
  contentHash: string;
  savedOffline: boolean;
  receivedAt: string;
}

export interface InspectionSummary {
  id: string;
  code: string;
  vesselName: string;
  vesselRegistration: string;
  inspectorName: string;
  locationText: string;
  inspectedAt: string;
  result: Inspection['result'];
  findings: number;
  savedOffline: boolean;
}

export interface PublicInspection {
  code: string;
  vesselName: string;
  vesselRegistration: string;
  inspectedAt: string;
  locationText: string;
  result: Inspection['result'];
  findings: string[];
  integrityOk: boolean;
}

/** HU-23: proceso sancionatorio. */
export type Infraction = 'PROHIBITED_GEAR' | 'FISHING_WITHOUT_PERMIT' | 'PROTECTED_AREA' | 'UNDERSIZED_CATCH'
  | 'UNAUTHORIZED_GEAR' | 'UNREGISTERED_VESSEL' | 'OTHER';
export type SanctionStage = 'OPENED' | 'DEFENSE' | 'EVIDENCE' | 'DECISION' | 'APPEAL' | 'CLOSED';
export type SanctionKind = 'WARNING' | 'FINE' | 'CONFISCATION' | 'PERMIT_SUSPENSION' | 'DISMISSED';
export type SanctionAction = 'ADVANCE' | 'DECIDE' | 'CLOSE' | 'FILE_DEFENSE' | 'FILE_APPEAL';

export interface SanctionSummary {
  id: string;
  number: string;
  vesselName: string;
  investigatedName: string;
  infraction: Infraction;
  infractionLabel: string;
  stage: SanctionStage;
  stageLabel: string;
  stageDeadline: string | null;
  overdue: boolean;
  openedAt: string;
}

export interface SanctionProcess extends SanctionSummary {
  origin: 'INSPECTION' | 'REPORT';
  inspectionId: string | null;
  inspectionCode: string | null;
  reportId: string | null;
  reportCode: string | null;
  vesselId: string;
  vesselRegistration: string;
  facts: string;
  defenseSubmitted: boolean;
  appealSubmitted: boolean;
  sanction: SanctionKind | null;
  sanctionLabel: string | null;
  fineSmmlv: number | null;
  decisionText: string | null;
  openedBy: string;
  closedAt: string | null;
  actions: { sequence: number; kind: string; stageFrom: string | null; stageTo: string; note: string | null; actorName: string; occurredAt: string }[];
  allowedActions: SanctionAction[];
}

/** HU-16: oferta del marketplace. Solo alias y municipio del vendedor (AC-16.5). */
export interface MarketOffer {
  id: string;
  lotCode: string;
  species: string;
  scientificName: string | null;
  weightKg: number;
  pricePerKg: number;
  totalPrice: number;
  port: string;
  landingDate: string;
  sellerAlias: string;
  municipality: string;
  notes: string | null;
  publishedAt: string;
  expiresAt: string;
  verificationPath: string;
}

export interface EligibleLot {
  lotId: string;
  lotCode: string;
  species: string;
  weightKg: number;
  port: string;
  landingDate: string;
  zone: string;
}

export type MarketRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface MyMarketOffer {
  offer: MarketOffer;
  status: 'ACTIVE' | 'SOLD' | 'EXPIRED' | 'WITHDRAWN';
  requests: { id: string; buyerAlias: string; quantityKg: number; message: string; status: MarketRequestStatus; createdAt: string; decidedAt: string | null }[];
}

export interface MyMarketRequest {
  id: string;
  offer: MarketOffer;
  offerStatus: MyMarketOffer['status'];
  quantityKg: number;
  message: string;
  status: MarketRequestStatus;
  createdAt: string;
  decidedAt: string | null;
}

/** HU-19: módulo educativo. */
export type CourseIcon = 'ruler' | 'calendar' | 'park' | 'hand' | 'fish' | 'boat' | 'book';

export interface CourseCard {
  id: string;
  title: string;
  summary: string;
  icon: CourseIcon;
  lessons: number;
  minutes: number;
  completedLessons: number;
  passed: boolean;
  certificateId: string | null;
  certificateCode: string | null;
  version: number;
}

export interface CourseLesson {
  id: string;
  position: number;
  title: string;
  body: string;
  imageUrl: string | null;
  audioUrl: string | null;
  minutes: number;
  completed: boolean;
}

export interface CourseDetail {
  id: string;
  title: string;
  summary: string;
  icon: CourseIcon;
  version: number;
  passScore: number;
  lessons: CourseLesson[];
  questions: { id: string; position: number; prompt: string; options: string[] }[];
  completedLessons: number;
  passed: boolean;
  certificateId: string | null;
  certificateCode: string | null;
}

export interface AttemptResult {
  score: number;
  passScore: number;
  passed: boolean;
  answers: { questionId: string; correct: boolean; correctIndex: number; explanation: string | null }[];
  certificateId: string | null;
  certificateCode: string | null;
}

export interface CourseInput {
  title: string;
  summary: string;
  icon: CourseIcon;
  passScore: number;
  lessons: { title: string; body: string; imageUrl?: string | null; audioUrl?: string | null; minutes: number }[];
  questions: { prompt: string; options: string[]; correctIndex: number; explanation?: string | null }[];
}

export interface AdminCourse extends CourseInput {
  id: string;
  status: 'DRAFT' | 'PUBLISHED';
  version: number;
  updatedAt: string;
}

export const domainService = {
  // --- Embarcaciones y artes (RF-02) ---------------------------------
  async vessels(): Promise<Vessel[]> {
    return (await httpClient.get<Vessel[]>('/embarcaciones')).data;
  },
  async createVessel(payload: {
    name: string;
    registration: string;
    port: string;
    hullType: string;
    enginePower: number;
    capacityTons: number;
    gears: string[];
    status?: string;
  }): Promise<Vessel> {
    return (await httpClient.post<Vessel>('/embarcaciones', payload)).data;
  },
  /** AC-02.4: cambia el estado (y opcionalmente puerto o artes) de una embarcación. */
  async updateVessel(id: string, payload: { status?: string; port?: string; gears?: string[] }): Promise<Vessel> {
    return (await httpClient.patch<Vessel>(`/embarcaciones/${id}`, payload)).data;
  },
  /** AC-02.5: perfil completo de una embarcación (usado por el escaneo QR de campo). */
  async vesselDetail(id: string): Promise<Vessel> {
    return (await httpClient.get<Vessel>(`/embarcaciones/${id}`)).data;
  },
  async gears(): Promise<Gear[]> {
    return (await httpClient.get<Gear[]>('/artes-pesca')).data;
  },
  async createGear(payload: { name: string; description?: string; destructive: boolean; status?: string }): Promise<Gear> {
    return (await httpClient.post<Gear>('/artes-pesca', payload)).data;
  },

  // --- Especies y vedas (RF-06 / RF-05) -------------------------------
  /** AC-06.3: cachea el catálogo en cada fetch exitoso y cae a la copia local sin conexión. */
  async species(): Promise<Species[]> {
    try {
      const data = (await httpClient.get<Species[]>('/especies')).data;
      cacheSpecies(data);
      return data;
    } catch (error) {
      const cached = getCachedSpecies();
      if (cached) return cached;
      throw error;
    }
  },
  async createSpecies(payload: {
    commonName: string;
    scientificName: string;
    minSizeCm: number;
    measurementMethod: string;
    region: string;
    imageUrl: string;
    status?: string;
  }): Promise<Species> {
    return (await httpClient.post<Species>('/especies', payload)).data;
  },
  async updateSpecies(
    id: string,
    payload: Partial<{
      commonName: string;
      scientificName: string;
      minSizeCm: number;
      measurementMethod: string;
      region: string;
      imageUrl: string;
      status: string;
    }>,
  ): Promise<Species> {
    return (await httpClient.patch<Species>(`/especies/${id}`, payload)).data;
  },
  /** AC-06.4: historial de versiones de una especie. */
  async speciesVersions(id: string): Promise<SpeciesVersion[]> {
    return (await httpClient.get<SpeciesVersion[]>(`/especies/${id}/versiones`)).data;
  },
  async closures(): Promise<Closure[]> {
    return (await httpClient.get<Closure[]>('/vedas')).data;
  },
  /** Solo las vedas vigentes hoy; se usa para avisar en el formulario de faena. */
  async activeClosures(): Promise<Closure[]> {
    return (await httpClient.get<Closure[]>('/vedas/activas')).data;
  },
  async createClosure(payload: {
    speciesId: string;
    zone: string;
    from: string;
    to: string;
    resolutionNumber: string;
    closureType: string;
    reason?: string;
    notes?: string;
  }): Promise<Closure> {
    return (await httpClient.post<Closure>('/vedas', payload)).data;
  },
  async updateClosure(
    id: string,
    payload: Partial<{
      status: string;
      from: string;
      to: string;
      resolutionNumber: string;
      closureType: string;
      notes: string;
    }>,
  ): Promise<Closure> {
    return (await httpClient.patch<Closure>(`/vedas/${id}`, payload)).data;
  },

  // --- Cuotas (RF-07) -------------------------------------------------
  /** AC-07.6: cachea las cuotas para validar capturas sin conexión con el último saldo conocido. */
  async quotas(): Promise<Quota[]> {
    try {
      const data = (await httpClient.get<Quota[]>('/cuotas')).data;
      cacheQuotas(data);
      return data;
    } catch (error) {
      const cached = getCachedQuotas();
      if (cached) return cached;
      throw error;
    }
  },
  // --- Educación (HU-19) --------------------------------------------------
  async courses(): Promise<CourseCard[]> {
    return (await httpClient.get<CourseCard[]>('/educacion/cursos')).data;
  },
  async course(id: string): Promise<CourseDetail> {
    return (await httpClient.get<CourseDetail>(`/educacion/cursos/${encodeURIComponent(id)}`)).data;
  },
  async completeLesson(courseId: string, lessonId: string): Promise<CourseDetail> {
    return (await httpClient.post<CourseDetail>(`/educacion/cursos/${encodeURIComponent(courseId)}/lecciones/${encodeURIComponent(lessonId)}/completar`)).data;
  },
  async submitQuiz(courseId: string, answers: number[]): Promise<AttemptResult> {
    return (await httpClient.post<AttemptResult>(`/educacion/cursos/${encodeURIComponent(courseId)}/intentos`, { answers })).data;
  },
  async certificatePdf(id: string): Promise<Blob> {
    return (await httpClient.get<Blob>(`/educacion/constancias/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' })).data;
  },
  async verifyCertificate(code: string): Promise<{ code: string; holderName: string; courseTitle: string; score: number; issuedAt: string }> {
    return (await publicClient.get(`/educacion/constancias/verificar/${encodeURIComponent(code.trim())}`)).data;
  },
  async adminCourses(): Promise<AdminCourse[]> {
    return (await httpClient.get<AdminCourse[]>('/educacion/admin/cursos')).data;
  },
  async saveCourse(input: CourseInput, id?: string): Promise<AdminCourse> {
    return (id
      ? await httpClient.put<AdminCourse>(`/educacion/admin/cursos/${encodeURIComponent(id)}`, input)
      : await httpClient.post<AdminCourse>('/educacion/admin/cursos', input)).data;
  },
  async publishCourse(id: string, publish: boolean): Promise<AdminCourse> {
    return (await httpClient.post<AdminCourse>(`/educacion/admin/cursos/${encodeURIComponent(id)}/${publish ? 'publicar' : 'retirar'}`)).data;
  },
  // --- Marketplace (HU-16) -------------------------------------------------
  /** Catálogo público: viaja sin sesión. */
  async marketOffers(filters: { speciesId?: string; port?: string; maxPrice?: number } = {}): Promise<MarketOffer[]> {
    return (await publicClient.get<MarketOffer[]>('/mercado/ofertas', {
      params: { especieId: filters.speciesId || undefined, puerto: filters.port || undefined, precioMax: filters.maxPrice || undefined },
    })).data;
  },
  async marketEligibleLots(): Promise<EligibleLot[]> {
    return (await httpClient.get<EligibleLot[]>('/mercado/lotes-disponibles')).data;
  },
  async publishOffer(payload: { lotId: string; pricePerKg: number; notes?: string }): Promise<MarketOffer> {
    return (await httpClient.post<MarketOffer>('/mercado/ofertas', payload)).data;
  },
  async withdrawOffer(id: string): Promise<MarketOffer> {
    return (await httpClient.post<MarketOffer>(`/mercado/ofertas/${encodeURIComponent(id)}/retirar`)).data;
  },
  async myMarketOffers(): Promise<MyMarketOffer[]> {
    return (await httpClient.get<MyMarketOffer[]>('/mercado/mis-ofertas')).data;
  },
  async requestOffer(id: string, payload: { quantityKg: number; message: string }): Promise<MyMarketRequest> {
    return (await httpClient.post<MyMarketRequest>(`/mercado/ofertas/${encodeURIComponent(id)}/solicitudes`, payload)).data;
  },
  async myMarketRequests(): Promise<MyMarketRequest[]> {
    return (await httpClient.get<MyMarketRequest[]>('/mercado/mis-solicitudes')).data;
  },
  async decideMarketRequest(id: string, accept: boolean): Promise<MyMarketOffer[]> {
    return (await httpClient.post<MyMarketOffer[]>(`/mercado/solicitudes/${encodeURIComponent(id)}/${accept ? 'aceptar' : 'rechazar'}`)).data;
  },
  // --- Procesos sancionatorios (HU-23) ------------------------------------
  async openSanction(payload: { inspectionId?: string; reportId?: string; vesselId?: string; infraction?: Infraction; facts?: string }): Promise<SanctionProcess> {
    return (await httpClient.post<SanctionProcess>('/procesos-sancionatorios', payload)).data;
  },
  async sanctions(): Promise<SanctionSummary[]> {
    return (await httpClient.get<SanctionSummary[]>('/procesos-sancionatorios')).data;
  },
  async sanction(id: string): Promise<SanctionProcess> {
    return (await httpClient.get<SanctionProcess>(`/procesos-sancionatorios/${encodeURIComponent(id)}`)).data;
  },
  /** Id del proceso abierto a partir de un acta, o null. */
  async sanctionForInspection(inspectionId: string): Promise<string | null> {
    const response = await httpClient.get<{ processId: string } | ''>(`/procesos-sancionatorios/por-acta/${encodeURIComponent(inspectionId)}`);
    return response.status === 204 || !response.data ? null : response.data.processId;
  },
  async sanctionStep(id: string, step: 'avanzar' | 'cierre', note?: string): Promise<SanctionProcess> {
    return (await httpClient.post<SanctionProcess>(`/procesos-sancionatorios/${encodeURIComponent(id)}/${step}`, { note: note || undefined })).data;
  },
  async decideSanction(id: string, payload: { sanction: SanctionKind; fineSmmlv?: number; text: string }): Promise<SanctionProcess> {
    return (await httpClient.post<SanctionProcess>(`/procesos-sancionatorios/${encodeURIComponent(id)}/decision`, payload)).data;
  },
  async fileSanction(id: string, kind: 'descargos' | 'recurso', text: string): Promise<SanctionProcess> {
    return (await httpClient.post<SanctionProcess>(`/procesos-sancionatorios/${encodeURIComponent(id)}/${kind}`, { text })).data;
  },
  // --- Inspecciones en campo (HU-22) ---------------------------------------
  /** 201 si el acta es nueva, 200 si ya había llegado (reenvío idempotente por clientId). */
  async submitInspection(payload: InspectionInput, photos: Blob[]): Promise<Inspection> {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    photos.forEach((photo, index) => form.append('photos', photo, `foto-${index + 1}.jpg`));
    return (await httpClient.post<Inspection>('/inspecciones', form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  async inspections(vesselId?: string): Promise<InspectionSummary[]> {
    return (await httpClient.get<InspectionSummary[]>('/inspecciones', { params: vesselId ? { embarcacionId: vesselId } : undefined })).data;
  },
  async inspection(id: string): Promise<Inspection> {
    return (await httpClient.get<Inspection>(`/inspecciones/${encodeURIComponent(id)}`)).data;
  },
  async inspectionPdf(id: string): Promise<Blob> {
    return (await httpClient.get<Blob>(`/inspecciones/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' })).data;
  },
  async verifyInspection(code: string): Promise<PublicInspection> {
    return (await publicClient.get<PublicInspection>(`/inspecciones/verificar/${encodeURIComponent(code.trim())}`)).data;
  },
  // --- Datos ambientales (RF-14) -------------------------------------------
  async environmentOverview(): Promise<EnvOverview> {
    return (await httpClient.get<EnvOverview>('/ambiental/zonas')).data;
  },
  async environmentSeries(zoneId: string, from: string, to: string, speciesId?: string): Promise<EnvSeries> {
    return (await httpClient.get<EnvSeries>(`/ambiental/zonas/${encodeURIComponent(zoneId)}/serie`, {
      params: { desde: from, hasta: to, especieId: speciesId || undefined },
    })).data;
  },
  async importEnvironment(): Promise<{ readingsStored: number; anomalies: number; sources: EnvSourceStatus[] }> {
    return (await httpClient.post('/ambiental/importaciones')).data;
  },
  // --- Rendimiento Máximo Sostenible (RF-17) ------------------------------
  async rmsEstimates(): Promise<RmsEstimate[]> {
    return (await httpClient.get<RmsEstimate[]>('/rms')).data;
  },
  async rmsEstimate(speciesId: string): Promise<RmsEstimate> {
    return (await httpClient.get<RmsEstimate>(`/rms/especies/${encodeURIComponent(speciesId)}`)).data;
  },
  async requestRmsRun(): Promise<RmsRun> {
    return (await httpClient.post<RmsRun>('/rms/calculos')).data;
  },
  /** Último cálculo; null si nunca se ha calculado (204). */
  async latestRmsRun(): Promise<RmsRun | null> {
    const response = await httpClient.get<RmsRun | ''>('/rms/calculos/ultimo');
    return response.status === 204 || !response.data ? null : response.data;
  },
  async rmsProposals(): Promise<RmsProposal[]> {
    return (await httpClient.get<RmsProposal[]>('/rms/propuestas')).data;
  },
  async proposeRmsQuota(
    speciesId: string,
    payload: { proposedKg: number; zone: string; from: string; to: string; justification?: string },
  ): Promise<RmsProposal> {
    return (await httpClient.post<RmsProposal>(`/rms/especies/${encodeURIComponent(speciesId)}/propuestas`, payload)).data;
  },
  async decideRmsProposal(id: string, decision: 'aprobar' | 'rechazar', note?: string): Promise<RmsProposal> {
    return (await httpClient.post<RmsProposal>(`/rms/propuestas/${id}/${decision}`, { note: note || undefined })).data;
  },
  async createQuota(payload: {
    speciesId: string;
    zone: string;
    from: string;
    to: string;
    totalKg: number;
    vesselId?: string;
    resolutionNumber?: string;
    notes?: string;
  }): Promise<Quota> {
    return (await httpClient.post<Quota>('/cuotas', payload)).data;
  },
  async updateQuota(
    id: string,
    payload: Partial<{ totalKg: number; to: string; status: 'ACTIVE' | 'CLOSED'; notes: string }>,
  ): Promise<Quota> {
    return (await httpClient.patch<Quota>(`/cuotas/${id}`, payload)).data;
  },

  // --- Geolocalización (RF-04) ----------------------------------------
  /** AC-04.6: las zonas se cachean para comprobar intrusiones en el propio dispositivo sin señal. */
  async zones(): Promise<RestrictedZone[]> {
    try {
      const data = (await httpClient.get<RestrictedZone[]>('/zonas-restringidas')).data;
      cacheZones(data);
      return data;
    } catch (error) {
      const cached = getCachedZones();
      if (cached) return cached;
      throw error;
    }
  },
  /** HU-13: AMP activos, sin sesión. zoom simplifica la geometría para el mapa (R4). */
  async marineProtectedAreas(zoom?: number): Promise<MarineProtectedArea[]> {
    return (await httpClient.get<MarineProtectedArea[]>('/areas-protegidas', { params: zoom == null ? {} : { zoom } })).data;
  },
  async marineProtectedArea(id: string): Promise<MarineProtectedArea> {
    return (await httpClient.get<MarineProtectedArea>(`/areas-protegidas/${encodeURIComponent(id)}`)).data;
  },
  async createZone(payload: {
    name: string;
    zoneType: RestrictedZone['zoneType'];
    polygon: [number, number][];
    resolutionNumber: string;
    description?: string;
  }): Promise<RestrictedZone> {
    return (await httpClient.post<RestrictedZone>('/zonas-restringidas', payload)).data;
  },
  async updateZone(id: string, payload: { active?: boolean; description?: string }): Promise<RestrictedZone> {
    return (await httpClient.patch<RestrictedZone>(`/zonas-restringidas/${id}`, payload)).data;
  },
  async postPositions(tripId: string, positions: PositionInput[]): Promise<PositionBatchResult> {
    return (await httpClient.post<PositionBatchResult>(`/faenas/${tripId}/posiciones`, { positions })).data;
  },
  async tripPositions(tripId: string): Promise<TripPosition[]> {
    return (await httpClient.get<TripPosition[]>(`/faenas/${tripId}/posiciones`)).data;
  },
  async tripZoneEvents(tripId: string): Promise<ZoneEvent[]> {
    return (await httpClient.get<ZoneEvent[]>(`/faenas/${tripId}/eventos-zona`)).data;
  },
  async zoneEvents(): Promise<ZoneEvent[]> {
    return (await httpClient.get<ZoneEvent[]>('/eventos-zona')).data;
  },
  async reviewZoneEvent(id: string): Promise<ZoneEvent> {
    return (await httpClient.patch<ZoneEvent>(`/eventos-zona/${id}/revisar`)).data;
  },

  // --- Denuncias (RF-10) ----------------------------------------------
  /**
   * AC-10.2: la denuncia anónima viaja por el cliente público, sin cabecera Authorization, para
   * que el servidor no pueda asociarla a la cuenta aunque haya sesión iniciada.
   */
  async submitReport(payload: ReportInput, photos: Blob[]): Promise<ReportReceipt> {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    photos.forEach((photo, index) => form.append('photos', photo, `foto-${index + 1}`));
    const client = payload.anonymous ? publicClient : httpClient;
    return (await client.post<ReportReceipt>('/denuncias', form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  async trackReport(code: string): Promise<ReportTracking> {
    return (await publicClient.get<ReportTracking>(`/denuncias/seguimiento/${encodeURIComponent(code.trim())}`)).data;
  },
  async myReports(): Promise<ReportTracking[]> {
    return (await httpClient.get<ReportTracking[]>('/denuncias/mias')).data;
  },
  async reports(status?: ReportStatus): Promise<ReportSummary[]> {
    return (await httpClient.get<ReportSummary[]>('/denuncias', { params: status ? { estado: status } : undefined })).data;
  },
  async reportDetail(id: string): Promise<ReportDetail> {
    return (await httpClient.get<ReportDetail>(`/denuncias/${id}`)).data;
  },
  async changeReportStatus(id: string, status: ReportStatus, note: string): Promise<ReportDetail> {
    return (await httpClient.patch<ReportDetail>(`/denuncias/${id}/estado`, { status, note })).data;
  },
  /** Las fotos exigen token: se traen como blob y se muestran con una URL de objeto. */
  async reportPhoto(id: string, photoId: string): Promise<Blob> {
    return (await httpClient.get<Blob>(`/denuncias/${id}/fotos/${photoId}`, { responseType: 'blob' })).data;
  },

  // --- Permisos (RF-08) -----------------------------------------------
  async permits(): Promise<Permit[]> {
    return (await httpClient.get<Permit[]>('/permisos')).data;
  },
  /** AC-08.5: la copia de cédula y el certificado de propiedad son obligatorios. */
  async createPermit(
    payload: { type: string; issued?: string; expires?: string },
    idDocument: File,
    vesselOwnershipDocument: File,
  ): Promise<Permit> {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    form.append('idDocument', idDocument);
    form.append('vesselOwnershipDocument', vesselOwnershipDocument);
    return (await httpClient.post<Permit>('/permisos', form)).data;
  },
  async evaluatePermit(
    permitId: string,
    status: PermitStatus,
    reason?: string,
  ): Promise<Permit> {
    return (await httpClient.patch<Permit>(`/permisos/${permitId}/evaluar`, { status, reason })).data;
  },
  /** AC-08.4: verifica un permiso por número o por el código escaneado en su QR. */
  async validatePermit(numberOrCode: string): Promise<Permit> {
    return (await httpClient.get<Permit>(`/permisos/validar/${encodeURIComponent(numberOrCode)}`)).data;
  },
  /**
   * AC-08.5: descarga un adjunto. El endpoint exige el token (como todo `/api/**`), así que no se
   * puede usar un `<a href>` plano — se trae como blob y se dispara la descarga en el navegador.
   */
  async downloadAttachment(permitId: string, kind: PermitAttachment['kind'], filename: string): Promise<void> {
    const response = await httpClient.get(`/permisos/${permitId}/adjuntos/${kind}`, { responseType: 'blob' });
    const url = window.URL.createObjectURL(response.data as Blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  // --- Faenas y capturas (RF-03) --------------------------------------
  async trips(): Promise<Trip[]> {
    return (await httpClient.get<Trip[]>('/faenas')).data;
  },
  async startTrip(payload: { id?: string; vesselId: string; zone: string; originPort: string; date?: string }): Promise<Trip> {
    return (await httpClient.post<Trip>('/faenas', payload)).data;
  },
  async addCapture(
    tripId: string,
    payload: { speciesId: string; weightKg: number; averageSizeCm: number; quantity: number },
  ): Promise<Trip> {
    return (await httpClient.post<Trip>(`/faenas/${tripId}/capturas`, payload)).data;
  },
  async closeTrip(
    tripId: string,
    payload: { nullDeclaration: boolean; landingLatitude: number; landingLongitude: number },
  ): Promise<Trip> {
    return (await httpClient.post<Trip>(`/faenas/${tripId}/cerrar`, payload)).data;
  },
  async sync(pending: PendingTripPayload[]): Promise<Trip[]> {
    return (await httpClient.post<Trip[]>('/faenas/sincronizar', pending)).data;
  },
  /** AC-03.5: corrige una faena; si ya está cerrada, `reason` es obligatorio (lo valida el backend). */
  async correctTrip(
    id: string,
    payload: { zone?: string; arrivalTime?: string; notes?: string; reason?: string },
  ): Promise<Trip> {
    const { zone, ...body } = payload;
    const query = zone ? `?zone=${encodeURIComponent(zone)}` : '';
    return (await httpClient.patch<Trip>(`/faenas/${id}${query}`, body)).data;
  },

  // --- Administración y auditoría (RF-20) -----------------------------
  async users(): Promise<UserRow[]> {
    return (await httpClient.get<UserRow[]>('/usuarios')).data;
  },
  async createUser(payload: {
    nationalId: string;
    fullName: string;
    email: string;
    role: Role;
    password: string;
  }): Promise<UserRow> {
    return (await httpClient.post<UserRow>('/usuarios', payload)).data;
  },
  /** AC-20.4: filtros opcionales de fecha, tipo de acción, módulo y usuario. */
  async audit(filters?: AuditFilters): Promise<AuditEvent[]> {
    return (await httpClient.get<AuditEvent[]>('/auditoria', { params: filters })).data;
  },

  // --- Inicio y notificaciones ----------------------------------------
  async summary(): Promise<DashboardSummary> {
    return (await httpClient.get<DashboardSummary>('/resumen')).data;
  },
  // --- Reportes oficiales (RF-18) --------------------------------------
  async officialCatalog(): Promise<OfficialReportCatalogEntry[]> {
    return (await httpClient.get<OfficialReportCatalogEntry[]>('/reportes-oficiales')).data;
  },
  async officialPreview(kind: OfficialReportKind, filters: IndicatorFilters): Promise<OfficialReportPreview> {
    return (await httpClient.get<OfficialReportPreview>(`/reportes-oficiales/${kind}/vista`, { params: indicatorParams(filters) })).data;
  },
  /** AC-18.5: hasta un año devuelve el archivo; más largo responde 202 con el trabajo en cola. */
  async officialExport(kind: OfficialReportKind, format: ExportFormat, filters: IndicatorFilters): Promise<ExportOutcome> {
    const response = await httpClient.get<Blob>(`/reportes-oficiales/${kind}/exportar`, {
      params: { ...indicatorParams(filters), formato: format },
      responseType: 'blob',
    });
    if (response.status === 202) {
      return { kind: 'job', job: JSON.parse(await response.data.text()) as ExportJob };
    }
    const fallback = `sigepes_${kind.toLowerCase()}.${format.toLowerCase()}`;
    return { kind: 'file', blob: response.data, filename: filenameFrom(response.headers['content-disposition'] as string | undefined, fallback) };
  },
  async exportJobs(): Promise<ExportJob[]> {
    return (await httpClient.get<ExportJob[]>('/reportes-oficiales/trabajos')).data;
  },
  async exportJobFile(job: ExportJob): Promise<{ blob: Blob; filename: string }> {
    const response = await httpClient.get<Blob>(`/reportes-oficiales/trabajos/${job.id}/archivo`, { responseType: 'blob' });
    return {
      blob: response.data,
      filename: filenameFrom(response.headers['content-disposition'] as string | undefined, `sigepes_${job.kind.toLowerCase()}.${job.format.toLowerCase()}`),
    };
  },

  // --- Documentos (RF-15) ----------------------------------------------
  async documents(filters: { tipo?: DocType | ''; entidad?: DocEntityType | ''; estado?: DocExpiryStatus | '' } = {}): Promise<DocumentRecord[]> {
    const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value));
    return (await httpClient.get<DocumentRecord[]>('/documentos', { params })).data;
  },
  async document(id: string): Promise<DocumentRecord> {
    return (await httpClient.get<DocumentRecord>(`/documentos/${id}`)).data;
  },
  async uploadDocument(
    payload: { docType: DocType; title: string; entityType: DocEntityType; entityId: string; expiresOn?: string; note?: string },
    file: File,
  ): Promise<DocumentRecord> {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    form.append('file', file);
    return (await httpClient.post<DocumentRecord>('/documentos', form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  /** RN-30: agrega una versión; las anteriores se conservan. */
  async uploadDocumentVersion(id: string, payload: { expiresOn?: string; note?: string }, file: File): Promise<DocumentRecord> {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    form.append('file', file);
    return (await httpClient.post<DocumentRecord>(`/documentos/${id}/versiones`, form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
  },
  /** AC-15.5: pide un enlace firmado de 5 minutos y devuelve la URL absoluta para abrirlo. */
  async documentDownloadUrl(id: string, version: number): Promise<string> {
    const { url } = (await httpClient.post<{ url: string; expiresAt: string }>(`/documentos/${id}/versiones/${version}/enlace`)).data;
    const base = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/api\/v1\/?$/, '');
    return `${base}${url}`;
  },

  // --- Indicadores (RF-12) ---------------------------------------------
  async indicators(filters: IndicatorFilters): Promise<IndicatorsResponse> {
    return (await httpClient.get<IndicatorsResponse>('/indicadores', { params: indicatorParams(filters) })).data;
  },
  /** AC-12.6: un pescador o armador solo consulta sus propias cifras. */
  async ownIndicators(filters: IndicatorFilters): Promise<OwnIndicators> {
    return (await httpClient.get<OwnIndicators>('/indicadores/mios', { params: indicatorParams(filters) })).data;
  },

  // --- Trazabilidad (RF-09) --------------------------------------------
  async lots(): Promise<Lot[]> {
    return (await httpClient.get<Lot[]>('/lotes')).data;
  },
  async eligibleCatches(tripId: string): Promise<EligibleCatch[]> {
    return (await httpClient.get<EligibleCatch[]>(`/faenas/${tripId}/capturas-para-lote`)).data;
  },
  async createLot(catchId: string, weightKg: number): Promise<Lot> {
    return (await httpClient.post<Lot>('/lotes', { catchId, weightKg })).data;
  },
  async transferLot(
    lotId: string,
    payload: { partyType: PartyType; partyName: string; partyTaxId?: string; location: string; occurredOn?: string },
  ): Promise<Lot> {
    return (await httpClient.post<Lot>(`/lotes/${lotId}/transferencias`, payload)).data;
  },
  /** Público: se consulta sin token, igual que lo haría un comprador con su teléfono. */
  async verifyLot(code: string): Promise<PublicLot> {
    return (await publicClient.get<PublicLot>(`/trazabilidad/${encodeURIComponent(code.trim())}`)).data;
  },

  // --- Alertas (RF-11) -------------------------------------------------
  async alerts(): Promise<AlertInbox> {
    return (await httpClient.get<AlertInbox>('/alertas')).data;
  },
  async unreadAlerts(): Promise<number> {
    return (await httpClient.get<{ unread: number }>('/alertas/no-leidas')).data.unread;
  },
  async markAlertRead(id: string): Promise<AppAlert> {
    return (await httpClient.patch<AppAlert>(`/alertas/${id}/leida`)).data;
  },
  async markAllAlertsRead(): Promise<number> {
    return (await httpClient.patch<{ unread: number }>('/alertas/leidas')).data.unread;
  },
  /** RN-22: el servidor rechaza archivar una alerta crítica. */
  async archiveAlert(id: string): Promise<void> {
    await httpClient.delete(`/alertas/${id}`);
  },
};
