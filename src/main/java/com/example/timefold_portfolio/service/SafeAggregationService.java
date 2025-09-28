package com.example.timefold_portfolio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class SafeAggregationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public int aggregateDay(LocalDate day) {
        long t0 = System.nanoTime();
        log.info("AGG start: day={}", day);

        Map<String, Map<String, Double>> acc = new HashMap<>(120_000);
        LongAdder scanned = new LongAdder();

        for (int hour = 0; hour < 24; hour++) {
            long h0 = System.nanoTime();
            jdbc.query("""
        SELECT element, measurement, source_id, value_json
        FROM value_m60 WHERE collect_dt=? AND hour_of_day=?
      """, rs -> {
                String key = rs.getString(1)+'|'+rs.getString(2)+'|'+rs.getString(3);
                Map<String, Double> m = parse(rs.getString(4));
                Map<String, Double> bucket = acc.computeIfAbsent(key, k->new HashMap<>());
                m.forEach((k,v)-> bucket.merge(k, v==null?0.0:v, Double::sum));
                scanned.increment();
            }, day, hour);
            long ms = (System.nanoTime() - h0) / 1_000_000;
            log.info("AGG hour scanned: day={}, hour={}, rowsScannedSoFar={}, groups={}," +
                    " elapsedMs={}", day, hour, scanned.longValue(), acc.size(), ms);
        }

        // 평균 계산 & upsert
        List<Object[]> upserts = new ArrayList<>(acc.size());
        acc.forEach((key,sum)->{
            Map<String, Double> avg = new HashMap<>();
            sum.forEach((k,v)-> avg.put(k, v/24.0));
            String[] p = key.split("\\|",3);
            upserts.add(new Object[]{ p[0], p[1], p[2], day, toJson(avg) });
        });

        int total = 0, batch = 1000, idx = 0;
        String sql = """
      INSERT INTO value_1d(element,measurement,source_id,collect_dt,value_json)
      VALUES (?,?,?,?,?)
      ON DUPLICATE KEY UPDATE value_json=VALUES(value_json), aggregated_at=CURRENT_TIMESTAMP
    """;
        for (int i=0;i<upserts.size();i+=batch){
            int[] r = jdbc.batchUpdate(sql, upserts.subList(i, Math.min(i+batch, upserts.size())));
            total += Arrays.stream(r).sum();
            idx++;
            if (idx % 10 == 0) { // 10배치마다 진행률
                log.info("AGG upsert progress: day={}, batchesDone={}, rowsUpsertedSoFar={}",
                        day, idx, total);
            }
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("AGG finished: day={}, groups={}, rowsScanned={}, rowsUpserted={}, elapsedMs={}",
                day, acc.size(), scanned.longValue(), total, totalMs);
        return total;
    }

    private Map<String, Double> parse(String json){
        try { return om.readValue(json, new TypeReference<Map<String, Double>>(){}); }
        catch(Exception e){ return Collections.emptyMap(); }
    }
    private String toJson(Map<String, Double> m){
        try { return om.writeValueAsString(m); } catch(Exception e){ return "{}"; }
    }
}
