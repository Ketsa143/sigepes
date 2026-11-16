import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { Box, Button, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { Footer } from '../../../shared/components/Footer';
import { Logotype } from '../../../shared/components/Logotype';
import { SkipLink } from '../../../shared/components/SkipLink';
import { TopUtilityBar } from '../../../shared/components/TopUtilityBar';
import { glass } from '../../../shared/theme/theme';
import { MarketCatalog } from '../components/MarketCatalog';

/** HU-16 · AC-16.2 · Catálogo público de producto pesquero legal, sin iniciar sesión. */
export function PublicMarketPage() {
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <SkipLink />
      <TopUtilityBar />
      <Box component="header" sx={{ ...glass.smoked, px: { xs: 2, sm: 4 }, py: 1.5 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ maxWidth: 1120, mx: 'auto' }}>
          <RouterLink to="/" aria-label="Ir a la página de inicio de SIGEPES" style={{ textDecoration: 'none' }}>
            <Logotype variant="light" />
          </RouterLink>
          <Button component={RouterLink} to="/" startIcon={<ArrowBackIcon />} sx={{ color: '#fff' }}>Inicio</Button>
        </Stack>
      </Box>
      <Box component="main" id="main-content" sx={{ flex: 1, px: { xs: 2, sm: 4 }, py: { xs: 4, md: 6 } }}>
        <Box sx={{ maxWidth: 1120, mx: 'auto' }}>
          <Typography variant="h3" component="h1" sx={{ fontSize: { xs: '2rem', md: '2.5rem' }, mb: 1 }}>
            Pescado con origen legal
          </Typography>
          <Typography color="text.secondary" sx={{ mb: 3, maxWidth: 760 }}>
            Ofertas de pescadores artesanales con lotes trazables: cada una enlaza a la verificación pública de su origen y
            su cadena de custodia. Inicia sesión para enviar una solicitud de compra.
          </Typography>
          <MarketCatalog publicMode />
        </Box>
      </Box>
      <Footer />
    </Box>
  );
}
