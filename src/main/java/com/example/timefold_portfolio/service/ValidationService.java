package com.example.timefold_portfolio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class ValidationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    public Map<String,Object> verify(LocalDate day, int sampleLimit){
        // 1) 기대값 계산(집계 로직과 동일)
        Map<String, Map<String, Double>> expected = new HashMap<>();
        for(int hour=0; hour<24; hour++){
            jdbc.query("""
        SELECT element,measurement,source_id,value_json
        FROM value_m60 WHERE collect_dt=? AND hour_of_day=?
      """, rs -> {
                String key = rs.getString(1)+'|'+rs.getString(2)+'|'+rs.getString(3);
                Map<String, Double> m = parse(rs.getString(4));
                Map<String, Double> bucket = expected.computeIfAbsent(key, k->new HashMap<>());
                m.forEach((k,v)-> bucket.merge(k, v==null?0.0:v, Double::sum));
            }, day, hour);
        }
        expected.replaceAll((k,v)->{
            Map<String, Double> avg = new HashMap<>();
            v.forEach((kk,vv)-> avg.put(kk, vv/24.0));
            return avg;
        });

        // 2) 저장값 로드
        Map<String, Map<String, Double>> stored = new HashMap<>();
        jdbc.query("""
      SELECT element,measurement,source_id,value_json
      FROM value_1d WHERE collect_dt=?
    """, rs -> {
            String key = rs.getString(1)+'|'+rs.getString(2)+'|'+rs.getString(3);
            stored.put(key, parse(rs.getString(4)));
        }, day);

        // 3) 비교
        int checked=0, mismatches=0;
        List<Map<String,Object>> samples = new ArrayList<>();
        for (var e : expected.entrySet()){
            checked++;
            Map<String, Double> a = e.getValue();
            Map<String, Double> b = stored.getOrDefault(e.getKey(), Collections.emptyMap());
            if (!approximatelyEqual(a, b, 1e-9)){ // float 오차 허용
                mismatches++;
                if (samples.size() < sampleLimit){
                    samples.add(Map.of(
                            "group", e.getKey(),
                            "expected", a, "stored", b
                    ));
                }
            }
        }
        return Map.of("day", day, "groupsChecked", checked, "mismatches", mismatches, "samples", samples);
    }

    private Map<String, Double> parse(String json){
        try { return om.readValue(json, new TypeReference<Map<String, Double>>(){}); }
        catch(Exception e){ return Collections.emptyMap(); }
    }
    private boolean approximatelyEqual(Map<String, Double> a, Map<String, Double> b, double eps){
        if (a.size() != b.size()) return false;
        for (var k : a.keySet()){
            double ax = a.getOrDefault(k,0.0), bx = b.getOrDefault(k,0.0);
            if (Math.abs(ax-bx) > eps) return false;
        }
        return true;
    }
}
