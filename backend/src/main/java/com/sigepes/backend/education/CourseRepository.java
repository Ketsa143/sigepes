package com.sigepes.backend.education;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.CourseEntity;

public interface CourseRepository extends JpaRepository<CourseEntity, String> {

    List<CourseEntity> findByStatusOrderByTitleAsc(CourseEntity.Status status);

    List<CourseEntity> findAllByOrderByTitleAsc();

    boolean existsByTitleIgnoreCase(String title);
}
