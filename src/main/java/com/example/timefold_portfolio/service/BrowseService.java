package com.example.timefold_portfolio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class BrowseService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    /** 날짜별 element 목록 */
    public List<String> elements(LocalDate day){
        return jdbc.queryForList(
                "SELECT DISTINCT element FROM value_1d WHERE collect_dt=? ORDER BY element", String.class, day);
    }

    /** 날짜+element별 measurement 목록 + 소스 수 */
    public List<Map<String,Object>> measurements(LocalDate day, String element){
        return jdbc.query(
                "SELECT measurement, COUNT(DISTINCT source_id) AS sources " +
                        "FROM value_1d WHERE collect_dt=? AND element=? GROUP BY measurement ORDER BY measurement",
                (rs, i) -> Map.of("measurement", rs.getString(1), "sources", rs.getLong(2)),
                day, element
        );
    }

    /** 날짜+element+measurement에서 sourceId 페이지 조회(부분검색 q prefix) */
    public List<String> sources(LocalDate day, String element, String measurement, String q, int limit, int offset){
        String like = (q==null || q.isBlank()) ? "%" : q + "%";
        return jdbc.queryForList(
                "SELECT source_id FROM value_1d " +
                        "WHERE collect_dt=? AND element=? AND measurement=? AND source_id LIKE ? " +
                        "ORDER BY source_id LIMIT ? OFFSET ?",
                String.class, day, element, measurement, like, limit, offset
        );
    }

    /** 특정 그룹(한 source)의 value_1d 시리즈 조회 */
    public Map<String, Object> series(LocalDate day, String e, String m, String s){
        return jdbc.query(
                "SELECT value_json FROM value_1d WHERE collect_dt=? AND element=? AND measurement=? AND source_id=?",
                ps -> { ps.setObject(1, day); ps.setString(2, e); ps.setString(3, m); ps.setString(4, s); },
                rs -> rs.next()
                        ? Map.of("ok", true, "group", e+"|"+m+"|"+s, "data", parse(rs.getString(1)))
                        : Map.of("ok", false)
        );
    }

    public Map<String, Object> seriesAvg(LocalDate day, String e, String m){
        Map<String, Double> sum = new HashMap<>();
        LongAdder count = new LongAdder(); // ✅

        jdbc.query(
                "SELECT value_json FROM value_1d WHERE collect_dt=? AND element=? AND measurement=?",
                rs -> {
                    Map<String, Double> v = parse(rs.getString(1));
                    v.forEach((k,val)-> sum.merge(k, val==null?0.0:val, Double::sum));
                    count.increment(); // ✅ 람다 내부에서 안전
                }, day, e, m
        );

        long n = count.longValue();
        if (n==0) return Map.of("ok", false);

        Map<String, Double> avg = new TreeMap<>();
        sum.forEach((k,v)-> avg.put(k, v / (double)n));
        return Map.of("ok", true, "group", e+"|"+m+"|ALL", "data", avg, "sources", n);
    }
    private Map<String, Double> parse(String json){
        try { return om.readValue(json, new TypeReference<Map<String, Double>>(){}); }
        catch(Exception e){ return Map.of(); }
    }
}
