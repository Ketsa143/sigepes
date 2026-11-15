import { Box, Stack } from '@mui/material';
import type { MpaZoneKind, RestrictedZone } from '../../api/domain.service';
import { MPA_ZONE_KIND_LABEL, ZONE_TYPE_LABEL } from '../../geo/geo';
import { INTRUSION_COLOR, MPA_KIND_STYLE, TRACK_COLOR, ZONE_STYLE, type MapPoint } from './mapStyles';

/** Leyenda común al mapa de Google y al esquema: el color nunca es la única pista (WCAG 1.4.1). */
export function MapLegend({ zones, track, note }: { zones: RestrictedZone[]; track: MapPoint[]; note?: string }) {
  const intrusions = track.filter((point) => point.intrusion).length;
  return (
    <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ fontSize: '.8rem', color: 'text.secondary' }}>
      {(Object.keys(ZONE_STYLE) as RestrictedZone['zoneType'][])
        .filter((type) => zones.some((zone) => zone.zoneType === type && !zone.mpaZoneKind))
        .map((type) => (
          <Stack key={type} direction="row" spacing={0.75} alignItems="center">
            <Box sx={{ width: 14, height: 14, borderRadius: '4px', bgcolor: ZONE_STYLE[type].fill, border: `2px solid ${ZONE_STYLE[type].stroke}` }} />
            <span>{ZONE_TYPE_LABEL[type]}</span>
          </Stack>
        ))}
      {(Object.keys(MPA_KIND_STYLE) as MpaZoneKind[])
        .filter((kind) => zones.some((zone) => zone.mpaZoneKind === kind))
        .map((kind) => (
          <Stack key={kind} direction="row" spacing={0.75} alignItems="center">
            <Box sx={{ width: 14, height: 14, borderRadius: '4px', bgcolor: MPA_KIND_STYLE[kind].fill, border: `2px solid ${MPA_KIND_STYLE[kind].stroke}` }} />
            <span>{MPA_ZONE_KIND_LABEL[kind]}</span>
          </Stack>
        ))}
      {zones.some((zone) => !zone.active) && (
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 14, height: 0, borderTop: '2px dashed', borderColor: 'text.secondary' }} />
          <span>Zona inactiva</span>
        </Stack>
      )}
      {track.length > 1 && (
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 14, height: 3, borderRadius: 2, bgcolor: TRACK_COLOR }} />
          <span>Recorrido (inicio en turquesa)</span>
        </Stack>
      )}
      {intrusions > 0 && (
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: INTRUSION_COLOR }} />
          <span>Ingreso a zona restringida</span>
        </Stack>
      )}
      {note && <span>{note}</span>}
    </Stack>
  );
}
