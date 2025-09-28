package com.example.timefold_portfolio.repository;

import com.example.timefold_portfolio.entity.ValueM60;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface ValueM60Repo extends JpaRepository<ValueM60,Long>{
    List<ValueM60> findByCollectDt(LocalDate dt);
}
