package com.example.timefold_portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Entity
@Table(name = "value_1d")
@Getter
@Setter
@NoArgsConstructor
public class Value1D {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String element;
    private String measurement;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "collect_dt")
    private LocalDate collectDt;

    @Lob @Column(name = "value_json")
    private String valueJson;

    @Column(name = "aggregated_at")
    private LocalDateTime aggregatedAt;
}
