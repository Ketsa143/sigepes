package com.sigepes.backend.inspections;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionPhotoRepository extends JpaRepository<InspectionParts.PhotoEntity, String> {

    List<InspectionParts.PhotoEntity> findByInspectionIdOrderByPositionAsc(String inspectionId);
}
