import type { MpaZoneKind, RestrictedZone } from '../../api/domain.service';

export interface MapPoint {
  id: string;
  latitude: number;
  longitude: number;
  /** Punto dentro de una zona restringida: se marca en rojo. */
  intrusion?: boolean;
}

export interface GeoMapProps {
  zones: RestrictedZone[];
  track?: MapPoint[];
  /** Zona a resaltar (p. ej. la que se está previsualizando en un formulario). */
  highlightZoneId?: string;
  height?: number;
  title: string;
  /**
   * Si se pasa, un clic en el mapa entrega la coordenada (ubicar una denuncia, trazar los vértices
   * de una zona). Solo el mapa de Google lo admite; el esquema sin conexión no es interactivo.
   */
  onPick?: (latitude: number, longitude: number) => void;
}

/**
 * Colores por tipo de zona. Trazo opaco y relleno translúcido para que se distingan tanto sobre el
 * esquema claro como sobre la imagen satelital oscura.
 */
export const ZONE_STYLE: Record<RestrictedZone['zoneType'], { fill: string; stroke: string; fillOpacity: number }> = {
  CLOSURE_ZONE: { fill: 'rgba(168, 90, 0, 0.16)', stroke: '#E07B00', fillOpacity: 0.28 },
  MPA: { fill: 'rgba(27, 107, 63, 0.16)', stroke: '#1FA35A', fillOpacity: 0.28 },
  OTHER: { fill: 'rgba(61, 110, 146, 0.16)', stroke: '#3D8FD1', fillOpacity: 0.26 },
};

type ZoneStyle = { fill: string; stroke: string; fillOpacity: number };

/** HU-13: dentro de un AMP el color dice qué se puede hacer: rojo intangible, verde uso, gris amortiguación. */
export const MPA_KIND_STYLE: Record<MpaZoneKind, ZoneStyle> = {
  INTANGIBLE: { fill: 'rgba(179, 38, 30, 0.16)', stroke: '#C62828', fillOpacity: 0.3 },
  SUSTAINABLE_USE: { fill: 'rgba(27, 107, 63, 0.16)', stroke: '#1FA35A', fillOpacity: 0.26 },
  BUFFER: { fill: 'rgba(96, 112, 128, 0.12)', stroke: '#7A8A99', fillOpacity: 0.18 },
};

export function zoneStyle(zone: RestrictedZone): ZoneStyle {
  return zone.mpaZoneKind ? MPA_KIND_STYLE[zone.mpaZoneKind] : ZONE_STYLE[zone.zoneType];
}

export const TRACK_COLOR = '#0B3B5C';
export const TRACK_START_COLOR = '#0E7C86';
export const INTRUSION_COLOR = '#B3261E';
