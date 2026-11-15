package com.sigepes.backend.rms;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatchEffortHistoryRepository extends JpaRepository<CatchEffortHistoryEntity, String> {

    List<CatchEffortHistoryEntity> findByYearLessThanOrderBySpeciesIdAscYearAsc(int year);
}
