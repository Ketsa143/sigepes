import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EventOutlinedIcon from '@mui/icons-material/EventOutlined';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, MenuItem, Stack, Step, StepLabel, Stepper,
  TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { domainService, type SanctionKind, type SanctionProcess } from '../../../shared/api/domain.service';
import { toAppError } from '../../../shared/errors/AppError';
import { SANCTION_LABEL, STAGES } from '../sanctionLabels';

const date = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' });
const dateTime = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

const ACTION_LABEL: Record<string, string> = {
  OPENED: 'Apertura del proceso',
  ADVANCED: 'Cambio de etapa',
  DEFENSE_FILED: 'Descargos presentados',
  DECIDED: 'Decisión',
  APPEAL_FILED: 'Recurso presentado',
  CLOSED: 'Cierre',
};

const NEXT_LABEL: Partial<Record<SanctionProcess['stage'], string>> = {
  OPENED: 'Notificar y pasar a Descargos',
  DEFENSE: 'Pasar a Pruebas',
};

/** HU-23 · Expediente del proceso: etapas, plazos, actuaciones y las acciones que corresponden. */
export function SanctionDetailPage() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['sanction', id], queryFn: () => domainService.sanction(id), retry: false });
  const p = query.data;

  if (query.isLoading) return <CircularProgress size={28} />;
  if (query.isError || !p) {
    const error = query.error ? toAppError(query.error) : null;
    return <Alert severity={error?.status === 403 ? 'warning' : 'error'}>{error?.message ?? 'El proceso no existe.'}</Alert>;
  }
  const activeStep = Math.max(0, STAGES.findIndex((stage) => stage.key === p.stage));

  return (
    <>
      <Button component={RouterLink} to="/app/sanctions" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Procesos sancionatorios
      </Button>
      <Typography variant="h4" component="h1">Proceso {p.number}</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {p.infractionLabel} · {p.vesselName} ({p.vesselRegistration}) · investigado: {p.investigatedName}
      </Typography>

      <Card sx={{ mb: 2.5 }}>
        <CardContent>
          <Stepper activeStep={p.stage === 'CLOSED' ? STAGES.length : activeStep} alternativeLabel>
            {STAGES.filter((stage) => stage.key !== 'APPEAL' || p.appealSubmitted).map((stage) => (
              <Step key={stage.key}>
                <StepLabel optional={<Typography variant="caption" color="text.secondary">{stage.hint}</Typography>}>
                  {stage.label}
                </StepLabel>
              </Step>
            ))}
          </Stepper>
          {p.stageDeadline && p.stage !== 'CLOSED' && (
            <Alert severity={p.overdue ? 'error' : 'info'} icon={<EventOutlinedIcon />} sx={{ mt: 2 }}>
              {p.stage === 'OPENED' || p.stage === 'DEFENSE'
                ? `Plazo de descargos: hasta el ${date.format(new Date(`${p.stageDeadline}T12:00:00`))}`
                : p.stage === 'EVIDENCE'
                  ? `Etapa de pruebas: hasta el ${date.format(new Date(`${p.stageDeadline}T12:00:00`))}`
                  : `Plazo para recurrir la decisión: hasta el ${date.format(new Date(`${p.stageDeadline}T12:00:00`))}`}
              {p.overdue ? ' (vencido)' : ''}
              {p.stage !== 'EVIDENCE' && (p.stage === 'DECISION' ? (p.appealSubmitted ? ' · recurso presentado' : '')
                : p.defenseSubmitted ? ' · descargos presentados' : ' · sin descargos todavía')}
            </Alert>
          )}
        </CardContent>
      </Card>

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Stack spacing={2.5}>
            <Card>
              <CardContent>
                <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Hechos</Typography>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mb: 1.5 }}>{p.facts}</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                  {p.inspectionId && (
                    <Chip component={RouterLink} to={`/app/inspections/${p.inspectionId}`} clickable
                      label={`Origen: acta ${p.inspectionCode ?? ''}`} />
                  )}
                  {p.reportCode && <Chip label={`Origen: denuncia ${p.reportCode}`} />}
                  <Chip variant="outlined" label={`Abierto por ${p.openedBy} el ${dateTime.format(new Date(p.openedAt))}`} />
                </Stack>
                {p.sanction && (
                  <Alert severity={p.sanction === 'DISMISSED' ? 'success' : 'warning'} sx={{ mt: 2 }}>
                    <strong>Decisión: {p.sanctionLabel}{p.fineSmmlv != null ? ` de ${p.fineSmmlv} SMMLV` : ''}.</strong>{' '}
                    {p.decisionText}
                  </Alert>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardContent>
                <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Actuaciones</Typography>
                <Stack component="ol" spacing={1.5} sx={{ m: 0, pl: 2.5 }}>
                  {p.actions.map((action) => (
                    <li key={action.sequence}>
                      <Typography variant="body2" fontWeight={700}>
                        {ACTION_LABEL[action.kind] ?? action.kind}
                        {action.kind === 'ADVANCED' || action.kind === 'CLOSED' ? `: ${action.stageFrom} → ${action.stageTo}` : ''}
                        {' · '}{action.actorName}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">{dateTime.format(new Date(action.occurredAt))}</Typography>
                      {action.note && <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{action.note}</Typography>}
                    </li>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
        <Grid size={{ xs: 12, md: 5 }}>
          <ActionsPanel process={p} />
        </Grid>
      </Grid>
    </>
  );
}

function ActionsPanel({ process: p }: { process: SanctionProcess }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const [text, setText] = useState('');
  const [sanction, setSanction] = useState<SanctionKind | ''>('');
  const [fine, setFine] = useState('');
  const done = (updated: SanctionProcess) => {
    queryClient.setQueryData(['sanction', p.id], updated);
    void queryClient.invalidateQueries({ queryKey: ['sanctions'] });
    setNote('');
    setText('');
    setSanction('');
    setFine('');
  };
  const step = useMutation({ mutationFn: (kind: 'avanzar' | 'cierre') => domainService.sanctionStep(p.id, kind, note.trim()), onSuccess: done });
  const decide = useMutation({
    mutationFn: () => domainService.decideSanction(p.id, {
      sanction: sanction as SanctionKind, fineSmmlv: sanction === 'FINE' ? Number(fine) : undefined, text: text.trim(),
    }),
    onSuccess: done,
  });
  const file = useMutation({ mutationFn: (kind: 'descargos' | 'recurso') => domainService.fileSanction(p.id, kind, text.trim()), onSuccess: done });
  const error = step.error ?? decide.error ?? file.error;
  const allowed = p.allowedActions;

  if (allowed.length === 0) {
    return (
      <Card>
        <CardContent>
          <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Siguiente paso</Typography>
          <Typography variant="body2" color="text.secondary">
            {p.stage === 'CLOSED'
              ? 'El proceso está cerrado.'
              : p.stage === 'DEFENSE' && !p.defenseSubmitted
                ? 'El paso a pruebas se habilita al presentarse los descargos o al vencer su plazo.'
                : p.stage === 'DECISION'
                  ? 'El proceso se cierra al vencer el plazo de recurso sin que se presente.'
                  : 'No tienes acciones pendientes en esta etapa.'}
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>Siguiente paso</Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{toAppError(error).message}</Alert>}
        <Stack spacing={2}>
          {(allowed.includes('ADVANCE') || allowed.includes('CLOSE')) && (
            <>
              <TextField label={p.stage === 'APPEAL' ? 'Resolución del recurso' : 'Anotación (opcional)'} value={note}
                multiline minRows={2} onChange={(e) => setNote(e.target.value)} required={p.stage === 'APPEAL'}
                slotProps={{ htmlInput: { maxLength: 1500 } }} />
              {allowed.includes('ADVANCE') && (
                <Button variant="contained" disabled={step.isPending} onClick={() => step.mutate('avanzar')}>
                  {NEXT_LABEL[p.stage] ?? 'Avanzar'}
                </Button>
              )}
              {allowed.includes('CLOSE') && (
                <Button variant="contained" disabled={step.isPending || (p.stage === 'APPEAL' && !note.trim())}
                  onClick={() => step.mutate('cierre')}>
                  {p.stage === 'APPEAL' ? 'Resolver recurso y cerrar' : 'Cerrar: decisión en firme'}
                </Button>
              )}
            </>
          )}
          {allowed.includes('DECIDE') && (
            <>
              <TextField select label="Decisión" value={sanction} onChange={(e) => setSanction(e.target.value as SanctionKind)}>
                {(Object.keys(SANCTION_LABEL) as SanctionKind[]).map((key) => (
                  <MenuItem key={key} value={key}>{SANCTION_LABEL[key]}</MenuItem>
                ))}
              </TextField>
              {sanction === 'FINE' && (
                <TextField label="Multa (salarios mínimos, SMMLV)" type="number" value={fine} required
                  onChange={(e) => setFine(e.target.value)} slotProps={{ htmlInput: { min: 0.01, step: 0.5 } }} />
              )}
              {sanction === 'PERMIT_SUSPENSION' && (
                <Alert severity="warning">Los permisos aprobados del investigado pasarán a «Suspendido».</Alert>
              )}
              <TextField label="Motivación" value={text} multiline minRows={3} required onChange={(e) => setText(e.target.value)}
                slotProps={{ htmlInput: { maxLength: 1500 } }} />
              <Button variant="contained" onClick={() => decide.mutate()}
                disabled={decide.isPending || !sanction || !text.trim() || (sanction === 'FINE' && !(Number(fine) > 0))}>
                Registrar decisión
              </Button>
            </>
          )}
          {(allowed.includes('FILE_DEFENSE') || allowed.includes('FILE_APPEAL')) && (
            <>
              <Typography variant="body2" color="text.secondary">
                {allowed.includes('FILE_DEFENSE')
                  ? 'Presenta tus descargos: tu versión de los hechos y las pruebas que quieras aportar.'
                  : 'Puedes recurrir la decisión explicando por qué no estás de acuerdo.'}
              </Typography>
              <TextField label={allowed.includes('FILE_DEFENSE') ? 'Descargos' : 'Recurso'} value={text} multiline minRows={4}
                required onChange={(e) => setText(e.target.value)} slotProps={{ htmlInput: { maxLength: 1500 } }} />
              <Box>
                <Button variant="contained" disabled={file.isPending || !text.trim()}
                  onClick={() => file.mutate(allowed.includes('FILE_DEFENSE') ? 'descargos' : 'recurso')}>
                  {allowed.includes('FILE_DEFENSE') ? 'Presentar descargos' : 'Presentar recurso'}
                </Button>
              </Box>
            </>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
