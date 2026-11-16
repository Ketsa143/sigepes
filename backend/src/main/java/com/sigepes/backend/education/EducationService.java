package com.sigepes.backend.education;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.Codes;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.education.EducationDtos.AdminCourse;
import com.sigepes.backend.education.EducationDtos.AnswerResult;
import com.sigepes.backend.education.EducationDtos.AttemptResult;
import com.sigepes.backend.education.EducationDtos.CourseCard;
import com.sigepes.backend.education.EducationDtos.CourseDetail;
import com.sigepes.backend.education.EducationDtos.CourseInput;
import com.sigepes.backend.education.EducationDtos.LessonInput;
import com.sigepes.backend.education.EducationDtos.LessonView;
import com.sigepes.backend.education.EducationDtos.PublicCertificate;
import com.sigepes.backend.education.EducationDtos.QuestionInput;
import com.sigepes.backend.education.EducationDtos.QuestionView;
import com.sigepes.backend.education.EducationEntities.AttemptEntity;
import com.sigepes.backend.education.EducationEntities.CertificateEntity;
import com.sigepes.backend.education.EducationEntities.CourseEntity;
import com.sigepes.backend.education.EducationEntities.LessonEntity;
import com.sigepes.backend.education.EducationEntities.ProgressEntity;
import com.sigepes.backend.education.EducationEntities.QuestionEntity;
import com.sigepes.backend.security.AuthenticatedUser;
import com.sigepes.backend.security.CurrentUser;
import com.sigepes.backend.users.UserRepository;

/**
 * HU-19 · Módulo educativo de pesca responsable.
 *
 * <p>Los cursos, lecciones y preguntas viven en la base (AC-19.4): el ADMIN los crea y publica sin
 * desplegar código. El cuestionario se califica en el servidor —el teléfono nunca recibe la respuesta
 * correcta antes de responder— y con la nota mínima se emite una constancia con código (AC-19.3).
 */
@Service
public class EducationService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final QuestionRepository questionRepository;
    private final ProgressRepository progressRepository;
    private final AttemptRepository attemptRepository;
    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public EducationService(CourseRepository courseRepository, LessonRepository lessonRepository,
            QuestionRepository questionRepository, ProgressRepository progressRepository,
            AttemptRepository attemptRepository, CertificateRepository certificateRepository,
            UserRepository userRepository, CurrentUser currentUser, AuditService auditService) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.progressRepository = progressRepository;
        this.attemptRepository = attemptRepository;
        this.certificateRepository = certificateRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Pescador (AC-19.1 a AC-19.3)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CourseCard> courses() {
        AuthenticatedUser actor = currentUser.require();
        Map<String, Long> done = progressRepository.findByUserId(actor.id()).stream()
                .collect(Collectors.groupingBy(ProgressEntity::getCourseId, Collectors.counting()));
        Map<String, CertificateEntity> certificates = certificateRepository.findByUserId(actor.id()).stream()
                .collect(Collectors.toMap(CertificateEntity::getCourseId, c -> c));
        return courseRepository.findByStatusOrderByTitleAsc(CourseEntity.Status.PUBLISHED).stream()
                .map(course -> {
                    List<LessonEntity> lessons = lessonRepository.findByCourseIdOrderByPositionAsc(course.getId());
                    CertificateEntity certificate = certificates.get(course.getId());
                    return new CourseCard(course.getId(), course.getTitle(), course.getSummary(), course.getIcon(),
                            lessons.size(), lessons.stream().mapToInt(LessonEntity::getMinutes).sum(),
                            done.getOrDefault(course.getId(), 0L).intValue(), certificate != null,
                            certificate == null ? null : certificate.getId(),
                            certificate == null ? null : certificate.getCode(), course.getVersion());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetail course(String courseId) {
        AuthenticatedUser actor = currentUser.require();
        CourseEntity course = published(courseId);
        Set<String> completed = progressRepository.findByUserIdAndCourseId(actor.id(), courseId).stream()
                .map(ProgressEntity::getLessonId).collect(Collectors.toSet());
        Optional<CertificateEntity> certificate = certificateRepository.findByUserIdAndCourseId(actor.id(), courseId);
        List<LessonView> lessons = lessonRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(l -> new LessonView(l.getId(), l.getPosition(), l.getTitle(), l.getBody(), l.getImageUrl(),
                        l.getAudioUrl(), l.getMinutes(), completed.contains(l.getId())))
                .toList();
        List<QuestionView> questions = questionRepository.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(q -> new QuestionView(q.getId(), q.getPosition(), q.getPrompt(), options(q.getOptionsJson())))
                .toList();
        return new CourseDetail(course.getId(), course.getTitle(), course.getSummary(), course.getIcon(),
                course.getVersion(), course.getPassScore(), lessons, questions,
                (int) lessons.stream().filter(LessonView::completed).count(), certificate.isPresent(),
                certificate.map(CertificateEntity::getId).orElse(null),
                certificate.map(CertificateEntity::getCode).orElse(null));
    }

    /** AC-19.2 · Marca la lección como vista. Idempotente: la cola sin señal puede reenviarla. */
    @Transactional
    public CourseDetail completeLesson(String courseId, String lessonId) {
        AuthenticatedUser actor = currentUser.require();
        published(courseId);
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .filter(l -> l.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.of(ErrorCode.COURSE_NOT_FOUND, "La lección no existe en este curso."));
        if (!progressRepository.existsByUserIdAndLessonId(actor.id(), lesson.getId())) {
            progressRepository.save(new ProgressEntity(Ids.generate("avc"), actor.id(), courseId, lesson.getId(),
                    Instant.now()));
        }
        return course(courseId);
    }

    /** AC-19.3 · Califica el cuestionario; con la nota mínima emite la constancia (una por curso). */
    @Transactional
    public AttemptResult attempt(String courseId, List<Integer> answers) {
        AuthenticatedUser actor = currentUser.require();
        CourseEntity course = published(courseId);
        List<QuestionEntity> questions = questionRepository.findByCourseIdOrderByPositionAsc(courseId);
        if (answers.size() != questions.size()) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "Responde las " + questions.size() + " preguntas del cuestionario.");
        }
        List<AnswerResult> results = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < questions.size(); i++) {
            QuestionEntity question = questions.get(i);
            Integer answer = answers.get(i);
            boolean ok = answer != null && answer == question.getCorrectIndex();
            if (ok) {
                correct++;
            }
            results.add(new AnswerResult(question.getId(), ok, question.getCorrectIndex(), question.getExplanation()));
        }
        int score = questions.isEmpty() ? 0 : Math.round(100f * correct / questions.size());
        boolean passed = score >= course.getPassScore();
        attemptRepository.save(new AttemptEntity(Ids.generate("int"), actor.id(), courseId, score, passed));

        Optional<CertificateEntity> certificate = certificateRepository.findByUserIdAndCourseId(actor.id(), courseId);
        if (passed && certificate.isEmpty()) {
            String holder = userRepository.findById(actor.id()).map(u -> u.getFullName()).orElse(actor.fullName());
            CertificateEntity issued = certificateRepository.save(new CertificateEntity(Ids.generate("cst"), newCode(),
                    actor.id(), courseId, holder, course.getTitle(), course.getVersion(), score));
            auditService.record(AuditActionType.CREATE, "COURSE_CERTIFICATE_ISSUED", AuditService.MODULE_EDUCATION,
                    "Constancia", issued.getId(), "Constancia " + issued.getCode() + " del curso «" + course.getTitle()
                            + "» con " + score + " %");
            certificate = Optional.of(issued);
        }
        return new AttemptResult(score, course.getPassScore(), passed, results,
                certificate.map(CertificateEntity::getId).orElse(null),
                certificate.map(CertificateEntity::getCode).orElse(null));
    }

    @Transactional(readOnly = true)
    public CertificateEntity ownCertificate(String certificateId) {
        AuthenticatedUser actor = currentUser.require();
        CertificateEntity certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CERTIFICATE_NOT_FOUND));
        if (!certificate.getUserId().equals(actor.id()) && !actor.isAuthority()) {
            throw ApiException.of(ErrorCode.CERTIFICATE_NOT_FOUND);
        }
        return certificate;
    }

    @Transactional(readOnly = true)
    public PublicCertificate verify(String code) {
        CertificateEntity certificate = certificateRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> ApiException.of(ErrorCode.CERTIFICATE_NOT_FOUND));
        return new PublicCertificate(certificate.getCode(), certificate.getHolderName(), certificate.getCourseTitle(),
                certificate.getScore(), certificate.getIssuedAt());
    }

    // ------------------------------------------------------------------
    // ADMIN (AC-19.4): crear, editar y publicar sin desplegar código
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminCourse> adminCourses() {
        return courseRepository.findAllByOrderByTitleAsc().stream().map(this::adminView).toList();
    }

    @Transactional
    public AdminCourse create(CourseInput input) {
        if (courseRepository.existsByTitleIgnoreCase(input.title().trim())) {
            throw ApiException.of(ErrorCode.COURSE_ALREADY_EXISTS);
        }
        CourseEntity saved = courseRepository.save(new CourseEntity(Ids.generate("cur"), input.title().trim(),
                input.summary().trim(), input.icon(), input.passScore()));
        replaceContent(saved.getId(), input);
        auditService.record(AuditActionType.CREATE, "COURSE_CREATED", AuditService.MODULE_EDUCATION, "Curso",
                saved.getId(), "Curso «" + saved.getTitle() + "» (borrador) con " + input.lessons().size()
                        + " lecciones y " + input.questions().size() + " preguntas");
        return adminView(saved);
    }

    /** Reemplaza el contenido y sube la versión: los teléfonos con la copia vieja la renuevan. */
    @Transactional
    public AdminCourse update(String courseId, CourseInput input) {
        CourseEntity course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiException.of(ErrorCode.COURSE_NOT_FOUND));
        if (!course.getTitle().equalsIgnoreCase(input.title().trim())
                && courseRepository.existsByTitleIgnoreCase(input.title().trim())) {
            throw ApiException.of(ErrorCode.COURSE_ALREADY_EXISTS);
        }
        course.update(input.title().trim(), input.summary().trim(), input.icon(), input.passScore());
        courseRepository.save(course);
        replaceContent(courseId, input);
        auditService.record(AuditActionType.UPDATE, "COURSE_UPDATED", AuditService.MODULE_EDUCATION, "Curso",
                courseId, "Curso «" + course.getTitle() + "» actualizado a la versión " + course.getVersion());
        return adminView(course);
    }

    @Transactional
    public AdminCourse setPublished(String courseId, boolean published) {
        CourseEntity course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiException.of(ErrorCode.COURSE_NOT_FOUND));
        course.setStatus(published ? CourseEntity.Status.PUBLISHED : CourseEntity.Status.DRAFT);
        courseRepository.save(course);
        auditService.record(AuditActionType.UPDATE, published ? "COURSE_PUBLISHED" : "COURSE_UNPUBLISHED",
                AuditService.MODULE_EDUCATION, "Curso", courseId,
                "Curso «" + course.getTitle() + "» " + (published ? "publicado" : "retirado"));
        return adminView(course);
    }

    // ------------------------------------------------------------------

    private void replaceContent(String courseId, CourseInput input) {
        for (int i = 0; i < input.questions().size(); i++) {
            QuestionInput q = input.questions().get(i);
            if (q.correctIndex() >= q.options().size()) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                        "La respuesta correcta de la pregunta " + (i + 1) + " no está entre sus opciones.");
            }
        }
        // El avance de las lecciones que se reemplazan ya no aplica; las constancias emitidas se conservan.
        progressRepository.deleteByCourseId(courseId);
        lessonRepository.deleteByCourseId(courseId);
        questionRepository.deleteByCourseId(courseId);
        lessonRepository.flush();
        int position = 1;
        for (LessonInput l : input.lessons()) {
            lessonRepository.save(new LessonEntity(Ids.generate("lec"), courseId, position++, l.title().trim(),
                    l.body().trim(), blankToNull(l.imageUrl()), blankToNull(l.audioUrl()), l.minutes()));
        }
        position = 1;
        for (QuestionInput q : input.questions()) {
            questionRepository.save(new QuestionEntity(Ids.generate("pre"), courseId, position++, q.prompt().trim(),
                    toJson(q.options().stream().map(String::trim).toList()), q.correctIndex(),
                    blankToNull(q.explanation())));
        }
    }

    private AdminCourse adminView(CourseEntity course) {
        return new AdminCourse(course.getId(), course.getTitle(), course.getSummary(), course.getIcon(),
                course.getStatus().name(), course.getVersion(), course.getPassScore(),
                lessonRepository.findByCourseIdOrderByPositionAsc(course.getId()).stream()
                        .map(l -> new LessonInput(l.getTitle(), l.getBody(), l.getImageUrl(), l.getAudioUrl(),
                                l.getMinutes()))
                        .toList(),
                questionRepository.findByCourseIdOrderByPositionAsc(course.getId()).stream()
                        .map(q -> new QuestionInput(q.getPrompt(), options(q.getOptionsJson()), q.getCorrectIndex(),
                                q.getExplanation()))
                        .toList(),
                course.getUpdatedAt());
    }

    private CourseEntity published(String courseId) {
        return courseRepository.findById(courseId)
                .filter(course -> course.getStatus() == CourseEntity.Status.PUBLISHED)
                .orElseThrow(() -> ApiException.of(ErrorCode.COURSE_NOT_FOUND));
    }

    private String newCode() {
        String code;
        do {
            code = Codes.random("CST", 8);
        } while (certificateRepository.findByCodeIgnoreCase(code).isPresent());
        return code;
    }

    static List<String> options(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
