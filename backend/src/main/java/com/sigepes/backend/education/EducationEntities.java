package com.sigepes.backend.education;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** HU-19 · Entidades del módulo educativo. El contenido vive en la base para que el ADMIN lo gestione. */
public final class EducationEntities {

    private EducationEntities() {
    }

    @Entity(name = "Course")
    @Table(name = "courses")
    public static class CourseEntity {

        public enum Status { DRAFT, PUBLISHED }

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "title", length = 160, nullable = false)
        private String title;

        @Column(name = "summary", length = 600, nullable = false)
        private String summary;

        @Column(name = "icon", length = 30, nullable = false)
        private String icon;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", length = 10, nullable = false)
        private Status status = Status.DRAFT;

        @Column(name = "version", nullable = false)
        private int version = 1;

        @Column(name = "pass_score", nullable = false)
        private int passScore = 70;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        protected CourseEntity() {
        }

        CourseEntity(String id, String title, String summary, String icon, int passScore) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.icon = icon;
            this.passScore = passScore;
            this.createdAt = Instant.now();
            this.updatedAt = createdAt;
        }

        void update(String title, String summary, String icon, int passScore) {
            this.title = title;
            this.summary = summary;
            this.icon = icon;
            this.passScore = passScore;
            this.version++;
            this.updatedAt = Instant.now();
        }

        void setStatus(Status status) {
            this.status = status;
            this.updatedAt = Instant.now();
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public String getIcon() {
            return icon;
        }

        public Status getStatus() {
            return status;
        }

        public int getVersion() {
            return version;
        }

        public int getPassScore() {
            return passScore;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }
    }

    @Entity(name = "CourseLesson")
    @Table(name = "course_lessons")
    public static class LessonEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "course_id", length = 40, nullable = false)
        private String courseId;

        @Column(name = "position", nullable = false)
        private int position;

        @Column(name = "title", length = 160, nullable = false)
        private String title;

        @Column(name = "body", length = 4000, nullable = false)
        private String body;

        @Column(name = "image_url", length = 400)
        private String imageUrl;

        @Column(name = "audio_url", length = 400)
        private String audioUrl;

        @Column(name = "minutes", nullable = false)
        private int minutes;

        protected LessonEntity() {
        }

        LessonEntity(String id, String courseId, int position, String title, String body, String imageUrl,
                String audioUrl, int minutes) {
            this.id = id;
            this.courseId = courseId;
            this.position = position;
            this.title = title;
            this.body = body;
            this.imageUrl = imageUrl;
            this.audioUrl = audioUrl;
            this.minutes = minutes;
        }

        public String getId() {
            return id;
        }

        public String getCourseId() {
            return courseId;
        }

        public int getPosition() {
            return position;
        }

        public String getTitle() {
            return title;
        }

        public String getBody() {
            return body;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getAudioUrl() {
            return audioUrl;
        }

        public int getMinutes() {
            return minutes;
        }
    }

    @Entity(name = "CourseQuestion")
    @Table(name = "course_questions")
    public static class QuestionEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "course_id", length = 40, nullable = false)
        private String courseId;

        @Column(name = "position", nullable = false)
        private int position;

        @Column(name = "prompt", length = 400, nullable = false)
        private String prompt;

        @Column(name = "options_json", length = 2000, nullable = false)
        private String optionsJson;

        @Column(name = "correct_index", nullable = false)
        private int correctIndex;

        @Column(name = "explanation", length = 600)
        private String explanation;

        protected QuestionEntity() {
        }

        QuestionEntity(String id, String courseId, int position, String prompt, String optionsJson, int correctIndex,
                String explanation) {
            this.id = id;
            this.courseId = courseId;
            this.position = position;
            this.prompt = prompt;
            this.optionsJson = optionsJson;
            this.correctIndex = correctIndex;
            this.explanation = explanation;
        }

        public String getId() {
            return id;
        }

        public int getPosition() {
            return position;
        }

        public String getPrompt() {
            return prompt;
        }

        public String getOptionsJson() {
            return optionsJson;
        }

        public int getCorrectIndex() {
            return correctIndex;
        }

        public String getExplanation() {
            return explanation;
        }
    }

    @Entity(name = "LessonProgress")
    @Table(name = "lesson_progress")
    public static class ProgressEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "user_id", length = 40, nullable = false)
        private String userId;

        @Column(name = "course_id", length = 40, nullable = false)
        private String courseId;

        @Column(name = "lesson_id", length = 40, nullable = false)
        private String lessonId;

        @Column(name = "completed_at", nullable = false)
        private Instant completedAt;

        protected ProgressEntity() {
        }

        ProgressEntity(String id, String userId, String courseId, String lessonId, Instant completedAt) {
            this.id = id;
            this.userId = userId;
            this.courseId = courseId;
            this.lessonId = lessonId;
            this.completedAt = completedAt;
        }

        public String getLessonId() {
            return lessonId;
        }

        public String getCourseId() {
            return courseId;
        }
    }

    @Entity(name = "QuizAttempt")
    @Table(name = "quiz_attempts")
    public static class AttemptEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "user_id", length = 40, nullable = false)
        private String userId;

        @Column(name = "course_id", length = 40, nullable = false)
        private String courseId;

        @Column(name = "score", nullable = false)
        private int score;

        @Column(name = "passed", nullable = false)
        private boolean passed;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        protected AttemptEntity() {
        }

        AttemptEntity(String id, String userId, String courseId, int score, boolean passed) {
            this.id = id;
            this.userId = userId;
            this.courseId = courseId;
            this.score = score;
            this.passed = passed;
            this.createdAt = Instant.now();
        }

        public int getScore() {
            return score;
        }

        public boolean isPassed() {
            return passed;
        }
    }

    @Entity(name = "CourseCertificate")
    @Table(name = "course_certificates")
    public static class CertificateEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "code", length = 20, nullable = false)
        private String code;

        @Column(name = "user_id", length = 40, nullable = false)
        private String userId;

        @Column(name = "course_id", length = 40, nullable = false)
        private String courseId;

        @Column(name = "holder_name", length = 160, nullable = false)
        private String holderName;

        @Column(name = "course_title", length = 160, nullable = false)
        private String courseTitle;

        @Column(name = "course_version", nullable = false)
        private int courseVersion;

        @Column(name = "score", nullable = false)
        private int score;

        @Column(name = "issued_at", nullable = false)
        private Instant issuedAt;

        protected CertificateEntity() {
        }

        CertificateEntity(String id, String code, String userId, String courseId, String holderName, String courseTitle,
                int courseVersion, int score) {
            this.id = id;
            this.code = code;
            this.userId = userId;
            this.courseId = courseId;
            this.holderName = holderName;
            this.courseTitle = courseTitle;
            this.courseVersion = courseVersion;
            this.score = score;
            this.issuedAt = Instant.now();
        }

        public String getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public String getUserId() {
            return userId;
        }

        public String getCourseId() {
            return courseId;
        }

        public String getHolderName() {
            return holderName;
        }

        public String getCourseTitle() {
            return courseTitle;
        }

        public int getScore() {
            return score;
        }

        public Instant getIssuedAt() {
            return issuedAt;
        }
    }
}
