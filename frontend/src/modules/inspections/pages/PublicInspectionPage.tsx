import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import GppBadOutlinedIcon from '@mui/icons-material/GppBadOutlined';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Stack, TextField, Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { domainService } from '../../../shared/api/domain.service';
import { Footer } from '../../../shared/components/Footer';
import { Logotype } from '../../../shared/components/Logotype';
import { SkipLink } from '../../../shared/components/SkipLink';
import { TopUtilityBar } from '../../../shared/components/TopUtilityBar';
import { toAppError } from '../../../shared/errors/AppError';
import { glass } from '../../../shared/theme/theme';

const time = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeStyle: 'short' });

/** HU-22 · AC-22.3 · Verificación pública del acta por el código impreso en el PDF. */
export function PublicInspectionPage() {
  const { code } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(code ?? '');
  const query = useQuery({
    queryKey: ['public-inspection', code],
    queryFn: () => domainService.verifyInspection(code as string),
    enabled: !!code,
    retry: false,
  });
  const error = query.error ? toAppError(query.error) : null;
  const act = query.data;

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <SkipLink />
      <TopUtilityBar />
      <Box component="header" sx={{ ...glass.smoked, px: { xs: 2, sm: 4 }, py: 1.5 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ maxWidth: 880, mx: 'auto' }}>
          <RouterLink to="/" aria-label="Ir a la página de inicio de SIGEPES" style={{ textDecoration: 'none' }}>
            <Logotype variant="light" />
          </RouterLink>
          <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} sx={{ color: '#fff' }}>Inicio</Button>
        </Stack>
      </Box>
      <Box component="main" id="main-content" sx={{ flex: 1, px: { xs: 2, sm: 4 }, py: { xs: 4, md: 6 } }}>
        <Box sx={{ maxWidth: 880, mx: 'auto' }}>
          <Typography variant="h3" component="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1 }}>
            Verificar un acta de inspección
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 3 }}>
            Escribe el código impreso en el acta (por ejemplo ACT-7K2M9QX4) para comprobar que es auténtica y que no fue
            modificada.
          </Typography>
          <Stack component="form" direction="row" spacing={1} sx={{ mb: 3 }}
            onSubmit={(e) => { e.preventDefault(); if (input.trim()) void navigate(`/verificar-acta/${input.trim().toUpperCase()}`); }}>
            <TextField label="Código del acta" value={input} onChange={(e) => setInput(e.target.value)} size="small" />
            <Button type="submit" variant="contained">Verificar</Button>
          </Stack>

          {query.isLoading && <CircularProgress size={28} />}
          {error && (
            <Alert severity={error.status === 404 ? 'warning' : 'error'} role="alert">
              {error.status === 404 ? 'No existe un acta con ese código.' : error.message}
            </Alert>
          )}
          {act && (
            <Card>
              <CardContent>
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2 }}>
                  {act.integrityOk
                    ? <VerifiedOutlinedIcon color="success" sx={{ fontSize: 40 }} />
                    : <GppBadOutlinedIcon color="error" sx={{ fontSize: 40 }} />}
                  <Box>
                    <Typography variant="h6" component="h2">
                      {act.integrityOk ? 'Acta auténtica e íntegra' : 'El acta fue alterada'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {act.integrityOk
                        ? 'El sello SHA-256 coincide con lo firmado en campo.'
                        : 'El contenido guardado ya no coincide con el sello: no la tome como válida y avise a la autoridad.'}
                    </Typography>
                  </Box>
                </Stack>
                <Typography variant="body2"><strong>Código:</strong> {act.code}</Typography>
                <Typography variant="body2"><strong>Embarcación:</strong> {act.vesselName} · {act.vesselRegistration}</Typography>
                <Typography variant="body2"><strong>Fecha:</strong> {time.format(new Date(act.inspectedAt))}</Typography>
                <Typography variant="body2"><strong>Lugar:</strong> {act.locationText}</Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1.5 }} flexWrap="wrap" useFlexGap>
                  {act.result === 'COMPLIANT'
                    ? <Chip color="success" label="Conforme" />
                    : act.findings.map((finding, index) => <Chip key={index} color="error" variant="outlined" label={finding} />)}
                </Stack>
              </CardContent>
            </Card>
          )}
        </Box>
      </Box>
      <Footer />
    </Box>
  );
}
