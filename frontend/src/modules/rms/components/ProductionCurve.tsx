import { Box, Stack } from '@mui/material';
import type { RmsEstimate } from '../../../shared/api/domain.service';
import { BAR_COLOR, SPLIT_COLORS } from '../../../shared/charts/chartColors';

const W = 560;
const H = 300;
const PAD = { top: 16, right: 20, bottom: 44, left: 72 };
const kg = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });

function niceMax(value: number): number {
  if (value <= 0) return 1;
  const exponent = 10 ** Math.floor(Math.log10(value));
  const fraction = value / exponent;
  const step = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return step * exponent;
}

/**
 * HU-17 · Curva de producción de equilibrio de Schaefer (captura sostenible según el esfuerzo) con
 * los años observados encima. Las guías marcan el RMS y el esfuerzo óptimo (f-RMS).
 */
export function ProductionCurve({ estimate }: { estimate: RmsEstimate }) {
  const { curve, series } = estimate;
  const maxEffort = niceMax(Math.max(...curve.map((p) => p.effort), ...series.map((p) => p.effort), 1));
  const maxYield = niceMax(Math.max(...curve.map((p) => p.yieldKg), ...series.map((p) => p.catchKg), 1));
  const x = (effort: number) => PAD.left + (effort / maxEffort) * (W - PAD.left - PAD.right);
  const y = (yieldKg: number) => H - PAD.bottom - (yieldKg / maxYield) * (H - PAD.top - PAD.bottom);
  const ticks = [0, 0.25, 0.5, 0.75, 1];
  const path = curve.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.effort).toFixed(1)},${y(p.yieldKg).toFixed(1)}`).join(' ');
  const msy = estimate.msyKg ?? 0;
  const fMsy = estimate.fMsy ?? 0;

  return (
    <Box>
      <Box
        component="svg"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`Curva de producción de ${estimate.species ?? 'la especie'}: RMS de ${kg.format(msy)} kg por año con ${kg.format(fMsy)} faenas por año.`}
        sx={{ width: '100%', height: 'auto', display: 'block', fontFamily: 'inherit' }}
      >
        {ticks.map((t) => (
          <g key={`y${t}`}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(maxYield * t)} y2={y(maxYield * t)} stroke="currentColor" strokeOpacity={0.12} />
            <text x={PAD.left - 8} y={y(maxYield * t) + 4} textAnchor="end" fontSize={11} fill="currentColor" fillOpacity={0.7}>
              {kg.format(maxYield * t)}
            </text>
          </g>
        ))}
        {ticks.map((t) => (
          <text key={`x${t}`} x={x(maxEffort * t)} y={H - PAD.bottom + 18} textAnchor="middle" fontSize={11} fill="currentColor" fillOpacity={0.7}>
            {kg.format(maxEffort * t)}
          </text>
        ))}
        <text x={(PAD.left + W - PAD.right) / 2} y={H - 6} textAnchor="middle" fontSize={12} fill="currentColor">
          Esfuerzo (faenas por año)
        </text>
        <text x={14} y={(PAD.top + H - PAD.bottom) / 2} textAnchor="middle" fontSize={12} fill="currentColor"
          transform={`rotate(-90 14 ${(PAD.top + H - PAD.bottom) / 2})`}>
          Captura (kg por año)
        </text>

        {msy > 0 && (
          <g stroke="currentColor" strokeOpacity={0.45} strokeDasharray="4 4">
            <line x1={PAD.left} x2={x(fMsy)} y1={y(msy)} y2={y(msy)} />
            <line x1={x(fMsy)} x2={x(fMsy)} y1={y(msy)} y2={H - PAD.bottom} />
          </g>
        )}
        <path d={path} fill="none" stroke={BAR_COLOR} strokeWidth={2.5} />
        {msy > 0 && (
          <g>
            <circle cx={x(fMsy)} cy={y(msy)} r={5} fill={BAR_COLOR} />
            <text x={x(fMsy) + 8} y={y(msy) - 8} fontSize={12} fontWeight={700} fill="currentColor">
              RMS {kg.format(msy)} kg
            </text>
          </g>
        )}
        {series.map((point) => (
          <g key={point.year}>
            <circle cx={x(point.effort)} cy={y(point.catchKg)} r={4.5} fill={SPLIT_COLORS[1]} fillOpacity={0.9}
              stroke="#fff" strokeWidth={1} />
            <title>{`${point.year}: ${kg.format(point.catchKg)} kg con ${kg.format(point.effort)} faenas`}</title>
          </g>
        ))}
      </Box>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap sx={{ fontSize: '.8rem', color: 'text.secondary', mt: 1 }}>
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 16, height: 3, borderRadius: 2, bgcolor: BAR_COLOR }} />
          <span>Captura sostenible (modelo)</span>
        </Stack>
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: SPLIT_COLORS[1] }} />
          <span>Años observados</span>
        </Stack>
        <Stack direction="row" spacing={0.75} alignItems="center">
          <Box sx={{ width: 16, height: 0, borderTop: '2px dashed', borderColor: 'text.secondary' }} />
          <span>RMS y esfuerzo óptimo</span>
        </Stack>
      </Stack>
    </Box>
  );
}
