import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Divider, Grid, IconButton, MenuItem, Stack, TextField,
  Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { domainService, type AdminCourse, type CourseIcon as CourseIconKey, type CourseInput } from '../../../shared/api/domain.service';
import { PageHeader } from '../../../shared/components/PageHeader';
import { toAppError } from '../../../shared/errors/AppError';
import { CourseIcon } from '../courseIcons';

const ICONS: { key: CourseIconKey; label: string }[] = [
  { key: 'ruler', label: 'Regla (tallas)' }, { key: 'calendar', label: 'Prohibición (vedas)' },
  { key: 'park', label: 'Árbol (áreas protegidas)' }, { key: 'hand', label: 'Manos (buenas prácticas)' },
  { key: 'fish', label: 'Pez' }, { key: 'boat', label: 'Embarcación' }, { key: 'book', label: 'Libro' },
];

const EMPTY: CourseInput = {
  title: '', summary: '', icon: 'book', passScore: 70,
  lessons: [{ title: '', body: '', imageUrl: '', minutes: 2 }],
  questions: [{ prompt: '', options: ['', ''], correctIndex: 0, explanation: '' }],
};

/** HU-19 · AC-19.4 · El ADMIN crea, edita, publica y retira cursos sin desplegar código. */
export function CourseAdminPage() {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['admin-courses'], queryFn: domainService.adminCourses });
  const [editing, setEditing] = useState<{ id?: string; input: CourseInput } | null>(null);
  const publish = useMutation({
    mutationFn: ({ id, value }: { id: string; value: boolean }) => domainService.publishCourse(id, value),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      void queryClient.invalidateQueries({ queryKey: ['courses'] });
    },
  });

  if (editing) {
    return <CourseEditor initial={editing.input} courseId={editing.id} onClose={() => setEditing(null)} />;
  }
  return (
    <>
      <Button component={RouterLink} to="/app/education" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>Cursos</Button>
      <PageHeader title="Gestionar cursos" subtitle="Crea, edita y publica cursos de pesca responsable. Los cambios llegan a los teléfonos sin desplegar código."
        actionLabel="Nuevo curso" actionIcon={<AddIcon />} onAction={() => setEditing({ input: EMPTY })} />
      {query.isLoading && <CircularProgress size={28} />}
      {query.isError && <Alert severity="error">{toAppError(query.error).message}</Alert>}
      {publish.isError && <Alert severity="error" sx={{ mb: 2 }}>{toAppError(publish.error).message}</Alert>}
      <Stack spacing={1.5}>
        {(query.data ?? []).map((course: AdminCourse) => (
          <Card key={course.id}>
            <CardContent>
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }} justifyContent="space-between">
                <Stack direction="row" spacing={1.5} alignItems="center">
                  <CourseIcon icon={course.icon} color="primary" sx={{ fontSize: 36 }} />
                  <Box>
                    <Typography fontWeight={700}>{course.title}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {course.lessons.length} lecciones · {course.questions.length} preguntas · versión {course.version} · aprueba con {course.passScore} %
                    </Typography>
                  </Box>
                </Stack>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip size="small" color={course.status === 'PUBLISHED' ? 'success' : 'default'}
                    label={course.status === 'PUBLISHED' ? 'Publicado' : 'Borrador'} />
                  <Button size="small" onClick={() => setEditing({ id: course.id, input: course })}>Editar</Button>
                  <Button size="small" variant="outlined" disabled={publish.isPending}
                    onClick={() => publish.mutate({ id: course.id, value: course.status !== 'PUBLISHED' })}>
                    {course.status === 'PUBLISHED' ? 'Retirar' : 'Publicar'}
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>
    </>
  );
}

function CourseEditor({ initial, courseId, onClose }: { initial: CourseInput; courseId?: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<CourseInput>(() => structuredClone(initial));
  const save = useMutation({
    mutationFn: () => domainService.saveCourse({
      ...form,
      lessons: form.lessons.map((l) => ({ ...l, imageUrl: l.imageUrl || null, audioUrl: l.audioUrl || null })),
      questions: form.questions.map((q) => ({ ...q, options: q.options.map((o) => o.trim()).filter(Boolean) })),
    }, courseId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-courses'] });
      void queryClient.invalidateQueries({ queryKey: ['courses'] });
      onClose();
    },
  });
  const set = (patch: Partial<CourseInput>) => setForm((current) => ({ ...current, ...patch }));

  return (
    <>
      <Button startIcon={<ArrowBackIcon />} onClick={onClose} sx={{ mb: 1 }}>Volver sin guardar</Button>
      <Typography variant="h4" component="h1" sx={{ mb: 2 }}>{courseId ? 'Editar curso' : 'Nuevo curso'}</Typography>
      {courseId && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Guardar sube la versión del curso: los teléfonos descargan la nueva y el avance de las lecciones se reinicia. Las constancias ya emitidas se conservan.
        </Alert>
      )}
      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 6 }}>
              <TextField label="Título" fullWidth required value={form.title} onChange={(e) => set({ title: e.target.value })} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField select label="Ícono" fullWidth value={form.icon} onChange={(e) => set({ icon: e.target.value as CourseIconKey })}>
                {ICONS.map((icon) => <MenuItem key={icon.key} value={icon.key}>{icon.label}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField label="Nota para aprobar (%)" type="number" fullWidth value={form.passScore}
                onChange={(e) => set({ passScore: Number(e.target.value) })} slotProps={{ htmlInput: { min: 1, max: 100 } }} />
            </Grid>
            <Grid size={12}>
              <TextField label="Resumen" fullWidth required multiline minRows={2} value={form.summary}
                onChange={(e) => set({ summary: e.target.value })} slotProps={{ htmlInput: { maxLength: 600 } }} />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Lecciones</Typography>
      <Stack spacing={1.5} sx={{ mb: 2 }}>
        {form.lessons.map((lesson, i) => (
          <Card key={i} variant="outlined">
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography fontWeight={700}>Lección {i + 1}</Typography>
                <IconButton aria-label={`Quitar lección ${i + 1}`} disabled={form.lessons.length === 1}
                  onClick={() => set({ lessons: form.lessons.filter((_, j) => j !== i) })}><DeleteOutlineIcon /></IconButton>
              </Stack>
              <Grid container spacing={1.5}>
                <Grid size={{ xs: 12, md: 9 }}>
                  <TextField label="Título" fullWidth size="small" value={lesson.title}
                    onChange={(e) => set({ lessons: form.lessons.map((l, j) => (j === i ? { ...l, title: e.target.value } : l)) })} />
                </Grid>
                <Grid size={{ xs: 12, md: 3 }}>
                  <TextField label="Minutos" type="number" fullWidth size="small" value={lesson.minutes}
                    onChange={(e) => set({ lessons: form.lessons.map((l, j) => (j === i ? { ...l, minutes: Number(e.target.value) } : l)) })}
                    slotProps={{ htmlInput: { min: 1, max: 30 } }} />
                </Grid>
                <Grid size={12}>
                  <TextField label="Texto (deje una línea en blanco entre párrafos)" fullWidth multiline minRows={4} value={lesson.body}
                    onChange={(e) => set({ lessons: form.lessons.map((l, j) => (j === i ? { ...l, body: e.target.value } : l)) })}
                    slotProps={{ htmlInput: { maxLength: 4000 } }} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField label="Imagen (URL https, opcional)" fullWidth size="small" value={lesson.imageUrl ?? ''}
                    onChange={(e) => set({ lessons: form.lessons.map((l, j) => (j === i ? { ...l, imageUrl: e.target.value } : l)) })} />
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                  <TextField label="Audio (URL https, opcional; si no, se lee en voz alta)" fullWidth size="small" value={lesson.audioUrl ?? ''}
                    onChange={(e) => set({ lessons: form.lessons.map((l, j) => (j === i ? { ...l, audioUrl: e.target.value } : l)) })} />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        ))}
        <Box><Button startIcon={<AddIcon />} onClick={() => set({ lessons: [...form.lessons, { title: '', body: '', minutes: 2 }] })}>Agregar lección</Button></Box>
      </Stack>

      <Divider sx={{ mb: 2 }} />
      <Typography variant="h6" component="h2" sx={{ mb: 1 }}>Cuestionario</Typography>
      <Stack spacing={1.5} sx={{ mb: 3 }}>
        {form.questions.map((question, i) => (
          <Card key={i} variant="outlined">
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                <Typography fontWeight={700}>Pregunta {i + 1}</Typography>
                <IconButton aria-label={`Quitar pregunta ${i + 1}`} disabled={form.questions.length === 1}
                  onClick={() => set({ questions: form.questions.filter((_, j) => j !== i) })}><DeleteOutlineIcon /></IconButton>
              </Stack>
              <Grid container spacing={1.5}>
                <Grid size={12}>
                  <TextField label="Enunciado" fullWidth size="small" value={question.prompt}
                    onChange={(e) => set({ questions: form.questions.map((q, j) => (j === i ? { ...q, prompt: e.target.value } : q)) })} />
                </Grid>
                <Grid size={{ xs: 12, md: 8 }}>
                  <TextField label="Opciones (una por línea, de 2 a 6)" fullWidth multiline minRows={3} value={question.options.join('\n')}
                    onChange={(e) => set({ questions: form.questions.map((q, j) => (j === i ? { ...q, options: e.target.value.split('\n') } : q)) })} />
                </Grid>
                <Grid size={{ xs: 12, md: 4 }}>
                  <TextField select label="Respuesta correcta" fullWidth size="small" value={question.correctIndex}
                    onChange={(e) => set({ questions: form.questions.map((q, j) => (j === i ? { ...q, correctIndex: Number(e.target.value) } : q)) })}>
                    {question.options.map((option, oi) => (
                      <MenuItem key={oi} value={oi}>{option.trim() || `Opción ${oi + 1}`}</MenuItem>
                    ))}
                  </TextField>
                </Grid>
                <Grid size={12}>
                  <TextField label="Explicación (se muestra al calificar, opcional)" fullWidth size="small" value={question.explanation ?? ''}
                    onChange={(e) => set({ questions: form.questions.map((q, j) => (j === i ? { ...q, explanation: e.target.value } : q)) })} />
                </Grid>
              </Grid>
            </CardContent>
          </Card>
        ))}
        <Box><Button startIcon={<AddIcon />} onClick={() => set({ questions: [...form.questions, { prompt: '', options: ['', ''], correctIndex: 0 }] })}>Agregar pregunta</Button></Box>
      </Stack>
      {save.isError && <Alert severity="error" sx={{ mb: 2 }}>{toAppError(save.error).message}</Alert>}
      <Stack direction="row" spacing={1.5}>
        <Button variant="contained" size="large" disabled={save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? 'Guardando…' : 'Guardar curso'}
        </Button>
        <Button onClick={onClose}>Cancelar</Button>
      </Stack>
    </>
  );
}
