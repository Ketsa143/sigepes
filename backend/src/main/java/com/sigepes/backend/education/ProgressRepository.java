package com.sigepes.backend.education;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.ProgressEntity;

public interface ProgressRepository extends JpaRepository<ProgressEntity, String> {

    List<ProgressEntity> findByUserIdAndCourseId(String userId, String courseId);

    List<ProgressEntity> findByUserId(String userId);

    boolean existsByUserIdAndLessonId(String userId, String lessonId);

    void deleteByCourseId(String courseId);
}
