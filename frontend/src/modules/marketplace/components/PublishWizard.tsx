import {
  Alert, Box, Button, Card, CardActionArea, CardContent, CircularProgress, Dialog, DialogActions, DialogContent,
  DialogTitle, InputAdornment, Stack, Step, StepLabel, Stepper, TextField, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { domainService, type EligibleLot } from '../../../shared/api/domain.service';
import { toAppError } from '../../../shared/errors/AppError';

const cop = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const day = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' });
const STEPS = ['Lote', 'Precio', 'Publicar'];

/**
 * HU-16 · AC-16.6 · U5 · Publicar una oferta en 3 pasos con los datos del lote ya cargados: elegir el
 * lote, poner el precio por kilo y confirmar. Meta: primera oferta sin ayuda en menos de 2 minutos.
 */
export function PublishWizard({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const lots = useQuery({ queryKey: ['market-eligible-lots'], queryFn: domainService.marketEligibleLots });
  const [step, setStep] = useState(0);
  const [lot, setLot] = useState<EligibleLot | null>(null);
  const [price, setPrice] = useState('');
  const [notes, setNotes] = useState('');
  const publish = useMutation({
    mutationFn: () => domainService.publishOffer({ lotId: lot!.lotId, pricePerKg: Number(price), notes: notes.trim() || undefined }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['market-my-offers'] });
      void queryClient.invalidateQueries({ queryKey: ['market-offers'] });
      void queryClient.invalidateQueries({ queryKey: ['market-eligible-lots'] });
    },
  });
  const value = Number(price);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="publish-title">
      <DialogTitle id="publish-title">Publicar una oferta</DialogTitle>
      <DialogContent dividers>
        <Stepper activeStep={publish.isSuccess ? 3 : step} sx={{ mb: 3 }}>
          {STEPS.map((label) => <Step key={label}><StepLabel>{label}</StepLabel></Step>)}
        </Stepper>

        {publish.isSuccess ? (
          <Alert severity="success">
            Oferta publicada: {lot?.species}, {lot?.weightKg} kg a {cop.format(value)}/kg. Estará en el catálogo 72 horas;
            te avisaremos cuando un comprador la solicite.
          </Alert>
        ) : step === 0 ? (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
              Elige el lote. Solo aparecen tus lotes con origen verificado que todavía no has entregado.
            </Typography>
            {lots.isLoading && <CircularProgress size={24} />}
            {lots.isSuccess && lots.data.length === 0 && (
              <Alert severity="info">
                No tienes lotes disponibles. Crea uno en «Trazabilidad» a partir de una faena cerrada: solo puedes ofertar lotes
                con origen verificado.
              </Alert>
            )}
            <Stack spacing={1}>
              {(lots.data ?? []).map((item) => (
                <Card key={item.lotId} variant="outlined"
                  sx={{ borderColor: lot?.lotId === item.lotId ? 'primary.main' : 'divider', borderWidth: lot?.lotId === item.lotId ? 2 : 1 }}>
                  <CardActionArea onClick={() => { setLot(item); setStep(1); }}>
                    <CardContent sx={{ py: 1.25, '&:last-child': { pb: 1.25 } }}>
                      <Typography fontWeight={700}>{item.species} · {item.weightKg} kg</Typography>
                      <Typography variant="caption" color="text.secondary">
                        Lote {item.lotCode} · desembarco en {item.port} el {day.format(new Date(`${item.landingDate}T12:00:00`))}
                      </Typography>
                    </CardContent>
                  </CardActionArea>
                </Card>
              ))}
            </Stack>
          </>
        ) : step === 1 && lot ? (
          <Stack spacing={2}>
            <Typography variant="body2">
              {lot.species} · {lot.weightKg} kg · {lot.port}
            </Typography>
            <TextField label="Precio por kilo" type="number" value={price} autoFocus required
              onChange={(e) => setPrice(e.target.value)}
              helperText={value > 0 ? `Total del lote: ${cop.format(value * lot.weightKg)}` : 'En pesos colombianos.'}
              slotProps={{ input: { startAdornment: <InputAdornment position="start">$</InputAdornment> }, htmlInput: { min: 100, step: 500, inputMode: 'numeric' } }} />
            <TextField label="Nota para el comprador (opcional)" value={notes} multiline minRows={2}
              placeholder="Ej.: entrego en el muelle, refrigerado" onChange={(e) => setNotes(e.target.value)}
              slotProps={{ htmlInput: { maxLength: 400 } }} />
          </Stack>
        ) : lot ? (
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>Revisa y publica</Typography>
            <Typography variant="body2">Especie: <strong>{lot.species}</strong></Typography>
            <Typography variant="body2">Cantidad: <strong>{lot.weightKg} kg</strong> (lote {lot.lotCode})</Typography>
            <Typography variant="body2">Precio: <strong>{cop.format(value)}/kg</strong> · total {cop.format(value * lot.weightKg)}</Typography>
            <Typography variant="body2">Desembarco: {lot.port}, {day.format(new Date(`${lot.landingDate}T12:00:00`))}</Typography>
            {notes.trim() && <Typography variant="body2">Nota: {notes.trim()}</Typography>}
            <Alert severity="info" sx={{ mt: 2 }}>
              Los compradores solo verán tu alias y tu municipio, nunca tu cédula, correo o teléfono.
            </Alert>
          </Box>
        ) : null}
        {publish.isError && <Alert severity="error" sx={{ mt: 2 }}>{toAppError(publish.error).message}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        {publish.isSuccess ? (
          <Button variant="contained" onClick={onClose}>Listo</Button>
        ) : (
          <>
            <Button onClick={step === 0 ? onClose : () => setStep(step - 1)}>{step === 0 ? 'Cancelar' : 'Atrás'}</Button>
            {step === 1 && <Button variant="contained" disabled={!(value >= 100)} onClick={() => setStep(2)}>Siguiente</Button>}
            {step === 2 && (
              <Button variant="contained" disabled={publish.isPending} onClick={() => publish.mutate()}>
                {publish.isPending ? 'Publicando…' : 'Publicar oferta'}
              </Button>
            )}
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
