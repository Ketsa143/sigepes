package com.sigepes.backend.education;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.LessonEntity;

public interface LessonRepository extends JpaRepository<LessonEntity, String> {

    List<LessonEntity> findByCourseIdOrderByPositionAsc(String courseId);

    void deleteByCourseId(String courseId);
}
