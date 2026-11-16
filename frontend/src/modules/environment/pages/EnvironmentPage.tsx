import CloudSyncOutlinedIcon from '@mui/icons-material/CloudSyncOutlined';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import WavesIcon from '@mui/icons-material/Waves';
import {
  Alert, Box, Card, CardActionArea, CardContent, Chip, CircularProgress, Grid, MenuItem, Stack, TextField,
  ToggleButton, ToggleButtonGroup, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import {
  domainService, type EnvOverview, type EnvZone, type RestrictedZone,
} from '../../../shared/api/domain.service';
import { ChartCard } from '../../../shared/charts/ChartCard';
import { GeoMap } from '../../../shared/components/GeoMap';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useAuthStore } from '../../auth/store/authStore';
import { EnvSeriesChart } from '../components/EnvSeriesChart';
import { VARIABLE, type EnvVariable } from '../components/envVariables';

const REGION_LABEL: Record<EnvZone['region'], string> = { PACIFICO: 'Pacífico', CARIBE: 'Caribe', INSULAR: 'Insular' };
const PERIODS = [7, 30, 90] as const;
const time = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

function localIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function fmt(value: number | null | undefined, digits: number): string {
  return value == null ? '—' : Number(value).toFixed(digits);
}

/** Cada zona se pinta como un recuadro alrededor de su punto de lectura. */
function zonesForMap(zones: EnvZone[]): RestrictedZone[] {
  const half = 0.35;
  return zones.map((zone) => ({
    id: zone.id,
    name: `${zone.name} · ${zone.latest?.sstC != null ? `${fmt(zone.latest.sstC, 1)} °C` : 'sin lectura'}`,
    zoneType: 'OTHER' as const,
    polygon: [
      [zone.latitude - half, zone.longitude - half], [zone.latitude + half, zone.longitude - half],
      [zone.latitude + half, zone.longitude + half], [zone.latitude - half, zone.longitude + half],
    ],
    resolutionNumber: REGION_LABEL[zone.region],
    description: zone.latest
      ? `Ola ${fmt(zone.latest.waveHeightM, 1)} m · clorofila ${fmt(zone.latest.chlorophyllMgM3, 2)} mg/m³`
      : 'Todavía no hay lecturas.',
    active: true,
  }));
}

/**
 * HU-14 · RF-14 · Tablero ambiental: temperatura del mar, clorofila-a y oleaje por zona de pesca, con
 * las capturas de una especie superpuestas y el aviso de dato viejo cuando la fuente no responde.
 */
export function EnvironmentPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canImport = user?.role === 'ANALYST' || user?.role === 'ADMIN';

  const overviewQuery = useQuery({
    queryKey: ['env-overview'],
    queryFn: domainService.environmentOverview,
    refetchInterval: 10 * 60_000,
  });
  const overview = overviewQuery.data;
  const zones = useMemo(() => overview?.zones ?? [], [overview]);
  const mapZones = useMemo(() => zonesForMap(zones), [zones]);

  const [zoneId, setZoneId] = useState<string | undefined>();
  const selected = zones.find((z) => z.id === zoneId) ?? zones.find((z) => z.sstAlert) ?? zones[0];

  const importMutation = useMutation({
    mutationFn: domainService.importEnvironment,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['env-overview'] });
      void queryClient.invalidateQueries({ queryKey: ['env-series'] });
    },
  });

  return (
    <>
      <PageHeader
        title="Datos ambientales"
        subtitle="Temperatura superficial del mar, clorofila-a y oleaje por zona de pesca. Se actualizan cada 6 horas; cruza las condiciones con las capturas para anticipar cierres preventivos."
        actionLabel={canImport ? (importMutation.isPending ? 'Importando…' : 'Importar ahora') : undefined}
        actionIcon={<CloudSyncOutlinedIcon />}
        onAction={canImport && !importMutation.isPending ? () => importMutation.mutate() : undefined}
      />

      {importMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => importMutation.reset()}>
          {importMutation.data.readingsStored === 0
            ? 'Las fuentes no tienen datos nuevos desde la última importación.'
            : `Se guardaron ${importMutation.data.readingsStored} lecturas nuevas${importMutation.data.anomalies ? ` y ${importMutation.data.anomalies} anomalías` : ''}.`}
        </Alert>
      )}
      {importMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>{toAppError(importMutation.error).message}</Alert>
      )}

      {overview && <SourceBanner overview={overview} />}

      {overviewQuery.isLoading && <CircularProgress size={28} />}
      {overviewQuery.isError && <Alert severity="error">No fue posible cargar los datos ambientales.</Alert>}

      {overview && (
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, lg: 7 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <GeoMap title="Zonas de pesca con lectura ambiental" zones={mapZones} highlightZoneId={selected?.id} height={380} />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, lg: 5 }}>
            <Stack component="ul" spacing={1} aria-label="Zonas de pesca" sx={{ listStyle: 'none', p: 0, m: 0 }}>
              {zones.map((zone) => (
                <Box component="li" key={zone.id}>
                  <ZoneCard zone={zone} selected={zone.id === selected?.id} onSelect={() => setZoneId(zone.id)}
                    threshold={overview.sstThresholdC} />
                </Box>
              ))}
            </Stack>
          </Grid>
          {selected && (
            <Grid size={12}>
              <SeriesPanel zone={selected} overview={overview} />
            </Grid>
          )}
        </Grid>
      )}
    </>
  );
}

/** AC-14.5 · D4: si una fuente no responde, se dice y se sigue mostrando lo último guardado. */
function SourceBanner({ overview }: { overview: EnvOverview }) {
  const down = overview.sources.filter((source) => !source.available);
  const allStale = overview.zones.length > 0 && overview.zones.every((zone) => zone.stale);
  if (down.length === 0 && !allStale) return null;
  const ages = overview.zones.map((zone) => zone.ageHours).filter((age): age is number => age != null);
  const newest = ages.length ? Math.min(...ages) : null;
  const names = down.map((source) => source.name).join(' y ');
  const verb = down.length > 1 ? 'no están disponibles' : 'no está disponible';
  let text: string;
  if (allStale) {
    text = newest != null
      ? `Dato de hace ${newest} h: ${down.length ? `${names} ${verb}` : 'las fuentes no entregan datos nuevos'}. Se muestran las últimas lecturas guardadas.`
      : 'Todavía no hay lecturas guardadas y las fuentes no responden.';
  } else {
    text = `${names} ${verb}: sus variables muestran el último dato guardado con su antigüedad. El resto está al día.`;
  }
  return <Alert severity="warning" icon={<WarningAmberIcon />} sx={{ mb: 2 }}>{text}</Alert>;
}

function ZoneCard({ zone, selected, onSelect, threshold }: {
  zone: EnvZone; selected: boolean; onSelect: () => void; threshold: number;
}) {
  const latest = zone.latest;
  const anomaly = latest?.anomalyC;
  return (
    <Card variant="outlined" sx={{ borderColor: selected ? 'primary.main' : 'divider', borderWidth: selected ? 2 : 1 }}>
      <CardActionArea onClick={onSelect} aria-pressed={selected}>
        <CardContent sx={{ py: 1.25, '&:last-child': { pb: 1.25 } }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
            <Box sx={{ minWidth: 0 }}>
              <Typography fontWeight={700} noWrap>{zone.name}</Typography>
              <Typography variant="caption" color="text.secondary">
                {REGION_LABEL[zone.region]}
                {zone.ageHours != null && ` · ${zone.ageHours === 0 ? 'hace menos de 1 h' : `hace ${zone.ageHours} h`}`}
              </Typography>
            </Box>
            <Stack direction="row" spacing={0.5} flexWrap="wrap" justifyContent="flex-end" useFlexGap>
              {zone.sstAlert && (
                <Chip size="small" color="error" label={`Anomalía ${anomaly != null && anomaly > 0 ? '+' : ''}${fmt(anomaly, 1)} °C`} />
              )}
              {zone.waveAlert && <Chip size="small" color="warning" icon={<WavesIcon />} label="Oleaje fuerte" />}
              {zone.stale && <Chip size="small" variant="outlined" label={latest ? 'Dato viejo' : 'Sin datos'} />}
            </Stack>
          </Stack>
          <Stack direction="row" spacing={2.5} sx={{ mt: 0.75, fontVariantNumeric: 'tabular-nums' }}>
            <Metric label="Temp." value={`${fmt(latest?.sstC, 1)} °C`}
              hint={anomaly != null && !zone.sstAlert ? `${anomaly > 0 ? '+' : ''}${fmt(anomaly, 1)} (umbral ±${threshold})` : undefined} />
            <Metric label="Clorofila"
              value={`${fmt(latest?.chlorophyllMgM3 ?? zone.lastChlorophyll?.value, 2)} mg/m³`}
              hint={latest?.chlorophyllMgM3 == null && zone.lastChlorophyll ? `dato de hace ${zone.lastChlorophyll.ageHours} h` : undefined} />
            <Metric label="Ola" value={`${fmt(latest?.waveHeightM, 1)} m`} />
          </Stack>
        </CardContent>
      </CardActionArea>
    </Card>
  );
}

function Metric({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block">{label}</Typography>
      <Typography variant="body2" fontWeight={700}>{value}</Typography>
      {hint && <Typography variant="caption" color="text.secondary">{hint}</Typography>}
    </Box>
  );
}

function SeriesPanel({ zone, overview }: { zone: EnvZone; overview: EnvOverview }) {
  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  const [variable, setVariable] = useState<EnvVariable>('sstC');
  const [speciesId, setSpeciesId] = useState('');
  const to = localIso(new Date());
  const fromDate = new Date();
  fromDate.setDate(fromDate.getDate() - (days - 1));
  const from = localIso(fromDate);

  const speciesQuery = useQuery({ queryKey: ['species'], queryFn: domainService.species, staleTime: 10 * 60_000 });
  const seriesQuery = useQuery({
    queryKey: ['env-series', zone.id, from, to, speciesId],
    queryFn: () => domainService.environmentSeries(zone.id, from, to, speciesId || undefined),
  });
  const series = seriesQuery.data;
  const meta = VARIABLE[variable];
  const readings = series?.readings ?? [];

  return (
    <ChartCard
      title={`${meta.label} · ${zone.name}`}
      subtitle={`Últimos ${days} días${zone.latest ? ` · última lectura ${time.format(new Date(zone.latest.observedAt))} (${zone.latest.source})` : ''}`}
      fill={false}
      table={{
        columns: ['Fecha y hora', 'Temperatura (°C)', 'Anomalía (°C)', 'Clorofila (mg/m³)', 'Ola (m)'],
        rows: [...readings].reverse().slice(0, 200).map((r) => [
          time.format(new Date(r.observedAt)), fmt(r.sstC, 2), fmt(r.anomalyC, 2), fmt(r.chlorophyllMgM3, 3),
          fmt(r.waveHeightM, 2),
        ]),
      }}
    >
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }} alignItems={{ md: 'center' }}>
        <ToggleButtonGroup size="small" exclusive value={variable} aria-label="Variable"
          onChange={(_, value) => value && setVariable(value)}>
          {(Object.keys(VARIABLE) as EnvVariable[]).map((key) => (
            <ToggleButton key={key} value={key} sx={{ whiteSpace: 'nowrap' }}>{VARIABLE[key].short}</ToggleButton>
          ))}
        </ToggleButtonGroup>
        <ToggleButtonGroup size="small" exclusive value={days} aria-label="Periodo"
          onChange={(_, value) => value && setDays(value)}>
          {PERIODS.map((period) => <ToggleButton key={period} value={period} sx={{ whiteSpace: 'nowrap' }}>{period} días</ToggleButton>)}
        </ToggleButtonGroup>
        <TextField select size="small" label="Superponer capturas de" value={speciesId} sx={{ minWidth: 240 }}
          onChange={(e) => setSpeciesId(e.target.value)}>
          <MenuItem value="">Ninguna especie</MenuItem>
          {(speciesQuery.data ?? []).map((species) => (
            <MenuItem key={species.id} value={species.id}>{species.commonName}</MenuItem>
          ))}
        </TextField>
      </Stack>
      {seriesQuery.isLoading && <CircularProgress size={24} />}
      {seriesQuery.isError && <Alert severity="error">{toAppError(seriesQuery.error).message}</Alert>}
      {series && readings.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
          No hay lecturas de esta zona en el periodo elegido.
        </Typography>
      )}
      {series && readings.length > 0 && (
        <EnvSeriesChart readings={readings} variable={variable} from={series.from} to={series.to}
          baseline={zone.sstBaselineC} threshold={overview.sstThresholdC}
          catches={series.catches} species={series.species} />
      )}
    </ChartCard>
  );
}
