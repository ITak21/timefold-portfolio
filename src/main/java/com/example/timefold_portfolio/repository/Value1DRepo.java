package com.example.timefold_portfolio.repository;

import com.example.timefold_portfolio.entity.Value1D;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;


public interface Value1DRepo extends JpaRepository<Value1D, Long> {
    Optional<Value1D> findByElementAndMeasurementAndSourceIdAndCollectDt(
            String element, String measurement, String sourceId, LocalDate collectDt);
}
