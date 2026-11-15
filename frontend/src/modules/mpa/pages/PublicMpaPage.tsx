import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ParkIcon from '@mui/icons-material/Park';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Grid, Stack, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { domainService, type MarineProtectedArea } from '../../../shared/api/domain.service';
import { Footer } from '../../../shared/components/Footer';
import { GeoMap } from '../../../shared/components/GeoMap';
import { Logotype } from '../../../shared/components/Logotype';
import { SkipLink } from '../../../shared/components/SkipLink';
import { TopUtilityBar } from '../../../shared/components/TopUtilityBar';
import { toAppError } from '../../../shared/errors/AppError';
import { MPA_CATEGORY_LABEL, MPA_ZONE_KIND_LABEL, mpaZonesForMap } from '../../../shared/geo/geo';
import { glass } from '../../../shared/theme/theme';

/** Zoom con el que se pide la capa general: suficiente para la costa colombiana (R4). */
const OVERVIEW_ZOOM = 7;

const KIND_RULE = {
  INTANGIBLE: 'No se permite ninguna actividad de pesca.',
  SUSTAINABLE_USE: 'Pesca permitida solo con las artes y especies autorizadas.',
  BUFFER: 'Zona de transición, sin restricción de arte ni especie.',
} as const;

/**
 * HU-13 · TASK-13.3 · Capa de Áreas Marinas Protegidas y ficha pública de cada área. No exige
 * cuenta: cualquier pescador, comprador o ciudadano puede consultar dónde y cómo se puede pescar.
 */
export function PublicMpaPage() {
  const { id } = useParams();
  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <SkipLink />
      <TopUtilityBar />
      <Box component="header" sx={{ ...glass.smoked, px: { xs: 2, sm: 4 }, py: 1.5 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ maxWidth: 1120, mx: 'auto' }}>
          <RouterLink to="/" aria-label="Ir a la página de inicio de SIGEPES" style={{ textDecoration: 'none' }}>
            <Logotype variant="light" />
          </RouterLink>
          <Button component={RouterLink} to={id ? '/areas-protegidas' : '/'} startIcon={<ArrowBackIcon />}
            sx={{ color: '#fff', whiteSpace: 'nowrap', flexShrink: 0 }}>
            {id ? 'Todas las áreas' : 'Inicio'}
          </Button>
        </Stack>
      </Box>
      <Box component="main" id="main-content" sx={{ flex: 1, px: { xs: 2, sm: 4 }, py: { xs: 4, md: 6 } }}>
        <Box sx={{ maxWidth: 1120, mx: 'auto' }}>{id ? <MpaDetail id={id} /> : <MpaOverview />}</Box>
      </Box>
      <Footer />
    </Box>
  );
}

function MpaOverview() {
  const query = useQuery({
    queryKey: ['public-mpas', OVERVIEW_ZOOM],
    queryFn: () => domainService.marineProtectedAreas(OVERVIEW_ZOOM),
    staleTime: 5 * 60_000,
  });
  const areas = useMemo(() => query.data ?? [], [query.data]);
  const zones = useMemo(() => mpaZonesForMap(areas), [areas]);

  return (
    <>
      <Typography variant="h3" component="h1" sx={{ fontSize: { xs: '2rem', md: '2.5rem' }, mb: 1 }}>
        Áreas Marinas Protegidas
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3, maxWidth: 720 }}>
        Consulta dónde están las áreas protegidas y qué se puede hacer en cada zona antes de salir a faenar. En la
        zona intangible no se pesca; en la de uso sostenible solo con las artes y especies autorizadas.
      </Typography>
      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {query.isSuccess && areas.length === 0 && (
        <Alert severity="info">La autoridad todavía no ha publicado Áreas Marinas Protegidas.</Alert>
      )}
      {areas.length > 0 && (
        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Card>
              <CardContent>
                <GeoMap title="Mapa de Áreas Marinas Protegidas" zones={zones} height={460} />
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={1.5} component="ul" aria-label="Listado de áreas protegidas" sx={{ listStyle: 'none', p: 0, m: 0 }}>
              {areas.map((area) => (
                <Box component="li" key={area.id}>
                  <Card>
                    <CardActionArea component={RouterLink} to={`/areas-protegidas/${area.id}`}>
                      <CardContent>
                        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                          <ParkIcon fontSize="small" color="success" aria-hidden />
                          <Typography fontWeight={700}>{area.name}</Typography>
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {MPA_CATEGORY_LABEL[area.category]} · {area.zones.length} zonas de manejo
                        </Typography>
                      </CardContent>
                    </CardActionArea>
                  </Card>
                </Box>
              ))}
            </Stack>
          </Grid>
        </Grid>
      )}
    </>
  );
}

function MpaDetail({ id }: { id: string }) {
  const query = useQuery({
    queryKey: ['public-mpa', id],
    queryFn: () => domainService.marineProtectedArea(id),
    retry: false,
    staleTime: 5 * 60_000,
  });
  const area = query.data;
  const zones = useMemo(() => (area ? mpaZonesForMap([area]) : []), [area]);
  const error = query.error ? toAppError(query.error) : null;

  if (query.isLoading) return <CircularProgress size={28} />;
  if (error) {
    return (
      <Alert severity={error.status === 404 ? 'warning' : 'error'} role="alert">
        {error.status === 404 ? 'No existe un área protegida publicada con ese identificador.' : error.message}
      </Alert>
    );
  }
  if (!area) return null;

  return (
    <>
      <Chip label={MPA_CATEGORY_LABEL[area.category]} color="success" size="small" sx={{ mb: 1 }} />
      <Typography variant="h3" component="h1" sx={{ fontSize: { xs: '2rem', md: '2.5rem' }, mb: 1 }}>
        {area.name}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 0.5 }}>
        Acto administrativo: <strong>{area.legalAct}</strong>
        {area.authority ? ` · ${area.authority}` : ''}
      </Typography>
      {area.description && <Typography sx={{ mb: 3, maxWidth: 760 }}>{area.description}</Typography>}

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <GeoMap title={`Zonificación de ${area.name}`} zones={zones} height={420} />
        </CardContent>
      </Card>

      <ZonesTable area={area} />
    </>
  );
}

function ZonesTable({ area }: { area: MarineProtectedArea }) {
  return (
    <TableContainer component={Card}>
      <Table aria-label={`Zonas de manejo de ${area.name}`}>
        <TableHead>
          <TableRow>
            <TableCell>Zona</TableCell>
            <TableCell>Tipo</TableCell>
            <TableCell>Qué se permite</TableCell>
            <TableCell>Artes permitidas</TableCell>
            <TableCell>Especies permitidas</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {area.zones.map((zone) => (
            <TableRow key={zone.id}>
              <TableCell>
                <Typography fontWeight={700}>{zone.name}</Typography>
                {zone.description && <Typography variant="body2" color="text.secondary">{zone.description}</Typography>}
              </TableCell>
              <TableCell>{MPA_ZONE_KIND_LABEL[zone.kind]}</TableCell>
              <TableCell>{KIND_RULE[zone.kind]}</TableCell>
              <TableCell>{refs(zone.kind, zone.allowedGears.map((gear) => gear.name), 'Cualquier arte legal')}</TableCell>
              <TableCell>{refs(zone.kind, zone.allowedSpecies.map((species) => species.name), 'Cualquier especie sin veda')}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function refs(kind: keyof typeof KIND_RULE, names: string[], anyLabel: string): string {
  if (kind === 'INTANGIBLE') return 'Ninguna';
  if (kind === 'BUFFER' || names.length === 0) return anyLabel;
  return names.join(', ');
}
