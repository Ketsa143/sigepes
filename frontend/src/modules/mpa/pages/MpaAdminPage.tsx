import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import UndoIcon from '@mui/icons-material/Undo';
import {
  Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, List, ListItemButton, ListItemText,
  MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  domainService, type Gear, type MarineProtectedArea, type MpaCategory, type MpaZoneKind, type RestrictedZone,
  type Species,
} from '../../../shared/api/domain.service';
import { GeoMap } from '../../../shared/components/GeoMap';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { MPA_CATEGORY_LABEL, MPA_ZONE_KIND_LABEL, mpaZonesForMap, parseVertices } from '../../../shared/geo/geo';
import { useInteractiveMap } from '../../../shared/hooks/useInteractiveMap';

const EMPTY_AREA = { name: '', category: 'NATIONAL_PARK' as MpaCategory, legalAct: '', authority: '', description: '' };
const EMPTY_ZONE = { name: '', kind: 'INTANGIBLE' as MpaZoneKind, vertices: '', description: '' };

/**
 * HU-13 · AC-13.1 / AC-13.2 · El administrador registra las Áreas Marinas Protegidas (nombre, categoría
 * y acto administrativo) y les agrega zonas de manejo con su polígono y, en uso sostenible, las artes
 * y especies permitidas. Desde ahí el control punto-en-zona y la consulta pública las usan solas.
 */
export function MpaAdminPage() {
  const queryClient = useQueryClient();
  const interactiveMap = useInteractiveMap();
  const areasQuery = useQuery({ queryKey: ['admin-mpas'], queryFn: domainService.marineProtectedAreasFresh });
  const gearsQuery = useQuery({ queryKey: ['gears'], queryFn: domainService.gears });
  const speciesQuery = useQuery({ queryKey: ['species'], queryFn: domainService.species });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [areaForm, setAreaForm] = useState(EMPTY_AREA);
  const [zoneForm, setZoneForm] = useState(EMPTY_ZONE);
  const [gears, setGears] = useState<Gear[]>([]);
  const [species, setSpecies] = useState<Species[]>([]);

  const areas = useMemo(() => areasQuery.data ?? [], [areasQuery.data]);
  const selected = areas.find((area) => area.id === selectedId) ?? null;
  const vertices = useMemo(() => parseVertices(zoneForm.vertices), [zoneForm.vertices]);
  const verticesInvalid = zoneForm.vertices.trim() !== '' && (vertices === null || vertices.length < 3);

  const refresh = (area: MarineProtectedArea) => {
    setSelectedId(area.id);
    // La respuesta ya trae el área actualizada: se muestra de una vez, sin esperar otra consulta.
    queryClient.setQueryData<MarineProtectedArea[]>(['admin-mpas'], (current = []) =>
      current.some((a) => a.id === area.id) ? current.map((a) => (a.id === area.id ? area : a)) : [...current, area]);
    void queryClient.invalidateQueries({ queryKey: ['admin-mpas'] });
    void queryClient.invalidateQueries({ queryKey: ['public-mpas'] });
    void queryClient.invalidateQueries({ queryKey: ['zones'] });
  };
  const createArea = useMutation({
    mutationFn: () => domainService.createMpa({
      name: areaForm.name.trim(), category: areaForm.category, legalAct: areaForm.legalAct.trim(),
      authority: areaForm.authority.trim() || undefined, description: areaForm.description.trim() || undefined,
    }),
    onSuccess: (area) => { setAreaForm(EMPTY_AREA); refresh(area); },
  });
  const addZone = useMutation({
    mutationFn: () => domainService.addMpaZone(selected!.id, {
      name: zoneForm.name.trim(), kind: zoneForm.kind, polygon: vertices ?? [],
      description: zoneForm.description.trim() || undefined,
      allowedGearIds: zoneForm.kind === 'SUSTAINABLE_USE' ? gears.map((g) => g.id) : [],
      allowedSpeciesIds: zoneForm.kind === 'SUSTAINABLE_USE' ? species.map((s) => s.id) : [],
    }),
    onSuccess: (area) => { setZoneForm(EMPTY_ZONE); setGears([]); setSpecies([]); refresh(area); },
  });

  const mapZones = useMemo<RestrictedZone[]>(() => {
    const existing = selected ? mpaZonesForMap([selected]) : mpaZonesForMap(areas);
    if (vertices && vertices.length >= 3) {
      existing.push({ id: 'preview', name: zoneForm.name || 'Zona nueva', zoneType: 'MPA', polygon: vertices,
        resolutionNumber: selected?.legalAct ?? '', active: true, mpaId: selected?.id, mpaZoneKind: zoneForm.kind });
    }
    return existing;
  }, [areas, selected, vertices, zoneForm.name, zoneForm.kind]);

  return (
    <>
      <Button component={RouterLink} to="/app/zones" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>Zonas restringidas</Button>
      <PageHeader title="Registrar Áreas Marinas Protegidas"
        subtitle="Registra el área con su acto administrativo y agrégale sus zonas de manejo. En cuanto se guardan, el control de posición GPS y de capturas las aplica, y aparecen en el mapa público." />
      {areasQuery.isError && <Alert severity="error">{toAppError(areasQuery.error).message}</Alert>}
      <Grid container spacing={2.5}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Áreas registradas</Typography>
              {areasQuery.isLoading && <CircularProgress size={24} />}
              <List dense disablePadding>
                {areas.map((area) => (
                  <ListItemButton key={area.id} selected={area.id === selectedId} onClick={() => setSelectedId(area.id)}>
                    <ListItemText primary={area.name}
                      secondary={`${MPA_CATEGORY_LABEL[area.category]} · ${area.zones.length} zonas`} />
                  </ListItemButton>
                ))}
              </List>
              {selected && <Button size="small" onClick={() => setSelectedId(null)}>Ver todas</Button>}
            </CardContent>
          </Card>
          <Card>
            <CardContent>
              <Typography variant="h6" component="h2" sx={{ mb: 1.5 }}>Nueva área (AC-13.1)</Typography>
              <Stack spacing={1.5}>
                <TextField label="Nombre" size="small" required value={areaForm.name}
                  onChange={(e) => setAreaForm({ ...areaForm, name: e.target.value })} />
                <TextField select label="Categoría" size="small" value={areaForm.category}
                  onChange={(e) => setAreaForm({ ...areaForm, category: e.target.value as MpaCategory })}>
                  {(Object.keys(MPA_CATEGORY_LABEL) as MpaCategory[]).map((key) => <MenuItem key={key} value={key}>{MPA_CATEGORY_LABEL[key]}</MenuItem>)}
                </TextField>
                <TextField label="Acto administrativo" size="small" required placeholder="Resolución 1234 de 2026" value={areaForm.legalAct}
                  onChange={(e) => setAreaForm({ ...areaForm, legalAct: e.target.value })} />
                <TextField label="Autoridad (opcional)" size="small" value={areaForm.authority}
                  onChange={(e) => setAreaForm({ ...areaForm, authority: e.target.value })} />
                <TextField label="Descripción (opcional)" size="small" multiline minRows={2} value={areaForm.description}
                  onChange={(e) => setAreaForm({ ...areaForm, description: e.target.value })} />
                {createArea.isError && <Alert severity="error">{toAppError(createArea.error).message}</Alert>}
                <Button variant="contained" startIcon={<AddIcon />}
                  disabled={createArea.isPending || !areaForm.name.trim() || !areaForm.legalAct.trim()}
                  onClick={() => createArea.mutate()}>
                  Registrar área
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Card sx={{ mb: 2 }}>
            <CardContent>
              <GeoMap title={selected ? `Zonificación de ${selected.name}` : 'Áreas Marinas Protegidas'} zones={mapZones}
                highlightZoneId={vertices && vertices.length >= 3 ? 'preview' : undefined} height={380}
                track={vertices && vertices.length < 3 ? vertices.map(([latitude, longitude], index) => ({ id: `v${index}`, latitude, longitude })) : []}
                onPick={selected ? (lat, lon) => setZoneForm((current) => ({
                  ...current,
                  vertices: `${current.vertices.trim()}${current.vertices.trim() ? '\n' : ''}${lat.toFixed(5)}, ${lon.toFixed(5)}`,
                })) : undefined} />
            </CardContent>
          </Card>
          {selected ? (
            <Card>
              <CardContent>
                <Typography variant="h6" component="h2" sx={{ mb: 0.5 }}>Agregar zona de manejo a «{selected.name}» (AC-13.2)</Typography>
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap sx={{ mb: 1.5 }}>
                  {selected.zones.map((zone) => <Chip key={zone.id} size="small" label={`${zone.name} · ${MPA_ZONE_KIND_LABEL[zone.kind]}`} />)}
                </Stack>
                <Grid container spacing={1.5}>
                  <Grid size={{ xs: 12, sm: 7 }}>
                    <TextField label="Nombre de la zona" size="small" fullWidth required value={zoneForm.name}
                      onChange={(e) => setZoneForm({ ...zoneForm, name: e.target.value })} />
                  </Grid>
                  <Grid size={{ xs: 12, sm: 5 }}>
                    <TextField select label="Tipo" size="small" fullWidth value={zoneForm.kind}
                      onChange={(e) => setZoneForm({ ...zoneForm, kind: e.target.value as MpaZoneKind })}>
                      {(Object.keys(MPA_ZONE_KIND_LABEL) as MpaZoneKind[]).map((key) => <MenuItem key={key} value={key}>{MPA_ZONE_KIND_LABEL[key]}</MenuItem>)}
                    </TextField>
                  </Grid>
                  <Grid size={12}>
                    <TextField label="Vértices (latitud, longitud)" fullWidth multiline minRows={4} value={zoneForm.vertices}
                      error={verticesInvalid}
                      helperText={verticesInvalid ? 'Al menos 3 vértices válidos, uno por línea: 6.05, -77.62'
                        : interactiveMap ? 'Uno por línea, o toca el mapa para agregarlos en orden.' : 'Uno por línea, en orden.'}
                      onChange={(e) => setZoneForm({ ...zoneForm, vertices: e.target.value })} />
                    {zoneForm.vertices.trim() !== '' && (
                      <Button size="small" startIcon={<UndoIcon />}
                        onClick={() => setZoneForm({ ...zoneForm, vertices: zoneForm.vertices.trim().split('\n').slice(0, -1).join('\n') })}>
                        Quitar el último vértice
                      </Button>
                    )}
                  </Grid>
                  {zoneForm.kind === 'SUSTAINABLE_USE' && (
                    <>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Autocomplete multiple size="small" options={(gearsQuery.data ?? []).filter((g) => !g.destructive)} value={gears}
                          onChange={(_, value) => setGears(value)} getOptionLabel={(g) => g.name} isOptionEqualToValue={(a, b) => a.id === b.id}
                          renderInput={(params) => <TextField {...params} label="Artes permitidas" helperText="Vacío: cualquier arte legal" />} />
                      </Grid>
                      <Grid size={{ xs: 12, md: 6 }}>
                        <Autocomplete multiple size="small" options={speciesQuery.data ?? []} value={species}
                          onChange={(_, value) => setSpecies(value)} getOptionLabel={(s) => s.commonName} isOptionEqualToValue={(a, b) => a.id === b.id}
                          renderInput={(params) => <TextField {...params} label="Especies permitidas" helperText="Vacío: cualquier especie sin veda" />} />
                      </Grid>
                    </>
                  )}
                  <Grid size={12}>
                    <TextField label="Descripción (opcional)" size="small" fullWidth value={zoneForm.description}
                      onChange={(e) => setZoneForm({ ...zoneForm, description: e.target.value })} />
                  </Grid>
                </Grid>
                {addZone.isError && <Alert severity="error" sx={{ mt: 1.5 }}>{toAppError(addZone.error).message}</Alert>}
                <Box sx={{ mt: 1.5 }}>
                  <Button variant="contained" disabled={addZone.isPending || !zoneForm.name.trim() || !vertices || vertices.length < 3}
                    onClick={() => addZone.mutate()}>
                    Guardar zona
                  </Button>
                </Box>
              </CardContent>
            </Card>
          ) : (
            <Alert severity="info">Elige un área de la lista (o regístrala) para agregarle sus zonas de manejo.</Alert>
          )}
        </Grid>
      </Grid>
    </>
  );
}
