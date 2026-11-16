import AddBusinessOutlinedIcon from '@mui/icons-material/AddBusinessOutlined';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Stack, Tab, Tabs, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { domainService, type MyMarketOffer } from '../../../shared/api/domain.service';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useAuthStore } from '../../auth/store/authStore';
import { MarketCatalog } from '../components/MarketCatalog';
import { PublishWizard } from '../components/PublishWizard';

const cop = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });

const OFFER_STATUS: Record<MyMarketOffer['status'], { label: string; color: 'success' | 'info' | 'default' }> = {
  ACTIVE: { label: 'Activa', color: 'success' },
  SOLD: { label: 'Vendida', color: 'info' },
  EXPIRED: { label: 'Vencida', color: 'default' },
  WITHDRAWN: { label: 'Retirada', color: 'default' },
};
const REQUEST_STATUS = {
  PENDING: { label: 'Pendiente', color: 'warning' },
  ACCEPTED: { label: 'Aceptada', color: 'success' },
  REJECTED: { label: 'No aceptada', color: 'default' },
} as const;

/** Mismo alias que publica el servidor: nombre e inicial del primer apellido. */
function aliasOf(fullName?: string): string | undefined {
  const words = fullName?.trim().split(/\s+/) ?? [];
  if (!words.length) return undefined;
  return words.length === 1 ? words[0] : `${words[0]} ${words[1][0]}.`;
}

/** HU-16 · Marketplace de producto pesquero legal: catálogo, mis ofertas y mis solicitudes. */
export function MarketplacePage() {
  const user = useAuthStore((s) => s.user);
  const seller = user?.role === 'FISHER' || user?.role === 'SHIPOWNER';
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? 'catalogo';
  const [publishing, setPublishing] = useState(false);

  return (
    <>
      <PageHeader
        title="Marketplace de producto legal"
        subtitle="Solo se ofertan lotes con origen verificado. Cada oferta enlaza a su cadena de custodia y dura 72 horas; los compradores solo ven alias y municipio del vendedor."
        actionLabel={seller ? 'Publicar oferta' : undefined}
        actionIcon={<AddBusinessOutlinedIcon />}
        onAction={seller ? () => setPublishing(true) : undefined}
      />
      <Tabs value={tab} onChange={(_, value) => setParams({ tab: value })} sx={{ mb: 2.5 }}>
        <Tab value="catalogo" label="Catálogo" />
        {seller && <Tab value="mis-ofertas" label="Mis ofertas" />}
        <Tab value="mis-solicitudes" label="Mis solicitudes" />
      </Tabs>
      {tab === 'catalogo' && <MarketCatalog ownAlias={aliasOf(user?.fullName)} />}
      {tab === 'mis-ofertas' && seller && <MyOffers onPublish={() => setPublishing(true)} />}
      {tab === 'mis-solicitudes' && <MyRequests />}
      {publishing && <PublishWizard onClose={() => setPublishing(false)} />}
    </>
  );
}

function MyOffers({ onPublish }: { onPublish: () => void }) {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['market-my-offers'], queryFn: domainService.myMarketOffers });
  const decide = useMutation({
    mutationFn: ({ id, accept }: { id: string; accept: boolean }) => domainService.decideMarketRequest(id, accept),
    onSuccess: (offers) => {
      queryClient.setQueryData(['market-my-offers'], offers);
      void queryClient.invalidateQueries({ queryKey: ['market-offers'] });
    },
  });
  const withdraw = useMutation({
    mutationFn: domainService.withdrawOffer,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['market-my-offers'] }),
  });
  const offers = query.data ?? [];

  if (query.isLoading) return <CircularProgress size={28} />;
  if (query.isError) return <Alert severity="error">{toAppError(query.error).message}</Alert>;
  if (offers.length === 0) {
    return (
      <EmptyState icon={<AddBusinessOutlinedIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
        title="Todavía no has publicado ofertas" description="Publica un lote con origen verificado en 3 pasos."
        actionLabel="Publicar oferta" onAction={onPublish} />
    );
  }
  return (
    <Stack spacing={2}>
      {(decide.error || withdraw.error) && <Alert severity="error">{toAppError(decide.error ?? withdraw.error).message}</Alert>}
      {offers.map(({ offer, status, requests }) => (
        <Card key={offer.id}>
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
              <Box>
                <Typography fontWeight={700}>{offer.species} · {offer.weightKg} kg a {cop.format(offer.pricePerKg)}/kg</Typography>
                <Typography variant="caption" color="text.secondary">
                  Lote {offer.lotCode} · publicada {dateTime.format(new Date(offer.publishedAt))}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip size="small" color={OFFER_STATUS[status].color} label={OFFER_STATUS[status].label} />
                {status === 'ACTIVE' && (
                  <Button size="small" disabled={withdraw.isPending} onClick={() => withdraw.mutate(offer.id)}>Retirar</Button>
                )}
              </Stack>
            </Stack>
            {requests.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>Sin solicitudes todavía.</Typography>
            ) : (
              <Stack spacing={1} sx={{ mt: 1.5 }}>
                {requests.map((request) => (
                  <Box key={request.id} sx={{ p: 1.25, borderRadius: 1, bgcolor: 'action.hover' }}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
                      <Box>
                        <Typography variant="body2" fontWeight={700}>
                          {request.buyerAlias} quiere {request.quantityKg} kg
                        </Typography>
                        <Typography variant="body2">{request.message}</Typography>
                        <Typography variant="caption" color="text.secondary">{dateTime.format(new Date(request.createdAt))}</Typography>
                      </Box>
                      {request.status === 'PENDING' && status === 'ACTIVE' ? (
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Button size="small" variant="contained" disabled={decide.isPending}
                            onClick={() => decide.mutate({ id: request.id, accept: true })}>Aceptar</Button>
                          <Button size="small" disabled={decide.isPending}
                            onClick={() => decide.mutate({ id: request.id, accept: false })}>Rechazar</Button>
                        </Stack>
                      ) : (
                        <Chip size="small" color={REQUEST_STATUS[request.status].color} label={REQUEST_STATUS[request.status].label} />
                      )}
                    </Stack>
                  </Box>
                ))}
              </Stack>
            )}
            {status === 'SOLD' && (
              <Alert severity="success" sx={{ mt: 1.5 }}>
                Venta registrada en la cadena de custodia del lote.{' '}
                <RouterLink to={offer.verificationPath}>Ver verificación pública</RouterLink>
              </Alert>
            )}
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}

function MyRequests() {
  const query = useQuery({ queryKey: ['market-my-requests'], queryFn: domainService.myMarketRequests });
  const requests = query.data ?? [];
  if (query.isLoading) return <CircularProgress size={28} />;
  if (query.isError) return <Alert severity="error">{toAppError(query.error).message}</Alert>;
  if (requests.length === 0) {
    return <Typography color="text.secondary">No has enviado solicitudes. Búscalas en el catálogo.</Typography>;
  }
  return (
    <Stack spacing={1.5}>
      {requests.map((request) => (
        <Card key={request.id}>
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
              <Box>
                <Typography fontWeight={700}>
                  {request.quantityKg} kg de {request.offer.species} · {request.offer.sellerAlias} ({request.offer.municipality})
                </Typography>
                <Typography variant="body2">{request.message}</Typography>
                <Typography variant="caption" color="text.secondary">
                  {cop.format(request.offer.pricePerKg)}/kg · lote {request.offer.lotCode} · {dateTime.format(new Date(request.createdAt))}
                </Typography>
              </Box>
              <Chip size="small" color={REQUEST_STATUS[request.status].color} label={REQUEST_STATUS[request.status].label} />
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}
