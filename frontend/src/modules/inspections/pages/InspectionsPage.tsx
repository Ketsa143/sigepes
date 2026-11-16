import AddIcon from '@mui/icons-material/Add';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined';
import {
  Alert, Button, Card, CardContent, Chip, CircularProgress, IconButton, List, ListItem, ListItemText, Stack, Table,
  TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import { domainService } from '../../../shared/api/domain.service';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useOnlineStatus } from '../../../shared/hooks/useOnlineStatus';
import {
  INSPECTION_OUTBOX_EVENT, queuedInspections, removeQueuedInspection, type QueuedInspection,
} from '../../../shared/storage/inspectionOutbox';
import { flushInspectionOutbox } from '../../../shared/sync/inspectionSync';
import { useAuthStore } from '../../auth/store/authStore';

const time = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

/** HU-22 · Actas de inspección: la autoridad ve todas y la cola sin señal; el armador, las suyas. */
export function InspectionsPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const online = useOnlineStatus();
  const role = useAuthStore((s) => s.user?.role);
  const canInspect = role === 'INSPECTOR' || role === 'ADMIN';
  const queuedFor = (location.state as { queued?: string } | null)?.queued;

  const query = useQuery({ queryKey: ['inspections'], queryFn: () => domainService.inspections() });
  const [queue, setQueue] = useState<QueuedInspection[]>([]);
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    const refresh = () => void queuedInspections().then(setQueue);
    refresh();
    window.addEventListener(INSPECTION_OUTBOX_EVENT, refresh);
    return () => window.removeEventListener(INSPECTION_OUTBOX_EVENT, refresh);
  }, []);

  const sync = async () => {
    setSyncing(true);
    try {
      const sent = await flushInspectionOutbox();
      if (sent > 0) await queryClient.invalidateQueries({ queryKey: ['inspections'] });
    } finally {
      setSyncing(false);
    }
  };

  const inspections = query.data ?? [];

  return (
    <>
      <PageHeader
        title="Actas de inspección"
        subtitle={canInspect
          ? 'Actas levantadas en campo, firmadas e inmutables. Las que se firmaron sin señal esperan aquí hasta sincronizarse.'
          : 'Actas de inspección levantadas a tus embarcaciones. Puedes descargarlas en PDF.'}
        actionLabel={canInspect ? 'Nueva acta' : undefined}
        actionIcon={<AddIcon />}
        onAction={canInspect ? () => void navigate('/app/inspections/new') : undefined}
      />

      {queuedFor && queue.length > 0 && (
        <Alert severity="info" icon={<CloudOffOutlinedIcon />} sx={{ mb: 2 }}>
          El acta de «{queuedFor}» quedó guardada en este dispositivo y se enviará sola al recuperar la señal.
        </Alert>
      )}

      {queue.length > 0 && (
        <Card sx={{ mb: 2.5, borderLeft: 4, borderColor: 'warning.main' }}>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem' }}>
                Pendientes de sincronizar ({queue.length})
              </Typography>
              <Button size="small" variant="outlined" startIcon={<CloudUploadOutlinedIcon />}
                disabled={!online || syncing} onClick={() => void sync()}>
                {syncing ? 'Enviando…' : online ? 'Enviar ahora' : 'Sin señal'}
              </Button>
            </Stack>
            <List dense>
              {queue.map((item) => (
                <ListItem key={item.clientId} divider
                  secondaryAction={item.rejection ? (
                    <IconButton edge="end" aria-label={`Descartar el acta de ${item.vesselName}`}
                      onClick={() => void removeQueuedInspection(item.clientId)}>
                      <DeleteOutlineIcon />
                    </IconButton>
                  ) : undefined}>
                  <ListItemText
                    primary={`${item.vesselName} · ${item.payload.locationText}`}
                    secondary={item.rejection
                      ? `Rechazada por el servidor: ${item.rejection}`
                      : `Firmada el ${time.format(new Date(item.payload.inspectedAt))} · se enviará al volver la señal`}
                    slotProps={{ secondary: { color: item.rejection ? 'error' : 'text.secondary' } }}
                  />
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      )}

      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {query.isSuccess && inspections.length === 0 && (
        <EmptyState
          icon={<FactCheckOutlinedIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
          title="Todavía no hay actas"
          description={canInspect ? 'Levanta la primera desde el botón «Nueva acta».' : 'No se han levantado actas a tus embarcaciones.'}
        />
      )}
      {inspections.length > 0 && (
        <TableContainer component={Card}>
          <Table aria-label="Actas de inspección">
            <TableHead>
              <TableRow>
                <TableCell>Código</TableCell>
                <TableCell>Embarcación</TableCell>
                <TableCell>Fecha</TableCell>
                <TableCell>Lugar</TableCell>
                <TableCell>Resultado</TableCell>
                <TableCell>Inspector</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {inspections.map((act) => (
                <TableRow key={act.id} hover>
                  <TableCell>
                    <Button component={RouterLink} to={`/app/inspections/${act.id}`} size="small" sx={{ fontWeight: 700 }}>
                      {act.code}
                    </Button>
                  </TableCell>
                  <TableCell>{act.vesselName}<Typography variant="caption" color="text.secondary" display="block">{act.vesselRegistration}</Typography></TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{time.format(new Date(act.inspectedAt))}</TableCell>
                  <TableCell>{act.locationText}</TableCell>
                  <TableCell>
                    <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                      {act.result === 'COMPLIANT'
                        ? <Chip size="small" color="success" label="Conforme" />
                        : <Chip size="small" color="error" label={`${act.findings} hallazgo${act.findings === 1 ? '' : 's'}`} />}
                      {act.savedOffline && <Chip size="small" variant="outlined" icon={<CloudOffOutlinedIcon />} label="Sin señal" />}
                    </Stack>
                  </TableCell>
                  <TableCell>{act.inspectorName}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </>
  );
}
