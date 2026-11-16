import AddIcon from '@mui/icons-material/Add';
import AddAPhotoOutlinedIcon from '@mui/icons-material/AddAPhotoOutlined';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import {
  Alert, Autocomplete, Box, Button, Card, CardContent, Checkbox, Chip, FormControlLabel, Grid, IconButton,
  MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  domainService, type Gear, type InspectionInput, type Species, type Vessel,
} from '../../../shared/api/domain.service';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useOnlineStatus } from '../../../shared/hooks/useOnlineStatus';
import { stripImage } from '../../../shared/media/stripImage';
import {
  cacheFieldGears, cacheFieldVessels, cachedFieldGears, cachedFieldVessels,
} from '../../../shared/storage/fieldCatalog';
import { enqueueInspection } from '../../../shared/storage/inspectionOutbox';
import { cacheSpecies, getCachedSpecies } from '../../../shared/storage/speciesOfflineStore';
import { SignaturePad } from '../components/SignaturePad';

const MAX_PHOTOS = 4;

interface CatchRow {
  key: number;
  speciesId: string;
  sizeCm: string;
  weightKg: string;
  quantity: string;
}

function newClientId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(16)}-${Math.random().toString(16).slice(2, 10)}-${Math.random().toString(16).slice(2, 10)}`;
}

/** Con señal trae el catálogo y lo guarda; sin señal usa la última copia guardada (D5). */
function useFieldList<T>(key: string, fetcher: () => Promise<T[]>, save: (items: T[]) => void,
  cached: () => { items: T[] } | null) {
  return useQuery({
    queryKey: ['field-catalog', key],
    queryFn: async () => {
      try {
        const items = await fetcher();
        save(items);
        return items;
      } catch (error) {
        const copy = cached();
        if (copy) return copy.items;
        throw error;
      }
    },
    staleTime: 10 * 60_000,
    retry: 0,
  });
}

/**
 * HU-22 · Acta de inspección en campo. Se arma y se firma en el teléfono; si no hay señal queda en
 * el dispositivo y se envía sola al recuperar la conexión (AC-22.4).
 */
export function NewInspectionPage() {
  const navigate = useNavigate();
  const online = useOnlineStatus();
  const vesselsQuery = useFieldList<Vessel>('vessels', domainService.vessels, cacheFieldVessels, cachedFieldVessels);
  const gearsQuery = useFieldList<Gear>('gears', domainService.gears, cacheFieldGears, cachedFieldGears);
  const speciesQuery = useFieldList<Species>('species', domainService.species, cacheSpecies,
    () => { const items = getCachedSpecies(); return items ? { items } : null; });
  const vessels = useMemo(() => vesselsQuery.data ?? [], [vesselsQuery.data]);
  const gears = useMemo(() => (gearsQuery.data ?? []).filter((gear) => gear.status !== 'INACTIVE'), [gearsQuery.data]);
  const species = useMemo(() => speciesQuery.data ?? [], [speciesQuery.data]);

  const [vessel, setVessel] = useState<Vessel | null>(null);
  const [locationText, setLocationText] = useState('');
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locating, setLocating] = useState(false);
  const [rows, setRows] = useState<CatchRow[]>([{ key: 1, speciesId: '', sizeCm: '', weightKg: '', quantity: '' }]);
  const [gearIds, setGearIds] = useState<string[]>([]);
  const [others, setOthers] = useState<string[]>([]);
  const [otherDraft, setOtherDraft] = useState('');
  const [observations, setObservations] = useState('');
  const [photos, setPhotos] = useState<Blob[]>([]);
  const [responsibleName, setResponsibleName] = useState('');
  const [responsibleId, setResponsibleId] = useState('');
  const [inspectorSignature, setInspectorSignature] = useState<string | null>(null);
  const [responsibleSignature, setResponsibleSignature] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const speciesById = useMemo(() => new Map(species.map((s) => [s.id, s])), [species]);
  const gearById = useMemo(() => new Map(gears.map((g) => [g.id, g])), [gears]);

  /** AC-22.2 · Vista previa de los hallazgos; el servidor los confirma (también el permiso). */
  const preview = useMemo(() => {
    const list: string[] = [];
    rows.forEach((row) => {
      const sp = speciesById.get(row.speciesId);
      const size = Number(row.sizeCm);
      if (sp && row.sizeCm && sp.minSizeCm != null && size < sp.minSizeCm) {
        list.push(`Talla inferior a la mínima (${sp.minSizeCm} cm): ${sp.commonName} de ${size} cm`);
      }
    });
    gearIds.forEach((id) => {
      const gear = gearById.get(id);
      if (!gear) return;
      if (gear.destructive) list.push(`Arte prohibido a bordo: ${gear.name}`);
      else if (vessel && !(vessel.gearIds ?? []).includes(id)) list.push(`Arte no registrado: ${gear.name}`);
    });
    if (vessel && vessel.status !== 'ACTIVE') list.push('Embarcación no activa');
    return [...list, ...others];
  }, [rows, gearIds, others, speciesById, gearById, vessel]);

  const locate = () => {
    if (!('geolocation' in navigator)) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        setLocating(false);
      },
      () => setLocating(false),
      { enableHighAccuracy: true, timeout: 15_000 },
    );
  };

  const addPhotos = async (files: FileList | null) => {
    if (!files) return;
    const room = MAX_PHOTOS - photos.length;
    const picked = await Promise.all(Array.from(files).slice(0, room).map((file) => stripImage(file)));
    setPhotos((current) => [...current, ...picked]);
  };

  const validRows = rows.filter((row) => row.speciesId && Number(row.sizeCm) > 0);
  const ready = !!vessel && locationText.trim().length > 0 && responsibleName.trim().length > 0
    && !!inspectorSignature && !!responsibleSignature
    && rows.every((row) => !row.speciesId || Number(row.sizeCm) > 0);

  const submit = async () => {
    if (!vessel || !inspectorSignature || !responsibleSignature) return;
    const payload: InspectionInput = {
      clientId: newClientId(),
      vesselId: vessel.id,
      inspectedAt: new Date().toISOString(),
      latitude: coords?.latitude ?? null,
      longitude: coords?.longitude ?? null,
      locationText: locationText.trim(),
      catches: validRows.map((row) => ({
        speciesId: row.speciesId,
        sizeCm: Number(row.sizeCm),
        weightKg: row.weightKg ? Number(row.weightKg) : null,
        quantity: row.quantity ? Number(row.quantity) : null,
      })),
      gearIds,
      otherFindings: others,
      observations: observations.trim() || undefined,
      responsibleName: responsibleName.trim(),
      responsibleIdNumber: responsibleId.trim() || undefined,
      inspectorSignature,
      responsibleSignature,
      savedOffline: false,
    };
    setSaving(true);
    setError(null);
    const queue = async () => {
      await enqueueInspection({ clientId: payload.clientId, payload: { ...payload, savedOffline: true }, photos,
        vesselName: vessel.name, queuedAt: new Date().toISOString() });
      void navigate('/app/inspections', { state: { queued: vessel.name } });
    };
    try {
      if (!online) {
        await queue();
        return;
      }
      const saved = await domainService.submitInspection(payload, photos);
      void navigate(`/app/inspections/${saved.id}`, { state: { created: true } });
    } catch (err) {
      const status = axios.isAxiosError(err) ? err.response?.status : undefined;
      if (!status || status >= 500) {
        // D5 · Sin respuesta del servidor: el acta firmada se guarda en el dispositivo, no se pierde.
        await queue();
        return;
      }
      setError(toAppError(err).message);
    } finally {
      setSaving(false);
    }
  };

  const catalogsMissing = !vesselsQuery.isLoading && vessels.length === 0;

  return (
    <>
      <PageHeader
        title="Nueva acta de inspección"
        subtitle="Registra lo revisado a bordo, los hallazgos y las firmas. Funciona sin señal: el acta se guarda en el teléfono y se envía sola al volver la conexión."
      />
      {!online && (
        <Alert severity="info" icon={<CloudOffOutlinedIcon />} sx={{ mb: 2 }}>
          Sin conexión: el acta quedará guardada en este dispositivo y se sincronizará una sola vez al recuperar la señal.
        </Alert>
      )}
      {catalogsMissing && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          No hay embarcaciones guardadas en este dispositivo. Abre esta pantalla una vez con señal antes de salir a campo.
        </Alert>
      )}

      <Stack spacing={2.5}>
        <Card>
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>1. Embarcación y lugar</Typography>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <Autocomplete
                  options={vessels}
                  value={vessel}
                  onChange={(_, value) => {
                    setVessel(value);
                    // Las artes registradas de la embarcación quedan marcadas por defecto.
                    setGearIds(value?.gearIds ?? []);
                  }}
                  getOptionLabel={(option) => `${option.name} · ${option.registration}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  renderInput={(params) => <TextField {...params} label="Embarcación (nombre o matrícula)" required />}
                />
                {vessel && (
                  <Typography variant="caption" color="text.secondary">
                    Armador: {vessel.ownerName ?? '—'} · Puerto {vessel.port} · Estado {vessel.status}
                  </Typography>
                )}
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField label="Lugar de la inspección" value={locationText} required fullWidth
                  placeholder="Ej.: Bahía Solano, frente al muelle" onChange={(e) => setLocationText(e.target.value)} />
                <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1 }}>
                  <Button size="small" startIcon={<MyLocationIcon />} onClick={locate} disabled={locating}>
                    {locating ? 'Ubicando…' : coords ? 'Actualizar GPS' : 'Agregar posición GPS'}
                  </Button>
                  {coords && (
                    <Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {coords.latitude.toFixed(5)}, {coords.longitude.toFixed(5)}
                    </Typography>
                  )}
                </Stack>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 0.5 }}>2. Capturas medidas</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              La talla se compara con la talla mínima de la especie al registrar el acta.
            </Typography>
            <Stack spacing={1.5}>
              {rows.map((row, index) => {
                const sp = speciesById.get(row.speciesId);
                const below = sp && row.sizeCm && sp.minSizeCm != null && Number(row.sizeCm) < sp.minSizeCm;
                const update = (patch: Partial<CatchRow>) =>
                  setRows((current) => current.map((r) => (r.key === row.key ? { ...r, ...patch } : r)));
                return (
                  <Box key={row.key}>
                    <Grid container spacing={1.5} alignItems="center">
                      <Grid size={{ xs: 12, sm: 4 }}>
                        <TextField select size="small" fullWidth label={`Especie ${index + 1}`} value={row.speciesId}
                          onChange={(e) => update({ speciesId: e.target.value })}>
                          <MenuItem value="">Sin especie</MenuItem>
                          {species.map((s) => (
                            <MenuItem key={s.id} value={s.id}>{s.commonName} (mín. {s.minSizeCm} cm)</MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid size={{ xs: 4, sm: 2.5 }}>
                        <TextField size="small" type="number" label="Talla (cm)" value={row.sizeCm} fullWidth
                          error={!!below} slotProps={{ htmlInput: { min: 0, step: 0.1, inputMode: 'decimal' } }}
                          onChange={(e) => update({ sizeCm: e.target.value })} />
                      </Grid>
                      <Grid size={{ xs: 4, sm: 2.5 }}>
                        <TextField size="small" type="number" label="Peso (kg)" value={row.weightKg} fullWidth
                          slotProps={{ htmlInput: { min: 0, step: 0.1, inputMode: 'decimal' } }}
                          onChange={(e) => update({ weightKg: e.target.value })} />
                      </Grid>
                      <Grid size={{ xs: 3, sm: 2 }}>
                        <TextField size="small" type="number" label="Cantidad" value={row.quantity} fullWidth
                          slotProps={{ htmlInput: { min: 0, step: 1, inputMode: 'numeric' } }}
                          onChange={(e) => update({ quantity: e.target.value })} />
                      </Grid>
                      <Grid size={{ xs: 1 }}>
                        <IconButton aria-label={`Quitar captura ${index + 1}`} disabled={rows.length === 1}
                          onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}>
                          <DeleteOutlineIcon />
                        </IconButton>
                      </Grid>
                    </Grid>
                    {below && (
                      <Typography variant="caption" color="error">
                        Hallazgo: talla inferior a la mínima ({sp.minSizeCm} cm).
                      </Typography>
                    )}
                  </Box>
                );
              })}
              <Box>
                <Button startIcon={<AddIcon />} onClick={() => setRows((current) => [...current,
                  { key: Math.max(...current.map((r) => r.key)) + 1, speciesId: '', sizeCm: '', weightKg: '', quantity: '' }])}>
                  Agregar captura
                </Button>
              </Box>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 0.5 }}>3. Artes a bordo</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              Marca las artes encontradas. Las registradas de la embarcación vienen marcadas.
            </Typography>
            <Grid container>
              {gears.map((gear) => {
                const checked = gearIds.includes(gear.id);
                const registered = (vessel?.gearIds ?? []).includes(gear.id);
                return (
                  <Grid key={gear.id} size={{ xs: 12, sm: 6, md: 4 }}>
                    <FormControlLabel
                      control={<Checkbox checked={checked} onChange={() => setGearIds((current) =>
                        checked ? current.filter((id) => id !== gear.id) : [...current, gear.id])} />}
                      label={
                        <Stack direction="row" spacing={0.75} alignItems="center">
                          <span>{gear.name}</span>
                          {gear.destructive && <Chip size="small" color="error" label="Prohibida" />}
                          {!gear.destructive && vessel && !registered && checked && (
                            <Chip size="small" color="warning" variant="outlined" label="No registrada" />
                          )}
                        </Stack>
                      }
                    />
                  </Grid>
                );
              })}
            </Grid>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>4. Otros hallazgos, observaciones y fotos</Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 1 }}>
              <TextField size="small" fullWidth label="Otro hallazgo" value={otherDraft}
                placeholder="Ej.: sin chalecos salvavidas para toda la tripulación"
                onChange={(e) => setOtherDraft(e.target.value)} slotProps={{ htmlInput: { maxLength: 400 } }} />
              <Button variant="outlined" disabled={!otherDraft.trim() || others.length >= 10}
                onClick={() => { setOthers((current) => [...current, otherDraft.trim()]); setOtherDraft(''); }}>
                Agregar
              </Button>
            </Stack>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
              {others.map((other, index) => (
                <Chip key={`${other}-${index}`} label={other} onDelete={() =>
                  setOthers((current) => current.filter((_, i) => i !== index))} />
              ))}
            </Stack>
            <TextField label="Observaciones" value={observations} multiline minRows={2} fullWidth
              onChange={(e) => setObservations(e.target.value)} slotProps={{ htmlInput: { maxLength: 1500 } }} />
            <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mt: 2 }} flexWrap="wrap" useFlexGap>
              <Button component="label" variant="outlined" startIcon={<AddAPhotoOutlinedIcon />}
                disabled={photos.length >= MAX_PHOTOS}>
                Tomar o elegir foto
                <input hidden type="file" accept="image/jpeg,image/png" capture="environment" multiple
                  onChange={(e) => { void addPhotos(e.target.files); e.target.value = ''; }} />
              </Button>
              <Typography variant="caption" color="text.secondary">{photos.length} de {MAX_PHOTOS} fotos</Typography>
              {photos.map((photo, index) => (
                <Chip key={index} label={`Foto ${index + 1} · ${Math.round(photo.size / 1024)} KB`}
                  onDelete={() => setPhotos((current) => current.filter((_, i) => i !== index))} />
              ))}
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>5. Firmas</Typography>
            <Grid container spacing={2} sx={{ mb: 2 }}>
              <Grid size={{ xs: 12, sm: 7 }}>
                <TextField label="Responsable de la embarcación" value={responsibleName} required fullWidth
                  onChange={(e) => setResponsibleName(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 12, sm: 5 }}>
                <TextField label="Cédula del responsable" value={responsibleId} fullWidth
                  onChange={(e) => setResponsibleId(e.target.value)}
                  slotProps={{ htmlInput: { maxLength: 30, inputMode: 'numeric' } }} />
              </Grid>
            </Grid>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <SignaturePad label="Firma del inspector" onChange={setInspectorSignature} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <SignaturePad label="Firma del responsable" onChange={setResponsibleSignature} />
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        <Card sx={{ borderColor: preview.length ? 'error.main' : 'success.main', borderWidth: 2, borderStyle: 'solid' }}>
          <CardContent>
            <Typography variant="h6" component="h2">
              {preview.length ? `Resultado: con hallazgos (${preview.length})` : 'Resultado: conforme'}
            </Typography>
            {preview.length > 0 && (
              <Box component="ul" sx={{ pl: 2.5, my: 1 }}>
                {preview.map((item, index) => <li key={index}><Typography variant="body2">{item}</Typography></li>)}
              </Box>
            )}
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 2 }}>
              Al registrar, el servidor confirma los hallazgos (también el permiso vigente) y sella el acta: después no se
              puede modificar.
            </Typography>
            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            <Stack direction="row" spacing={1.5}>
              <Button variant="contained" size="large" disabled={!ready || saving} onClick={() => void submit()}>
                {saving ? 'Registrando…' : online ? 'Firmar y registrar acta' : 'Firmar y guardar en el dispositivo'}
              </Button>
              <Button onClick={() => void navigate('/app/inspections')}>Cancelar</Button>
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
