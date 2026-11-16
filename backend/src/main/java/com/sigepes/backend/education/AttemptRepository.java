package com.sigepes.backend.education;


import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.AttemptEntity;

public interface AttemptRepository extends JpaRepository<AttemptEntity, String> {

    long countByUserIdAndCourseId(String userId, String courseId);
}
