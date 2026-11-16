import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined';
import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import {
  Alert, Box, Button, Card, CardActions, CardContent, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, Grid, InputAdornment, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { domainService, type MarketOffer } from '../../../shared/api/domain.service';
import { EmptyState } from '../../../shared/components/EmptyState';
import { toAppError } from '../../../shared/errors/AppError';

const cop = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const kg = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });
const day = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' });

function hoursLeft(expiresAt: string): string {
  const hours = Math.max(0, Math.round((new Date(expiresAt).getTime() - Date.now()) / 3_600_000));
  return hours >= 24 ? `vence en ${Math.floor(hours / 24)} d ${hours % 24} h` : `vence en ${hours} h`;
}

interface Props {
  /** Sin sesión: se muestra el catálogo y se invita a iniciar sesión para solicitar. */
  publicMode?: boolean;
  /** Para no ofrecer «Solicitar» en las ofertas propias. */
  ownAlias?: string;
}

/**
 * HU-16 · AC-16.2 · Catálogo de producto legal con filtros por especie, puerto y precio. Cada oferta
 * enlaza a la verificación pública del lote; del vendedor solo se ve alias y municipio (AC-16.5).
 */
export function MarketCatalog({ publicMode, ownAlias }: Props) {
  const [speciesId, setSpeciesId] = useState('');
  const [port, setPort] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  const [selected, setSelected] = useState<MarketOffer | null>(null);
  const species = useQuery({ queryKey: ['species'], queryFn: domainService.species, enabled: !publicMode, staleTime: 600_000 });
  const query = useQuery({
    queryKey: ['market-offers', speciesId, port, maxPrice],
    queryFn: () => domainService.marketOffers({ speciesId, port: port.trim(), maxPrice: Number(maxPrice) || undefined }),
  });
  const offers = query.data ?? [];

  return (
    <>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2.5 }}>
        {!publicMode && (
          <TextField select size="small" label="Especie" value={speciesId} sx={{ minWidth: 200 }}
            onChange={(e) => setSpeciesId(e.target.value)}>
            <MenuItem value="">Todas</MenuItem>
            {(species.data ?? []).map((s) => <MenuItem key={s.id} value={s.id}>{s.commonName}</MenuItem>)}
          </TextField>
        )}
        <TextField size="small" label="Puerto" value={port} placeholder="Ej.: Bahía Solano" onChange={(e) => setPort(e.target.value)} />
        <TextField size="small" label="Precio máximo por kg" value={maxPrice} type="number"
          onChange={(e) => setMaxPrice(e.target.value)}
          slotProps={{ input: { startAdornment: <InputAdornment position="start">$</InputAdornment> }, htmlInput: { min: 0, step: 1000 } }} />
      </Stack>

      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {query.isSuccess && offers.length === 0 && (
        <EmptyState
          icon={<StorefrontOutlinedIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
          title="No hay ofertas con estos filtros"
          description="Las ofertas duran 72 horas. Vuelve pronto o amplía la búsqueda."
        />
      )}
      <Grid container spacing={2}>
        {offers.map((offer) => (
          <Grid key={offer.id} size={{ xs: 12, sm: 6, lg: 4 }}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flex: 1 }}>
                <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1}>
                  <Box>
                    <Typography variant="h6" component="h3" sx={{ fontSize: '1.1rem' }}>{offer.species}</Typography>
                    {offer.scientificName && (
                      <Typography variant="caption" color="text.secondary" sx={{ fontStyle: 'italic' }}>{offer.scientificName}</Typography>
                    )}
                  </Box>
                  <Chip size="small" color="success" icon={<VerifiedOutlinedIcon />} label="Origen legal" />
                </Stack>
                <Typography variant="h5" sx={{ mt: 1.5, fontVariantNumeric: 'tabular-nums' }}>
                  {cop.format(offer.pricePerKg)} <Typography component="span" variant="body2" color="text.secondary">/ kg</Typography>
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                  {kg.format(offer.weightKg)} kg · total {cop.format(offer.totalPrice)}
                </Typography>
                <Typography variant="body2" sx={{ mt: 1 }}>
                  Desembarco: {offer.port}, {day.format(new Date(`${offer.landingDate}T12:00:00`))}
                </Typography>
                <Typography variant="body2" color="text.secondary">Vendedor: {offer.sellerAlias} · {offer.municipality}</Typography>
                {offer.notes && <Typography variant="body2" sx={{ mt: 1 }}>{offer.notes}</Typography>}
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>{hoursLeft(offer.expiresAt)}</Typography>
              </CardContent>
              <CardActions sx={{ px: 2, pb: 2 }}>
                <Button size="small" component={RouterLink} to={offer.verificationPath}>Verificar lote {offer.lotCode}</Button>
                <Box sx={{ flex: 1 }} />
                {publicMode ? (
                  <Button size="small" variant="contained" component={RouterLink} to="/login">Inicia sesión para comprar</Button>
                ) : offer.sellerAlias !== ownAlias && (
                  <Button size="small" variant="contained" onClick={() => setSelected(offer)}>Solicitar</Button>
                )}
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
      {selected && <RequestDialog offer={selected} onClose={() => setSelected(null)} />}
    </>
  );
}

function RequestDialog({ offer, onClose }: { offer: MarketOffer; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(String(offer.weightKg));
  const [message, setMessage] = useState('');
  const send = useMutation({
    mutationFn: () => domainService.requestOffer(offer.id, { quantityKg: Number(quantity), message: message.trim() }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['market-my-requests'] }),
  });
  const qty = Number(quantity);
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="market-request-title">
      <DialogTitle id="market-request-title">Solicitar {offer.species}</DialogTitle>
      <DialogContent dividers>
        {send.isSuccess ? (
          <Alert severity="success">
            Solicitud enviada a {offer.sellerAlias}. Verás su respuesta en «Mis solicitudes» y en tus notificaciones.
          </Alert>
        ) : (
          <Stack spacing={2}>
            <TextField label="Cantidad (kg)" type="number" value={quantity} onChange={(e) => setQuantity(e.target.value)}
              helperText={`Disponible: ${kg.format(offer.weightKg)} kg · aprox. ${cop.format((qty || 0) * offer.pricePerKg)}`}
              slotProps={{ htmlInput: { min: 0.1, max: offer.weightKg, step: 0.5 } }} />
            <TextField label="Mensaje para el vendedor" value={message} multiline minRows={3} required
              placeholder="Ej.: Lo recojo mañana en el muelle. Pago contra entrega."
              helperText="No incluyas datos personales si no quieres compartirlos."
              onChange={(e) => setMessage(e.target.value)} slotProps={{ htmlInput: { maxLength: 600 } }} />
            {send.isError && <Alert severity="error">{toAppError(send.error).message}</Alert>}
          </Stack>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>{send.isSuccess ? 'Cerrar' : 'Cancelar'}</Button>
        {!send.isSuccess && (
          <Button variant="contained" onClick={() => send.mutate()}
            disabled={send.isPending || !message.trim() || !(qty > 0) || qty > offer.weightKg}>
            Enviar solicitud
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
