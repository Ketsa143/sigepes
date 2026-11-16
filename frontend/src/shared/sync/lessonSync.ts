import axios from 'axios';
import { domainService } from '../api/domain.service';
import { cacheCourse, queuedLessons, setQueuedLessons } from '../storage/courseOfflineStore';

let running: Promise<number> | null = null;

/** HU-19 · AC-19.2 · Envía las lecciones vistas sin señal. Devuelve cuántas se sincronizaron. */
export function flushLessonQueue(): Promise<number> {
  if (running) return running;
  running = (async () => {
    let sent = 0;
    const pending = queuedLessons();
    const left = [...pending];
    for (const item of pending) {
      try {
        cacheCourse(await domainService.completeLesson(item.courseId, item.lessonId));
        left.splice(left.indexOf(item), 1);
        sent += 1;
      } catch (error) {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined;
        if (status === 404) {
          // El curso cambió o se retiró: esa lección ya no existe.
          left.splice(left.indexOf(item), 1);
        } else {
          break;
        }
      }
    }
    setQueuedLessons(left);
    return sent;
  })().finally(() => {
    running = null;
  });
  return running;
}
