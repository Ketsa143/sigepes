package com.sigepes.backend.education;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.education.EducationEntities.CertificateEntity;

public interface CertificateRepository extends JpaRepository<CertificateEntity, String> {

    Optional<CertificateEntity> findByUserIdAndCourseId(String userId, String courseId);

    Optional<CertificateEntity> findByCodeIgnoreCase(String code);

    List<CertificateEntity> findByUserId(String userId);
}
