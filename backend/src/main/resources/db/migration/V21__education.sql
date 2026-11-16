-- =====================================================================
-- SIGEPES · Sprint 3 · HU-19 · Módulo educativo de pesca responsable
--   AC-19.1: cursos con lecciones cortas (texto, imagen, audio) y un
--            cuestionario final.
--   AC-19.2: avance por usuario y por lección.
--   AC-19.3: con 70 % o más se emite una constancia verificable por código.
--   AC-19.4: el ADMIN crea, edita y publica cursos sin desplegar código: el
--            contenido vive en estas tablas, no en el código.
-- =====================================================================

CREATE TABLE courses (
    id           VARCHAR(40)    NOT NULL,
    title        VARCHAR(160)   NOT NULL,
    summary      VARCHAR(600)   NOT NULL,
    icon         VARCHAR(30)    NOT NULL,
    status       VARCHAR(10)    NOT NULL,
    version      INTEGER        NOT NULL,
    pass_score   INTEGER        NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP      NOT NULL,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uq_courses_title UNIQUE (title),
    CONSTRAINT ck_courses_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_courses_pass CHECK (pass_score BETWEEN 1 AND 100)
);

CREATE TABLE course_lessons (
    id           VARCHAR(40)    NOT NULL,
    course_id    VARCHAR(40)    NOT NULL,
    position     INTEGER        NOT NULL,
    title        VARCHAR(160)   NOT NULL,
    body         VARCHAR(4000)  NOT NULL,
    image_url    VARCHAR(400),
    audio_url    VARCHAR(400),
    minutes      INTEGER        NOT NULL,
    CONSTRAINT pk_course_lessons PRIMARY KEY (id),
    CONSTRAINT fk_course_lessons_course FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE INDEX ix_course_lessons_course ON course_lessons (course_id, position);

CREATE TABLE course_questions (
    id             VARCHAR(40)    NOT NULL,
    course_id      VARCHAR(40)    NOT NULL,
    position       INTEGER        NOT NULL,
    prompt         VARCHAR(400)   NOT NULL,
    options_json   VARCHAR(2000)  NOT NULL,
    correct_index  INTEGER        NOT NULL,
    explanation    VARCHAR(600),
    CONSTRAINT pk_course_questions PRIMARY KEY (id),
    CONSTRAINT fk_course_questions_course FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE INDEX ix_course_questions_course ON course_questions (course_id, position);

-- AC-19.2: avance por lección (idempotente: una fila por usuario y lección).
CREATE TABLE lesson_progress (
    id            VARCHAR(40)   NOT NULL,
    user_id       VARCHAR(40)   NOT NULL,
    course_id     VARCHAR(40)   NOT NULL,
    lesson_id     VARCHAR(40)   NOT NULL,
    completed_at  TIMESTAMP     NOT NULL,
    CONSTRAINT pk_lesson_progress PRIMARY KEY (id),
    CONSTRAINT uq_lesson_progress UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_progress_course FOREIGN KEY (course_id) REFERENCES courses (id)
);

CREATE TABLE quiz_attempts (
    id           VARCHAR(40)   NOT NULL,
    user_id      VARCHAR(40)   NOT NULL,
    course_id    VARCHAR(40)   NOT NULL,
    score        INTEGER       NOT NULL,
    passed       BOOLEAN       NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    CONSTRAINT pk_quiz_attempts PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_quiz_attempts_course FOREIGN KEY (course_id) REFERENCES courses (id)
);

-- AC-19.3: constancia. Guarda el nombre y el título tal como estaban al emitirla.
CREATE TABLE course_certificates (
    id              VARCHAR(40)    NOT NULL,
    code            VARCHAR(20)    NOT NULL,
    user_id         VARCHAR(40)    NOT NULL,
    course_id       VARCHAR(40)    NOT NULL,
    holder_name     VARCHAR(160)   NOT NULL,
    course_title    VARCHAR(160)   NOT NULL,
    course_version  INTEGER        NOT NULL,
    score           INTEGER        NOT NULL,
    issued_at       TIMESTAMP      NOT NULL,
    CONSTRAINT pk_course_certificates PRIMARY KEY (id),
    CONSTRAINT uq_course_certificates_code UNIQUE (code),
    CONSTRAINT uq_course_certificates_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_course_certificates_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_course_certificates_course FOREIGN KEY (course_id) REFERENCES courses (id)
);
