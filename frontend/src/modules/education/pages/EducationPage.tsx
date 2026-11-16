import SchoolOutlinedIcon from '@mui/icons-material/SchoolOutlined';
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined';
import {
  Alert, Box, Button, Card, CardActionArea, CardContent, Chip, CircularProgress, Grid, LinearProgress, Stack, Typography,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { domainService } from '../../../shared/api/domain.service';
import { EmptyState } from '../../../shared/components/EmptyState';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { useAuthStore } from '../../auth/store/authStore';
import { CourseIcon } from '../courseIcons';

/**
 * HU-19 · Cursos de pesca responsable. U4: tarjetas grandes con ícono; tocar la tarjeta lleva directo a
 * la primera lección pendiente (menú → curso → lección: 3 toques como máximo).
 */
export function EducationPage() {
  const navigate = useNavigate();
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN');
  const query = useQuery({ queryKey: ['courses'], queryFn: domainService.courses });
  const courses = query.data ?? [];

  return (
    <>
      <PageHeader
        title="Cursos de pesca responsable"
        subtitle="Lecciones cortas que puede escuchar. Al aprobar el cuestionario recibe una constancia que mejora su perfil."
        secondaryAction={isAdmin ? <Button component={RouterLink} to="/app/education/admin">Gestionar cursos</Button> : undefined}
      />
      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {query.isSuccess && courses.length === 0 && (
        <EmptyState icon={<SchoolOutlinedIcon sx={{ fontSize: 48, color: 'secondary.light' }} />}
          title="Todavía no hay cursos publicados" description="La autoridad publicará los cursos aquí." />
      )}
      <Grid container spacing={2.5}>
        {courses.map((course) => {
          const progress = course.lessons ? Math.round((course.completedLessons / course.lessons) * 100) : 0;
          return (
            <Grid key={course.id} size={{ xs: 12, sm: 6, lg: 4 }}>
              <Card sx={{ height: '100%' }}>
                <CardActionArea sx={{ height: '100%', alignItems: 'stretch' }} onClick={() => void navigate(`/app/education/${course.id}`)}>
                  <CardContent>
                    <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 1.5 }}>
                      <Box sx={{ width: 64, height: 64, borderRadius: 2, display: 'grid', placeItems: 'center', bgcolor: 'primary.main', color: 'primary.contrastText', flexShrink: 0 }}>
                        <CourseIcon icon={course.icon} sx={{ fontSize: 38 }} />
                      </Box>
                      <Typography variant="h6" component="h2" sx={{ fontSize: '1.2rem', lineHeight: 1.25 }}>{course.title}</Typography>
                    </Stack>
                    <Typography variant="body1" color="text.secondary" sx={{ mb: 1.5 }}>{course.summary}</Typography>
                    <Typography variant="body2" sx={{ mb: 0.5 }}>
                      {course.lessons} lecciones · unos {course.minutes} minutos
                    </Typography>
                    <LinearProgress variant="determinate" value={progress} sx={{ height: 8, borderRadius: 4, mb: 1 }}
                      aria-label={`Avance: ${course.completedLessons} de ${course.lessons} lecciones`} />
                    {course.passed ? (
                      <Chip color="success" icon={<WorkspacePremiumOutlinedIcon />} label={`Aprobado · constancia ${course.certificateCode}`} />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {course.completedLessons === 0 ? 'Toque para empezar' : `${course.completedLessons} de ${course.lessons} lecciones vistas`}
                      </Typography>
                    )}
                  </CardContent>
                </CardActionArea>
              </Card>
            </Grid>
          );
        })}
      </Grid>
    </>
  );
}
