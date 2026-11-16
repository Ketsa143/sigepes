import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import { Alert, Box, Button, Card, CardContent, CircularProgress, Stack, TextField, Typography } from '@mui/material';
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

const date = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' });

/** HU-19 · AC-19.3 · Verificación pública de una constancia de curso por su código. */
export function PublicCertificatePage() {
  const { code } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(code ?? '');
  const query = useQuery({
    queryKey: ['public-certificate', code],
    queryFn: () => domainService.verifyCertificate(code as string),
    enabled: !!code,
    retry: false,
  });
  const error = query.error ? toAppError(query.error) : null;

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
            Verificar una constancia
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 3 }}>Escriba el código impreso en la constancia (por ejemplo CST-7K2M9QX4).</Typography>
          <Stack component="form" direction="row" spacing={1} sx={{ mb: 3 }}
            onSubmit={(e) => { e.preventDefault(); if (input.trim()) void navigate(`/verificar-constancia/${input.trim().toUpperCase()}`); }}>
            <TextField label="Código" size="small" value={input} onChange={(e) => setInput(e.target.value)} />
            <Button type="submit" variant="contained">Verificar</Button>
          </Stack>
          {query.isLoading && <CircularProgress size={28} />}
          {error && (
            <Alert severity={error.status === 404 ? 'warning' : 'error'}>
              {error.status === 404 ? 'No existe una constancia con ese código.' : error.message}
            </Alert>
          )}
          {query.data && (
            <Card>
              <CardContent>
                <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1.5 }}>
                  <VerifiedOutlinedIcon color="success" sx={{ fontSize: 40 }} />
                  <Typography variant="h6" component="h2">Constancia válida</Typography>
                </Stack>
                <Typography><strong>{query.data.holderName}</strong> aprobó el curso «{query.data.courseTitle}»</Typography>
                <Typography color="text.secondary">
                  con {query.data.score} % el {date.format(new Date(query.data.issuedAt))} · código {query.data.code}
                </Typography>
              </CardContent>
            </Card>
          )}
        </Box>
      </Box>
      <Footer />
    </Box>
  );
}
