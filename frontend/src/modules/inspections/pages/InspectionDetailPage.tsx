import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PictureAsPdfOutlinedIcon from '@mui/icons-material/PictureAsPdfOutlined';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, Table, TableBody, TableCell,
  TableHead, TableRow, Typography,
} from '@mui/material';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import BalanceIcon from '@mui/icons-material/Balance';
import { Link as RouterLink, useLocation, useParams } from 'react-router-dom';
import { domainService } from '../../../shared/api/domain.service';
import { toAppError } from '../../../shared/errors/AppError';
import { saveBlob } from '../../../shared/files/saveBlob';
import { useAuthStore } from '../../auth/store/authStore';
import { OpenSanctionDialog } from '../../sanctions/components/OpenSanctionDialog';

const time = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short' });

/** HU-22 · Acta firmada: lo revisado, los hallazgos, el sello SHA-256 y la descarga en PDF. */
export function InspectionDetailPage() {
  const { id = '' } = useParams();
  const location = useLocation();
  const created = (location.state as { created?: boolean } | null)?.created;
  const query = useQuery({ queryKey: ['inspection', id], queryFn: () => domainService.inspection(id), retry: false });
  const pdf = useMutation({
    mutationFn: () => domainService.inspectionPdf(id),
    onSuccess: (blob) => saveBlob(blob, `acta-${query.data?.code ?? id}.pdf`),
  });
  const act = query.data;
  const role = useAuthStore((s) => s.user?.role);
  const manager = role === 'INSPECTOR' || role === 'ADMIN';
  const [opening, setOpening] = useState(false);
  // HU-23: un acta con hallazgos da lugar a un proceso sancionatorio (uno por acta).
  const processQuery = useQuery({
    queryKey: ['sanction-for-inspection', id],
    queryFn: () => domainService.sanctionForInspection(id),
    enabled: manager && act?.result === 'WITH_FINDINGS',
  });

  if (query.isLoading) return <CircularProgress size={28} />;
  if (query.isError || !act) {
    return <Alert severity="error">{query.error ? toAppError(query.error).message : 'El acta no existe.'}</Alert>;
  }
  const conform = act.result === 'COMPLIANT';

  return (
    <>
      <Button component={RouterLink} to="/app/inspections" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Actas de inspección
      </Button>
      {created && <Alert severity="success" sx={{ mb: 2 }}>Acta {act.code} registrada y sellada.</Alert>}
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }}
        spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" component="h1">Acta {act.code}</Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1 }} flexWrap="wrap" useFlexGap>
            <Chip color={conform ? 'success' : 'error'}
              label={conform ? 'Conforme' : `Con hallazgos (${act.findings.length})`} />
            <Chip variant="outlined" icon={<LockOutlinedIcon />} label="Firmada e inmutable" />
            {act.savedOffline && <Chip variant="outlined" label="Levantada sin señal" />}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {manager && !conform && processQuery.isSuccess && (processQuery.data ? (
            <Button variant="outlined" startIcon={<BalanceIcon />} component={RouterLink} to={`/app/sanctions/${processQuery.data}`}>
              Ver proceso sancionatorio
            </Button>
          ) : (
            <Button variant="outlined" color="error" startIcon={<BalanceIcon />} onClick={() => setOpening(true)}>
              Abrir proceso sancionatorio
            </Button>
          ))}
          <Button variant="contained" startIcon={<PictureAsPdfOutlinedIcon />} disabled={pdf.isPending}
            onClick={() => pdf.mutate()}>
            {pdf.isPending ? 'Generando…' : 'Descargar PDF'}
          </Button>
        </Stack>
      </Stack>
      {opening && <OpenSanctionDialog inspectionId={act.id} origin={`el acta ${act.code}`} onClose={() => setOpening(false)} />}
      {pdf.isError && <Alert severity="error" sx={{ mb: 2 }}>{toAppError(pdf.error).message}</Alert>}

      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 5 }}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>Datos del acta</Typography>
              {[
                ['Embarcación', `${act.vesselName} · ${act.vesselRegistration}`],
                ['Armador', act.ownerName],
                ['Permiso vigente', act.permitNumber ?? 'Ninguno en la fecha'],
                ['Fecha', time.format(new Date(act.inspectedAt))],
                ['Lugar', act.locationText + (act.latitude != null ? ` (${Number(act.latitude).toFixed(5)}, ${Number(act.longitude).toFixed(5)})` : '')],
                ['Inspector', act.inspectorName],
                ['Responsable a bordo', act.responsibleName],
                ['Fotos', String(act.photos)],
              ].map(([label, value]) => (
                <Box key={label} sx={{ mb: 1 }}>
                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                  <Typography variant="body2" fontWeight={600}>{value}</Typography>
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 7 }}>
          <Stack spacing={2.5}>
            <Card>
              <CardContent>
                <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Hallazgos</Typography>
                {act.findings.length === 0 && <Typography variant="body2">Sin hallazgos.</Typography>}
                <Stack spacing={1}>
                  {act.findings.map((finding) => (
                    <Alert key={finding.id} severity="error" icon={false}>
                      <strong>{finding.label}.</strong> {finding.description}
                      {!finding.automatic && ' (anotado por el inspector)'}
                    </Alert>
                  ))}
                </Stack>
                {act.observations && (
                  <Typography variant="body2" sx={{ mt: 2 }}><strong>Observaciones:</strong> {act.observations}</Typography>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardContent>
                <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Revisión a bordo</Typography>
                <Table size="small" aria-label="Capturas y artes revisadas">
                  <TableHead>
                    <TableRow>
                      <TableCell>Especie o arte</TableCell>
                      <TableCell align="right">Talla (cm)</TableCell>
                      <TableCell align="right">Mínima</TableCell>
                      <TableCell align="right">Peso (kg)</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {act.items.map((item, index) => (
                      <TableRow key={index} sx={item.finding ? { '& td': { color: 'error.main', fontWeight: 600 } } : undefined}>
                        <TableCell>{item.kind === 'GEAR' ? `Arte: ${item.label}` : item.label}</TableCell>
                        <TableCell align="right">{item.sizeCm ?? '—'}</TableCell>
                        <TableCell align="right">{item.minSizeCm ?? '—'}</TableCell>
                        <TableCell align="right">{item.weightKg ?? '—'}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
        <Grid size={12}>
          <Alert severity="info" icon={<LockOutlinedIcon />}>
            Sello de integridad SHA-256: <Box component="code" sx={{ wordBreak: 'break-all' }}>{act.contentHash}</Box>.
            Cualquiera puede verificarla en{' '}
            <RouterLink to={`/verificar-acta/${act.code}`}>/verificar-acta/{act.code}</RouterLink>.
          </Alert>
        </Grid>
      </Grid>
    </>
  );
}
