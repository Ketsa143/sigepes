import type { CourseDetail } from '../api/domain.service';

/**
 * HU-19 · AC-19.2 · Cursos para ver sin señal y lecciones vistas pendientes de sincronizar.
 *
 * <p>Cada curso abierto con señal se guarda entero (texto y preguntas, sin respuestas). Las lecciones
 * que se terminan sin señal quedan en una cola; el servidor es idempotente, así que reenviarlas no
 * duplica el avance.
 */
const COURSE_PREFIX = 'sigepes-course-';
const QUEUE_KEY = 'sigepes-lesson-queue';

export interface QueuedLesson {
  courseId: string;
  lessonId: string;
}

export function cacheCourse(course: CourseDetail): void {
  try {
    localStorage.setItem(COURSE_PREFIX + course.id, JSON.stringify(course));
  } catch {
    // Sin espacio: la lección se sigue viendo, solo no queda copia.
  }
}

export function cachedCourse(id: string): CourseDetail | null {
  try {
    const raw = localStorage.getItem(COURSE_PREFIX + id);
    return raw ? (JSON.parse(raw) as CourseDetail) : null;
  } catch {
    return null;
  }
}

export function queuedLessons(): QueuedLesson[] {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    return raw ? (JSON.parse(raw) as QueuedLesson[]) : [];
  } catch {
    return [];
  }
}

export function queueLesson(item: QueuedLesson): void {
  const queue = queuedLessons().filter((q) => !(q.courseId === item.courseId && q.lessonId === item.lessonId));
  try {
    localStorage.setItem(QUEUE_KEY, JSON.stringify([...queue, item]));
  } catch {
    // Sin almacenamiento no hay cola: el avance se marcará al repetir la lección con señal.
  }
}

export function setQueuedLessons(queue: QueuedLesson[]): void {
  try {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  } catch {
    // Nada que hacer sin almacenamiento.
  }
}
