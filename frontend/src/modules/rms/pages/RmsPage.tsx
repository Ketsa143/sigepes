import RefreshIcon from '@mui/icons-material/Refresh';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Grid, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import {
  domainService, type RmsEstimate, type RmsProposal, type StockStatus,
} from '../../../shared/api/domain.service';
import { ChartCard } from '../../../shared/charts/ChartCard';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PageHeader } from '../../../shared/components/PageHeader';
import { StatusChip } from '../../../shared/components/StatusChip';
import { toAppError } from '../../../shared/errors/AppError';
import { useAuthStore } from '../../auth/store/authStore';
import { ProductionCurve } from '../components/ProductionCurve';

const kg = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });
const dec = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const dateTime = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

const STOCK: Record<StockStatus, { label: string; color: 'success' | 'warning' | 'error' }> = {
  UNDER_EXPLOITED: { label: 'Sub-explotado', color: 'success' },
  FULLY_EXPLOITED: { label: 'Plena explotación', color: 'warning' },
  OVER_EXPLOITED: { label: 'Sobreexplotado', color: 'error' },
};

const CALCULATORS = ['ANALYST', 'ADMIN'];

/**
 * HU-17 · TASK-17.3 · Rendimiento Máximo Sostenible por especie: curva de producción, parámetros
 * del modelo de Schaefer y propuestas de cuota (≤ RMS) que el ADMIN aprueba.
 */
export function RmsPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((s) => s.user);
  const canCalculate = !!user && CALCULATORS.includes(user.role);
  const isAdmin = user?.role === 'ADMIN';

  const runQuery = useQuery({
    queryKey: ['rms-run'],
    queryFn: domainService.latestRmsRun,
    // Mientras el cálculo corre en segundo plano se consulta su estado cada 2 s.
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'PENDING' || status === 'RUNNING' ? 2000 : false;
    },
  });
  const running = runQuery.data?.status === 'PENDING' || runQuery.data?.status === 'RUNNING';
  const estimatesQuery = useQuery({ queryKey: ['rms-estimates'], queryFn: domainService.rmsEstimates });
  const proposalsQuery = useQuery({ queryKey: ['rms-proposals'], queryFn: domainService.rmsProposals });
  const estimates = useMemo(() => estimatesQuery.data ?? [], [estimatesQuery.data]);

  const [selectedId, setSelectedId] = useState<string | undefined>();
  const selected = selectedId ?? estimates.find((e) => e.fitted)?.speciesId ?? estimates[0]?.speciesId;
  const detailQuery = useQuery({
    queryKey: ['rms-estimate', selected, estimatesQuery.dataUpdatedAt],
    queryFn: () => domainService.rmsEstimate(selected as string),
    enabled: !!selected,
  });

  // Al terminar un cálculo se refrescan los resultados.
  const lastFinished = runQuery.data?.status === 'DONE' ? runQuery.data.finishedAt : undefined;
  useEffect(() => {
    if (lastFinished) void queryClient.invalidateQueries({ queryKey: ['rms-estimates'] });
  }, [lastFinished, queryClient]);

  const runMutation = useMutation({
    mutationFn: domainService.requestRmsRun,
    onSuccess: (run) => queryClient.setQueryData(['rms-run'], run),
  });

  const [proposeOpen, setProposeOpen] = useState(false);
  const [decision, setDecision] = useState<{ proposal: RmsProposal; action: 'aprobar' | 'rechazar' } | null>(null);

  return (
    <>
      <PageHeader
        title="Rendimiento Máximo Sostenible"
        subtitle="Modelo de producción excedente de Schaefer ajustado con la captura y el esfuerzo de cada especie. Las cuotas propuestas no pueden superar el RMS."
        actionLabel={canCalculate ? (running ? 'Calculando…' : 'Recalcular') : undefined}
        actionIcon={<RefreshIcon />}
        onAction={canCalculate && !running ? () => runMutation.mutate() : undefined}
      />

      <RunBanner run={runQuery.data} error={runMutation.error ? toAppError(runMutation.error).message : null} />

      {estimatesQuery.isLoading && <CircularProgress size={28} />}
      {estimatesQuery.isError && <Alert severity="error">No fue posible cargar los resultados del RMS.</Alert>}
      {estimatesQuery.isSuccess && estimates.length === 0 && (
        <EmptyState
          icon={<ShowChartIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
          title="Todavía no hay cálculos de RMS"
          description="El cálculo usa las faenas cerradas de años completos y la serie histórica de cada especie."
          actionLabel={canCalculate && !running ? 'Calcular ahora' : undefined}
          onAction={canCalculate && !running ? () => runMutation.mutate() : undefined}
        />
      )}

      {estimates.length > 0 && (
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, lg: 5 }}>
            <EstimatesTable estimates={estimates} selected={selected} onSelect={setSelectedId} />
          </Grid>
          <Grid size={{ xs: 12, lg: 7 }}>
            {detailQuery.isLoading && <CircularProgress size={28} />}
            {detailQuery.data && (
              <EstimateDetail
                estimate={detailQuery.data}
                canPropose={canCalculate}
                onPropose={() => setProposeOpen(true)}
              />
            )}
          </Grid>
          <Grid size={12}>
            <ProposalsTable
              proposals={proposalsQuery.data ?? []}
              isAdmin={isAdmin}
              onDecide={(proposal, action) => setDecision({ proposal, action })}
            />
          </Grid>
        </Grid>
      )}

      {proposeOpen && detailQuery.data && (
        <ProposeDialog estimate={detailQuery.data} onClose={() => setProposeOpen(false)} />
      )}
      {decision && <DecisionDialog {...decision} onClose={() => setDecision(null)} />}
    </>
  );
}

function RunBanner({ run, error }: { run: Awaited<ReturnType<typeof domainService.latestRmsRun>> | undefined; error: string | null }) {
  if (error) return <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>;
  if (!run) return null;
  if (run.status === 'PENDING' || run.status === 'RUNNING') {
    return (
      <Alert severity="info" icon={<CircularProgress size={18} />} sx={{ mb: 2 }}>
        Calculando el RMS de todas las especies en segundo plano. Puedes seguir usando SIGEPES.
      </Alert>
    );
  }
  if (run.status === 'FAILED') {
    return <Alert severity="error" sx={{ mb: 2 }}>El último cálculo falló: {run.error}</Alert>;
  }
  return (
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
      Último cálculo: {run.finishedAt ? dateTime.format(new Date(run.finishedAt)) : '—'} · {run.speciesCount ?? 0} especies
      {run.durationMs != null ? ` en ${dec.format(run.durationMs / 1000)} s` : ''}
    </Typography>
  );
}

function StockChip({ status }: { status: StockStatus | null }) {
  if (!status) return <Chip size="small" variant="outlined" label="Sin ajuste" />;
  return <Chip size="small" color={STOCK[status].color} label={STOCK[status].label} />;
}

function EstimatesTable({ estimates, selected, onSelect }: {
  estimates: RmsEstimate[]; selected?: string; onSelect: (id: string) => void;
}) {
  return (
    <TableContainer component={Card}>
      <Table size="small" aria-label="RMS por especie">
        <TableHead>
          <TableRow>
            <TableCell>Especie</TableCell>
            <TableCell align="right">Años</TableCell>
            <TableCell align="right">RMS (kg)</TableCell>
            <TableCell>Estado</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {estimates.map((estimate) => (
            <TableRow
              key={estimate.speciesId}
              hover
              selected={estimate.speciesId === selected}
              onClick={() => onSelect(estimate.speciesId)}
              sx={{ cursor: 'pointer' }}
              tabIndex={0}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onSelect(estimate.speciesId); }}
              aria-selected={estimate.speciesId === selected}
            >
              <TableCell>
                <Typography fontWeight={700} variant="body2">{estimate.species ?? estimate.speciesId}</Typography>
                {estimate.indicative && estimate.fitted && <Chip size="small" label="Indicativo" color="warning" variant="outlined" sx={{ mt: 0.5 }} />}
              </TableCell>
              <TableCell align="right">{estimate.yearsCount}</TableCell>
              <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {estimate.msyKg != null ? kg.format(estimate.msyKg) : '—'}
              </TableCell>
              <TableCell><StockChip status={estimate.stockStatus} /></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function EstimateDetail({ estimate, canPropose, onPropose }: {
  estimate: RmsEstimate; canPropose: boolean; onPropose: () => void;
}) {
  const params: [string, string][] = estimate.fitted
    ? [
        ['RMS', `${kg.format(estimate.msyKg ?? 0)} kg/año`],
        ['Esfuerzo óptimo (f-RMS)', `${dec.format(estimate.fMsy ?? 0)} faenas/año`],
        ['Tasa de crecimiento (r)', dec.format(estimate.r ?? 0)],
        ['Capacidad de carga (K)', `${kg.format(estimate.kKg ?? 0)} kg`],
        ['Biomasa / B-RMS', dec.format(estimate.biomassRatio ?? 0)],
        ['Pesca / F-RMS', dec.format(estimate.fishingRatio ?? 0)],
        ['Ajuste (R²)', dec.format(estimate.r2 ?? 0)],
      ]
    : [];
  return (
    <Stack spacing={2}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={1} flexWrap="wrap" useFlexGap>
        <div>
          <Typography variant="h6" component="h2">{estimate.species}</Typography>
          <Typography variant="body2" color="text.secondary">
            <em>{estimate.scientificName}</em> · serie {estimate.firstYear}–{estimate.lastYear} ({estimate.yearsCount} años con datos)
          </Typography>
        </div>
        <Stack direction="row" spacing={1} alignItems="center">
          <StockChip status={estimate.stockStatus} />
          {canPropose && estimate.fitted && (
            <Button variant="contained" size="small" onClick={onPropose}>Proponer cuota</Button>
          )}
        </Stack>
      </Stack>
      {estimate.note && (
        <Alert severity={estimate.fitted ? 'warning' : 'info'}>{estimate.note}</Alert>
      )}
      {estimate.fitted && (
        <ChartCard
          title="Curva de producción"
          subtitle="Captura sostenible según el esfuerzo, con los años observados"
          fill={false}
          table={{
            columns: ['Año', 'Captura (kg)', 'Esfuerzo (faenas)', 'CPUE (kg/faena)', 'Fuente'],
            rows: estimate.series.map((p) => [p.year, kg.format(p.catchKg), kg.format(p.effort), dec.format(p.cpue),
              p.source === 'FAENAS' ? 'Faenas SIGEPES' : 'Histórico']),
          }}
        >
          <ProductionCurve estimate={estimate} />
        </ChartCard>
      )}
      {params.length > 0 && (
        <Card>
          <CardContent>
            <Grid container spacing={1.5}>
              {params.map(([label, value]) => (
                <Grid key={label} size={{ xs: 6, sm: 4 }}>
                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                  <Typography fontWeight={700} sx={{ fontVariantNumeric: 'tabular-nums' }}>{value}</Typography>
                </Grid>
              ))}
            </Grid>
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}

function ProposalsTable({ proposals, isAdmin, onDecide }: {
  proposals: RmsProposal[]; isAdmin: boolean; onDecide: (proposal: RmsProposal, action: 'aprobar' | 'rechazar') => void;
}) {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem', mb: 1.5 }}>Propuestas de cuota</Typography>
        {proposals.length === 0 ? (
          <Typography variant="body2" color="text.secondary">Todavía no hay propuestas basadas en el RMS.</Typography>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Propuestas de cuota basadas en el RMS">
              <TableHead>
                <TableRow>
                  <TableCell>Especie</TableCell>
                  <TableCell>Zona y periodo</TableCell>
                  <TableCell align="right">Propuesta (kg)</TableCell>
                  <TableCell align="right">RMS (kg)</TableCell>
                  <TableCell>Estado</TableCell>
                  <TableCell>Notas</TableCell>
                  {isAdmin && <TableCell align="right">Decisión</TableCell>}
                </TableRow>
              </TableHead>
              <TableBody>
                {proposals.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>
                      {p.species}
                      {p.indicative && <Chip size="small" label="Indicativo" color="warning" variant="outlined" sx={{ ml: 1 }} />}
                    </TableCell>
                    <TableCell>{p.zone} · {p.from} a {p.to}</TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>{kg.format(p.proposedKg)}</TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>{kg.format(p.msyKg)}</TableCell>
                    <TableCell><StatusChip status={p.status} /></TableCell>
                    <TableCell sx={{ maxWidth: 280 }}>
                      <Typography variant="body2" color="text.secondary">
                        {[p.justification, p.decisionNote].filter(Boolean).join(' · ') || '—'}
                      </Typography>
                    </TableCell>
                    {isAdmin && (
                      <TableCell align="right">
                        {p.status === 'PROPOSED' ? (
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button size="small" variant="contained" onClick={() => onDecide(p, 'aprobar')}>Aprobar</Button>
                            <Button size="small" color="error" onClick={() => onDecide(p, 'rechazar')}>Rechazar</Button>
                          </Stack>
                        ) : (p.quotaId ? <Typography variant="caption">Cuota {p.quotaId}</Typography> : '—')}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
}

function ProposeDialog({ estimate, onClose }: { estimate: RmsEstimate; onClose: () => void }) {
  const queryClient = useQueryClient();
  const msy = estimate.msyKg ?? 0;
  const start = new Date();
  start.setMonth(start.getMonth() + 1, 1);
  const end = new Date(start);
  end.setMonth(end.getMonth() + 6);
  // Fecha local: toISOString() pasa a UTC y, desde las 7 p. m. en Colombia, daba el día siguiente.
  const iso = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const [form, setForm] = useState({
    proposedKg: String(Math.floor(msy * 0.9)),
    zone: '',
    from: iso(start),
    to: iso(end),
    justification: '',
  });
  const value = Number(form.proposedKg);
  const overMsy = value > msy;
  const mutation = useMutation({
    mutationFn: () => domainService.proposeRmsQuota(estimate.speciesId, {
      proposedKg: value,
      zone: form.zone.trim(),
      from: form.from,
      to: form.to,
      justification: form.justification.trim() || undefined,
    }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['rms-proposals'] });
      onClose();
    },
  });
  const invalid = !(value > 0) || overMsy || !form.zone.trim() || !form.from || !form.to;
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Proponer cuota · {estimate.species}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Alert severity={estimate.indicative ? 'warning' : 'info'}>
            RMS de {kg.format(msy)} kg/año. La cuota no puede superarlo.
            {estimate.indicative && ' El cálculo es indicativo: para aprobarla el ADMIN necesitará una nota justificativa.'}
          </Alert>
          <TextField label="Cuota propuesta (kg)" type="number" value={form.proposedKg} required
            onChange={(e) => setForm({ ...form, proposedKg: e.target.value })}
            error={overMsy} helperText={overMsy ? 'Supera el RMS.' : `Máximo ${kg.format(msy)} kg`}
            slotProps={{ htmlInput: { min: 1, max: msy, step: 1 } }} />
          <TextField label="Zona" value={form.zone} required placeholder="Pacífico Norte"
            onChange={(e) => setForm({ ...form, zone: e.target.value })} />
          <Stack direction="row" spacing={2}>
            <TextField label="Desde" type="date" value={form.from} fullWidth required
              onChange={(e) => setForm({ ...form, from: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField label="Hasta" type="date" value={form.to} fullWidth required
              onChange={(e) => setForm({ ...form, to: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
          </Stack>
          <TextField label="Justificación" value={form.justification} multiline minRows={2}
            helperText={estimate.indicative ? 'Recomendada: el RMS es indicativo.' : 'Opcional'}
            onChange={(e) => setForm({ ...form, justification: e.target.value })}
            slotProps={{ htmlInput: { maxLength: 600 } }} />
          {mutation.error && <Alert severity="error">{toAppError(mutation.error).message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={invalid || mutation.isPending} onClick={() => mutation.mutate()}>
          Enviar propuesta
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function DecisionDialog({ proposal, action, onClose }: {
  proposal: RmsProposal; action: 'aprobar' | 'rechazar'; onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const needsNote = action === 'aprobar' && proposal.indicative && !proposal.justification;
  const mutation = useMutation({
    mutationFn: () => domainService.decideRmsProposal(proposal.id, action, note.trim() || undefined),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['rms-proposals'] });
      void queryClient.invalidateQueries({ queryKey: ['quotas'] });
      onClose();
    },
  });
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{action === 'aprobar' ? 'Aprobar' : 'Rechazar'} propuesta · {proposal.species}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Box>
            <Typography variant="body2">
              {kg.format(proposal.proposedKg)} kg en {proposal.zone}, del {proposal.from} al {proposal.to} (RMS {kg.format(proposal.msyKg)} kg).
            </Typography>
            {action === 'aprobar' && (
              <Typography variant="body2" color="text.secondary">Al aprobarla se crea la cuota en el módulo de cuotas.</Typography>
            )}
          </Box>
          {needsNote && (
            <Alert severity="warning">El RMS es indicativo (menos de 5 años de datos): escribe la nota justificativa.</Alert>
          )}
          <TextField label={needsNote ? 'Nota justificativa' : 'Nota (opcional)'} value={note} multiline minRows={2}
            required={needsNote} onChange={(e) => setNote(e.target.value)} slotProps={{ htmlInput: { maxLength: 400 } }} />
          {mutation.error && <Alert severity="error">{toAppError(mutation.error).message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" color={action === 'aprobar' ? 'primary' : 'error'}
          disabled={(needsNote && !note.trim()) || mutation.isPending} onClick={() => mutation.mutate()}>
          {action === 'aprobar' ? 'Aprobar y crear cuota' : 'Rechazar'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
