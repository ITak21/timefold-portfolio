package com.example.timefold_portfolio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name="value_m60")
@Getter
@Setter
@NoArgsConstructor
public class ValueM60 {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY) Long id;
    String element;
    String measurement;
    @Column(name="source_id") String sourceId;
    @Column(name="data_id") Long dataId;
    @Column(name="collect_dt")
    LocalDate collectDt;
    @Column(name="hour_of_day") Integer hourOfDay;
    @Lob @Column(name="value_json") String valueJson;
}
