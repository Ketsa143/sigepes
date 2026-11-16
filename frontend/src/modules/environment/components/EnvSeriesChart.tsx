import { Box, Stack, Typography } from '@mui/material';
import { useMemo, useState } from 'react';
import type { EnvReading } from '../../../shared/api/domain.service';
import { BAR_COLOR, SPLIT_COLORS } from '../../../shared/charts/chartColors';
import { VARIABLE, type EnvVariable } from './envVariables';


const W = 720;
const LINE_H = 230;
const BAR_H = 120;
const PAD = { top: 14, right: 18, bottom: 28, left: 56 };
const dayLabel = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', timeZone: 'America/Bogota' });
const fullLabel = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Bogota' });
const kg = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });

interface Props {
  readings: EnvReading[];
  variable: EnvVariable;
  from: string;
  to: string;
  /** Referencia de temperatura y umbral de anomalía: banda sombreada (solo temperatura). */
  baseline?: number | null;
  threshold?: number;
  /** AC-14.4: capturas diarias de la especie elegida, en un panel aparte con el mismo eje de tiempo. */
  catches?: { date: string; weightKg: number }[];
  species?: string | null;
}

function extent(values: number[], pad: number): [number, number] {
  if (values.length === 0) return [0, 1];
  let min = Math.min(...values);
  let max = Math.max(...values);
  if (min === max) {
    min -= 1;
    max += 1;
  }
  const margin = (max - min) * pad;
  return [min - margin, max + margin];
}

/** Las fechas de los extremos se alinean hacia dentro para no salirse del gráfico. */
function anchor(index: number): 'start' | 'middle' | 'end' {
  return index === 0 ? 'start' : index === 4 ? 'end' : 'middle';
}

/** Inicio del día en Colombia (UTC-5 fijo) como instante, para alinear lecturas y capturas. */
function dayStart(date: string): number {
  return new Date(`${date}T00:00:00-05:00`).getTime();
}

/**
 * HU-14 · AC-14.2 / AC-14.4 · Serie de una variable ambiental y, debajo, las capturas de la especie
 * del mismo periodo. Son dos paneles con el mismo eje de tiempo, no un gráfico de dos ejes.
 */
export function EnvSeriesChart({ readings, variable, from, to, baseline, threshold, catches = [], species }: Props) {
  const meta = VARIABLE[variable];
  const points = useMemo(
    () => readings
      .filter((r) => r[variable] != null)
      .map((r) => ({ t: new Date(r.observedAt).getTime(), v: Number(r[variable]), at: r.observedAt })),
    [readings, variable],
  );
  const [hover, setHover] = useState<number | null>(null);

  const t0 = dayStart(from);
  const t1 = dayStart(to) + 86_400_000;
  const x = (t: number) => PAD.left + ((t - t0) / (t1 - t0)) * (W - PAD.left - PAD.right);

  const band = variable === 'sstC' && baseline != null && threshold != null
    ? [baseline - threshold, baseline + threshold] as const
    : null;
  const [vMin, vMax] = extent([...points.map((p) => p.v), ...(band ?? [])], 0.08);
  const y = (v: number) => LINE_H - PAD.bottom - ((v - vMin) / (vMax - vMin)) * (LINE_H - PAD.top - PAD.bottom);
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ');
  const yTicks = [0, 0.25, 0.5, 0.75, 1].map((f) => vMin + f * (vMax - vMin));
  const days = Math.max(1, Math.round((t1 - t0) / 86_400_000));
  const xTicks = Array.from({ length: 5 }, (_, i) => t0 + ((t1 - t0) * i) / 4);

  const maxCatch = Math.max(1, ...catches.map((c) => c.weightKg));
  const barWidth = Math.max(2, ((W - PAD.left - PAD.right) / days) - 2);
  const by = (value: number) => BAR_H - PAD.bottom - (value / maxCatch) * (BAR_H - PAD.top - PAD.bottom);

  const nearest = (clientX: number, svg: SVGSVGElement) => {
    const rect = svg.getBoundingClientRect();
    const px = ((clientX - rect.left) / rect.width) * W;
    let best = -1;
    let bestDistance = Infinity;
    points.forEach((p, i) => {
      const d = Math.abs(x(p.t) - px);
      if (d < bestDistance) {
        bestDistance = d;
        best = i;
      }
    });
    return best >= 0 ? best : null;
  };
  const hovered = hover == null ? null : points[hover];

  return (
    <Stack spacing={1}>
      <Box sx={{ position: 'relative' }}>
        <Box
          component="svg"
          viewBox={`0 0 ${W} ${LINE_H}`}
          role="img"
          aria-label={`${meta.label} entre ${from} y ${to}: ${points.length} lecturas.`}
          onMouseMove={(e: React.MouseEvent<SVGSVGElement>) => setHover(nearest(e.clientX, e.currentTarget))}
          onMouseLeave={() => setHover(null)}
          sx={{ width: '100%', height: 'auto', display: 'block', fontFamily: 'inherit', touchAction: 'pan-y' }}
        >
          {yTicks.map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke="currentColor" strokeOpacity={0.08} />
              <text x={PAD.left - 8} y={y(v) + 4} textAnchor="end" fontSize={11} fill="currentColor" fillOpacity={0.6}>
                {v.toFixed(meta.digits)}
              </text>
            </g>
          ))}
          {band && (
            <>
              <rect x={PAD.left} width={W - PAD.left - PAD.right} y={y(band[1])} height={Math.max(0, y(band[0]) - y(band[1]))}
                fill={SPLIT_COLORS[0]} fillOpacity={0.07} />
              <line x1={PAD.left} x2={W - PAD.right} y1={y(baseline!)} y2={y(baseline!)} stroke="currentColor"
                strokeOpacity={0.35} strokeDasharray="4 4" />
              <text x={PAD.left + 6} y={Math.min(LINE_H - PAD.bottom - 4, y(band[0]) + 13)} fontSize={10.5} fill="currentColor" fillOpacity={0.6}>
                Referencia {baseline!.toFixed(1)} °C · umbral ±{threshold} °C
              </text>
            </>
          )}
          {xTicks.map((t, i) => (
            <text key={t} x={x(t)} y={LINE_H - 8} textAnchor={anchor(i)} fontSize={11} fill="currentColor" fillOpacity={0.6}>
              {dayLabel.format(new Date(t))}
            </text>
          ))}
          <path d={path} fill="none" stroke={SPLIT_COLORS[0]} strokeWidth={2} strokeLinejoin="round" />
          {points.length <= 60 && points.map((p) => (
            <circle key={p.t} cx={x(p.t)} cy={y(p.v)} r={2.5} fill={SPLIT_COLORS[0]} />
          ))}
          {hovered && (
            <g>
              <line x1={x(hovered.t)} x2={x(hovered.t)} y1={PAD.top} y2={LINE_H - PAD.bottom} stroke="currentColor" strokeOpacity={0.3} />
              <circle cx={x(hovered.t)} cy={y(hovered.v)} r={5} fill={SPLIT_COLORS[0]} stroke="var(--mui-palette-background-paper, #fff)" strokeWidth={2} />
            </g>
          )}
        </Box>
        {hovered && (
          <Box sx={{
            position: 'absolute', top: 4, left: `${Math.min(70, (x(hovered.t) / W) * 100)}%`, pointerEvents: 'none',
            bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 1, px: 1, py: 0.5, boxShadow: 2,
          }}>
            <Typography variant="caption" color="text.secondary" display="block">{fullLabel.format(new Date(hovered.at))}</Typography>
            <Typography variant="body2" fontWeight={700}>{hovered.v.toFixed(meta.digits)} {meta.unit}</Typography>
          </Box>
        )}
      </Box>

      {species && (
        <Box>
          <Typography variant="caption" color="text.secondary">
            Capturas de {species} en la zona (kg por día, faenas registradas en SIGEPES)
          </Typography>
          {catches.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
              No hay capturas de {species} en esta zona durante el periodo.
            </Typography>
          ) : (
            <Box
              component="svg"
              viewBox={`0 0 ${W} ${BAR_H}`}
              role="img"
              aria-label={`Capturas de ${species}: ${catches.length} días con desembarcos.`}
              sx={{ width: '100%', height: 'auto', display: 'block', fontFamily: 'inherit' }}
            >
              <line x1={PAD.left} x2={W - PAD.right} y1={by(0)} y2={by(0)} stroke="currentColor" strokeOpacity={0.2} />
              <text x={PAD.left - 8} y={by(maxCatch) + 4} textAnchor="end" fontSize={11} fill="currentColor" fillOpacity={0.6}>
                {kg.format(maxCatch)}
              </text>
              {catches.map((c) => {
                const left = x(dayStart(c.date)) + 1;
                const top = by(c.weightKg);
                return (
                  <rect key={c.date} x={left} y={top} width={barWidth} height={Math.max(1, by(0) - top)} rx={2}
                    fill={BAR_COLOR}>
                    <title>{`${c.date}: ${kg.format(c.weightKg)} kg`}</title>
                  </rect>
                );
              })}
              {xTicks.map((t, i) => (
                <text key={t} x={x(t)} y={BAR_H - 8} textAnchor={anchor(i)} fontSize={11} fill="currentColor" fillOpacity={0.6}>
                  {dayLabel.format(new Date(t))}
                </text>
              ))}
            </Box>
          )}
        </Box>
      )}
    </Stack>
  );
}
