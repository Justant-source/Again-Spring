package com.againspring.repository;

import com.againspring.domain.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {

    Optional<DailyStats> findByStatDate(LocalDate statDate);

    List<DailyStats> findTop30ByOrderByStatDateDesc();

    List<DailyStats> findByStatDateBetweenOrderByStatDateAsc(LocalDate from, LocalDate to);
}
