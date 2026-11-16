package com.sigepes.backend.education;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.QuestionEntity;

public interface QuestionRepository extends JpaRepository<QuestionEntity, String> {

    List<QuestionEntity> findByCourseIdOrderByPositionAsc(String courseId);

    void deleteByCourseId(String courseId);
}
