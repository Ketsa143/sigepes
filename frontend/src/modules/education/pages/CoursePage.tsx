import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CloudOffOutlinedIcon from '@mui/icons-material/CloudOffOutlined';
import StopCircleOutlinedIcon from '@mui/icons-material/StopCircleOutlined';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined';
import {
  Alert, Box, Button, Card, CardContent, Chip, CircularProgress, FormControlLabel, LinearProgress, Radio, RadioGroup,
  Stack, Typography,
} from '@mui/material';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { domainService, type AttemptResult, type CourseDetail } from '../../../shared/api/domain.service';
import { toAppError } from '../../../shared/errors/AppError';
import { saveBlob } from '../../../shared/files/saveBlob';
import { useOnlineStatus } from '../../../shared/hooks/useOnlineStatus';
import { cacheCourse, cachedCourse, queueLesson } from '../../../shared/storage/courseOfflineStore';
import { CourseIcon } from '../courseIcons';

/** Lee en voz alta con la voz en español del teléfono (AC-19.5: «Escuchar», sin archivos de audio). */
function useSpeech() {
  const supported = typeof window !== 'undefined' && 'speechSynthesis' in window;
  const [speaking, setSpeaking] = useState(false);
  useEffect(() => () => { if (supported) window.speechSynthesis.cancel(); }, [supported]);
  const speak = (text: string) => {
    if (!supported) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'es-CO';
    utterance.rate = 0.95;
    const voice = window.speechSynthesis.getVoices().find((v) => v.lang.startsWith('es'));
    if (voice) utterance.voice = voice;
    utterance.onend = () => setSpeaking(false);
    utterance.onerror = () => setSpeaking(false);
    setSpeaking(true);
    window.speechSynthesis.speak(utterance);
  };
  const stop = () => {
    if (supported) window.speechSynthesis.cancel();
    setSpeaking(false);
  };
  return { supported, speaking, speak, stop };
}

/**
 * HU-19 · Curso: una lección por pantalla, en letra grande y con «Escuchar»; al final, el cuestionario.
 * Con señal el curso se guarda entero en el teléfono; sin señal se ve la copia y el avance se envía
 * después (AC-19.2).
 */
export function CoursePage() {
  const { id = '' } = useParams();
  const online = useOnlineStatus();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['course', id],
    queryFn: async () => {
      try {
        const course = await domainService.course(id);
        cacheCourse(course);
        return { course, offline: false };
      } catch (error) {
        const copy = cachedCourse(id);
        if (copy) return { course: copy, offline: true };
        throw error;
      }
    },
    retry: 0,
  });
  const course = query.data?.course;
  const [index, setIndex] = useState<number | null>(null);
  const firstPending = course ? Math.max(0, course.lessons.findIndex((l) => !l.completed)) : 0;
  const current = index ?? (course && course.lessons.every((l) => l.completed) ? course.lessons.length : firstPending);

  const setCourse = (next: CourseDetail) => {
    cacheCourse(next);
    queryClient.setQueryData(['course', id], { course: next, offline: false });
    void queryClient.invalidateQueries({ queryKey: ['courses'] });
  };
  const complete = useMutation({
    mutationFn: async (lessonId: string) => {
      if (!online) throw new Error('offline');
      return domainService.completeLesson(id, lessonId);
    },
    onSuccess: setCourse,
    onError: (_, lessonId) => {
      // Sin señal: queda en la cola y se marca en la copia local.
      queueLesson({ courseId: id, lessonId });
      if (course) {
        const next = { ...course, lessons: course.lessons.map((l) => (l.id === lessonId ? { ...l, completed: true } : l)) };
        next.completedLessons = next.lessons.filter((l) => l.completed).length;
        cacheCourse(next);
        queryClient.setQueryData(['course', id], { course: next, offline: true });
      }
    },
  });

  if (query.isLoading) return <CircularProgress size={28} />;
  if (query.isError || !course) {
    return <Alert severity="error">{query.error ? toAppError(query.error).message : 'El curso no existe.'}</Alert>;
  }
  const total = course.lessons.length;
  const inQuiz = current >= total;
  const lesson = inQuiz ? null : course.lessons[current];

  return (
    <Box sx={{ maxWidth: 820 }}>
      <Button component={RouterLink} to="/app/education" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>Cursos</Button>
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 1 }}>
        <CourseIcon icon={course.icon} color="primary" sx={{ fontSize: 40 }} />
        <Typography variant="h4" component="h1" sx={{ fontSize: { xs: '1.6rem', md: '2rem' } }}>{course.title}</Typography>
      </Stack>
      {query.data?.offline && (
        <Alert severity="info" icon={<CloudOffOutlinedIcon />} sx={{ mb: 2 }}>
          Sin conexión: está viendo la copia guardada en el teléfono. Su avance se enviará al volver la señal.
        </Alert>
      )}
      <LinearProgress variant="determinate" value={(Math.min(current, total) / total) * 100}
        sx={{ height: 10, borderRadius: 5, mb: 1 }} aria-label={`Lección ${Math.min(current + 1, total)} de ${total}`} />
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {inQuiz ? 'Cuestionario final' : `Lección ${current + 1} de ${total} · ${lesson!.minutes} min`}
      </Typography>

      {lesson && (
        <LessonCard
          key={lesson.id}
          lesson={lesson}
          busy={complete.isPending}
          onBack={current > 0 ? () => setIndex(current - 1) : undefined}
          onDone={() => {
            if (!lesson.completed) complete.mutate(lesson.id);
            setIndex(current + 1);
          }}
        />
      )}
      {inQuiz && <Quiz course={course} onReview={() => setIndex(0)} onPassed={(result) => {
        setCourse({ ...course, passed: true, certificateId: result.certificateId, certificateCode: result.certificateCode });
      }} />}
    </Box>
  );
}

function LessonCard({ lesson, busy, onBack, onDone }: {
  lesson: CourseDetail['lessons'][number]; busy: boolean; onBack?: () => void; onDone: () => void;
}) {
  const speech = useSpeech();
  return (
    <Card>
      <CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2} sx={{ mb: 2 }}>
          <Typography variant="h5" component="h2" sx={{ fontWeight: 700 }}>{lesson.title}</Typography>
          {lesson.completed && <Chip color="success" icon={<CheckCircleIcon />} label="Vista" />}
        </Stack>
        {lesson.imageUrl && (
          <Box component="img" src={lesson.imageUrl} alt="" sx={{ width: '100%', borderRadius: 2, mb: 2, maxHeight: 320, objectFit: 'cover' }} />
        )}
        {lesson.audioUrl ? (
          <Box component="audio" controls src={lesson.audioUrl} sx={{ width: '100%', mb: 2 }} aria-label={`Audio de ${lesson.title}`} />
        ) : speech.supported && (
          <Button size="large" variant="outlined" sx={{ mb: 2 }}
            startIcon={speech.speaking ? <StopCircleOutlinedIcon /> : <VolumeUpIcon />}
            onClick={() => (speech.speaking ? speech.stop() : speech.speak(`${lesson.title}. ${lesson.body}`))}>
            {speech.speaking ? 'Detener' : 'Escuchar'}
          </Button>
        )}
        {lesson.body.split(/\n\s*\n/).map((paragraph, i) => (
          <Typography key={i} sx={{ fontSize: { xs: '1.15rem', md: '1.25rem' }, lineHeight: 1.6, mb: 2 }}>{paragraph}</Typography>
        ))}
        <Stack direction="row" spacing={1.5} sx={{ mt: 3 }}>
          {onBack && <Button size="large" onClick={() => { speech.stop(); onBack(); }}>Anterior</Button>}
          <Button size="large" variant="contained" disabled={busy} onClick={() => { speech.stop(); onDone(); }}>
            Entendido, seguir
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

function Quiz({ course, onReview, onPassed }: { course: CourseDetail; onReview: () => void; onPassed: (result: AttemptResult) => void }) {
  const online = useOnlineStatus();
  const [answers, setAnswers] = useState<(number | null)[]>(() => course.questions.map(() => null));
  const submit = useMutation({
    mutationFn: () => domainService.submitQuiz(course.id, answers.map((a) => a ?? -1)),
    onSuccess: (result) => { if (result.passed) onPassed(result); },
  });
  const pdf = useMutation({
    mutationFn: (certificateId: string) => domainService.certificatePdf(certificateId),
    onSuccess: (blob) => saveBlob(blob, `constancia-${course.certificateCode ?? submit.data?.certificateCode ?? 'sigepes'}.pdf`),
  });
  const result = submit.data;
  const certificateId = result?.certificateId ?? course.certificateId;

  return (
    <Card>
      <CardContent sx={{ p: { xs: 2.5, md: 4 } }}>
        <Typography variant="h5" component="h2" sx={{ fontWeight: 700, mb: 1 }}>Cuestionario</Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          Aprueba con {course.passScore} % o más. Puede repetirlo las veces que quiera.
        </Typography>
        {course.passed && !result && (
          <Alert severity="success" icon={<WorkspacePremiumOutlinedIcon />} sx={{ mb: 3 }}>
            Ya aprobó este curso. Constancia {course.certificateCode}.
          </Alert>
        )}
        <Stack spacing={3}>
          {course.questions.map((question, qi) => {
            const feedback = result?.answers[qi];
            return (
              <Box key={question.id} component="fieldset" sx={{ border: 0, p: 0, m: 0 }}>
                <Typography component="legend" sx={{ fontSize: '1.15rem', fontWeight: 600, mb: 1 }}>
                  {qi + 1}. {question.prompt}
                </Typography>
                <RadioGroup value={answers[qi] ?? ''} onChange={(e) => setAnswers((current) => current.map((a, i) => (i === qi ? Number(e.target.value) : a)))}>
                  {question.options.map((option, oi) => (
                    <FormControlLabel key={oi} value={oi} control={<Radio />} disabled={!!result}
                      label={<Typography sx={{ fontSize: '1.1rem' }}>{option}</Typography>} />
                  ))}
                </RadioGroup>
                {feedback && (
                  <Alert severity={feedback.correct ? 'success' : 'warning'} sx={{ mt: 1 }}>
                    {feedback.correct ? 'Correcto.' : `La respuesta correcta es: «${question.options[feedback.correctIndex]}».`}
                    {feedback.explanation ? ` ${feedback.explanation}` : ''}
                  </Alert>
                )}
              </Box>
            );
          })}
        </Stack>
        {submit.isError && <Alert severity="error" sx={{ mt: 2 }}>{toAppError(submit.error).message}</Alert>}
        {result && (
          <Alert severity={result.passed ? 'success' : 'info'} sx={{ mt: 3 }} icon={result.passed ? <WorkspacePremiumOutlinedIcon /> : undefined}>
            {result.passed
              ? `¡Aprobado con ${result.score} %! Su constancia es la ${result.certificateCode}.`
              : `Obtuvo ${result.score} %. Necesita ${result.passScore} %: repase las lecciones e inténtelo de nuevo.`}
          </Alert>
        )}
        <Stack direction="row" spacing={1.5} sx={{ mt: 3 }} flexWrap="wrap" useFlexGap>
          {!result && (
            <Button size="large" variant="contained" disabled={!online || submit.isPending || answers.some((a) => a == null)}
              onClick={() => submit.mutate()}>
              {online ? 'Calificar' : 'Necesita señal para calificar'}
            </Button>
          )}
          {result && !result.passed && <Button size="large" variant="contained" onClick={() => { submit.reset(); setAnswers(course.questions.map(() => null)); }}>Intentar de nuevo</Button>}
          {certificateId && (
            <Button size="large" variant="outlined" startIcon={<WorkspacePremiumOutlinedIcon />} disabled={pdf.isPending}
              onClick={() => pdf.mutate(certificateId)}>
              Descargar constancia
            </Button>
          )}
          <Button size="large" onClick={onReview}>Repasar lecciones</Button>
        </Stack>
      </CardContent>
    </Card>
  );
}
