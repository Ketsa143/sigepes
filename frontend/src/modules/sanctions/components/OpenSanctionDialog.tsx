import {
  Alert, Autocomplete, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { domainService, type Infraction, type Vessel } from '../../../shared/api/domain.service';
import { toAppError } from '../../../shared/errors/AppError';
import { INFRACTION_LABEL } from '../sanctionLabels';

interface Props {
  /** Origen del proceso: un acta con hallazgos o una denuncia fundada. */
  inspectionId?: string;
  reportId?: string;
  /** Texto de contexto (código del acta o de la denuncia). */
  origin: string;
  onClose: () => void;
}

/**
 * HU-23 · AC-23.1 · Abre el proceso sancionatorio. Desde un acta la embarcación y la infracción ya se
 * conocen (se propone la más grave); desde una denuncia hay que indicar la embarcación investigada.
 */
export function OpenSanctionDialog({ inspectionId, reportId, origin, onClose }: Props) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const needsVessel = !!reportId;
  const vessels = useQuery({ queryKey: ['vessels'], queryFn: domainService.vessels, enabled: needsVessel });
  const [vessel, setVessel] = useState<Vessel | null>(null);
  const [infraction, setInfraction] = useState<Infraction | ''>('');
  const [facts, setFacts] = useState('');

  const open = useMutation({
    mutationFn: () => domainService.openSanction({
      inspectionId, reportId, vesselId: vessel?.id, infraction: infraction || undefined, facts: facts.trim() || undefined,
    }),
    onSuccess: async (process) => {
      await queryClient.invalidateQueries({ queryKey: ['sanctions'] });
      void navigate(`/app/sanctions/${process.id}`);
    },
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="open-sanction-title">
      <DialogTitle id="open-sanction-title">Abrir proceso sancionatorio</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            A partir de {origin}. El proceso queda en «Apertura» y el investigado recibe la notificación con el plazo de
            15 días hábiles para presentar descargos.
          </Typography>
          {needsVessel && (
            <Autocomplete
              options={vessels.data ?? []}
              loading={vessels.isLoading}
              value={vessel}
              onChange={(_, value) => setVessel(value)}
              getOptionLabel={(option) => `${option.name} · ${option.registration}${option.ownerName ? ` · ${option.ownerName}` : ''}`}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              renderInput={(params) => <TextField {...params} label="Embarcación investigada" required />}
            />
          )}
          <TextField select label="Infracción tipificada" value={infraction}
            helperText={inspectionId ? 'Si no eliges, se propone la más grave de los hallazgos del acta.' : 'Si no eliges, se propone según el tipo de denuncia.'}
            onChange={(e) => setInfraction(e.target.value as Infraction | '')}>
            <MenuItem value="">Proponer automáticamente</MenuItem>
            {(Object.keys(INFRACTION_LABEL) as Infraction[]).map((key) => (
              <MenuItem key={key} value={key}>{INFRACTION_LABEL[key]}</MenuItem>
            ))}
          </TextField>
          <TextField label="Hechos (opcional)" value={facts} multiline minRows={3}
            helperText="Si lo dejas vacío se toman los hallazgos del acta o el relato de la denuncia."
            onChange={(e) => setFacts(e.target.value)} slotProps={{ htmlInput: { maxLength: 1500 } }} />
          {open.isError && <Alert severity="error">{toAppError(open.error).message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={open.isPending || (needsVessel && !vessel)} onClick={() => open.mutate()}>
          {open.isPending ? 'Abriendo…' : 'Abrir proceso'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
