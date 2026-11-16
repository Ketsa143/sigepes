import BalanceIcon from '@mui/icons-material/Balance';
import {
  Alert, Button, Card, Chip, CircularProgress, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import { domainService } from '../../../shared/api/domain.service';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useAuthStore } from '../../auth/store/authStore';

const date = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' });

/** HU-23 · Procesos sancionatorios: la autoridad ve todos; el investigado, los suyos. */
export function SanctionsPage() {
  const role = useAuthStore((s) => s.user?.role);
  const manager = role === 'INSPECTOR' || role === 'ADMIN';
  const query = useQuery({ queryKey: ['sanctions'], queryFn: domainService.sanctions });
  const processes = query.data ?? [];

  return (
    <>
      <PageHeader
        title="Procesos sancionatorios"
        subtitle={manager
          ? 'Expedientes abiertos a partir de actas con hallazgos o de denuncias fundadas, con sus etapas y plazos en días hábiles. Se abren desde el acta o la denuncia.'
          : 'Procesos en los que eres investigado. Aquí ves la etapa, los plazos y puedes presentar descargos o recurso.'}
      />
      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {query.isSuccess && processes.length === 0 && (
        <EmptyState
          icon={<BalanceIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
          title="No hay procesos"
          description={manager ? 'Abre uno desde un acta con hallazgos o desde una denuncia cerrada como fundada.' : 'No tienes procesos sancionatorios.'}
        />
      )}
      {processes.length > 0 && (
        <TableContainer component={Card}>
          <Table aria-label="Procesos sancionatorios">
            <TableHead>
              <TableRow>
                <TableCell>Número</TableCell>
                <TableCell>Embarcación</TableCell>
                <TableCell>Infracción</TableCell>
                <TableCell>Etapa</TableCell>
                <TableCell>Plazo</TableCell>
                <TableCell>Apertura</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {processes.map((p) => (
                <TableRow key={p.id} hover>
                  <TableCell>
                    <Button component={RouterLink} to={`/app/sanctions/${p.id}`} size="small" sx={{ fontWeight: 700 }}>
                      {p.number}
                    </Button>
                  </TableCell>
                  <TableCell>
                    {p.vesselName}
                    <Typography variant="caption" color="text.secondary" display="block">{p.investigatedName}</Typography>
                  </TableCell>
                  <TableCell>{p.infractionLabel}</TableCell>
                  <TableCell>
                    <Chip size="small" label={p.stageLabel} color={p.stage === 'CLOSED' ? 'default' : 'primary'}
                      variant={p.stage === 'CLOSED' ? 'outlined' : 'filled'} />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {p.stageDeadline ? (
                      <Typography variant="body2" color={p.overdue ? 'error' : 'text.primary'} fontWeight={p.overdue ? 700 : 400}>
                        {date.format(new Date(`${p.stageDeadline}T12:00:00`))}{p.overdue ? ' · vencido' : ''}
                      </Typography>
                    ) : '—'}
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{date.format(new Date(p.openedAt))}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </>
  );
}
