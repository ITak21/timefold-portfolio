package com.example.timefold_portfolio.service;

import com.example.timefold_portfolio.support.DbLockService;
import com.example.timefold_portfolio.support.LockService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service @RequiredArgsConstructor
public class ParallelAggregationService {
    private final JdbcTemplate jdbc;
    private final LockService lock;
    private final ObjectMapper om = new ObjectMapper();

    @Transactional
    public int aggregateDay(LocalDate day){
        return lock.withLock("agg:"+day, java.time.Duration.ofSeconds(30), () -> doAggregate(day));
    }

    private int doAggregate(LocalDate day){
        long t0 = System.nanoTime();
        int threads = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // 시간별 부분 집계를 병렬 수행(스레드별 localMap 누적)
        List<Callable<Map<String, Map<String, Double>>>> tasks = new ArrayList<>();
        for(int hour=0; hour<24; hour++){
            final int h = hour;
            tasks.add(() -> scanHour(day, h));
        }

        Map<String, Map<String, Double>> acc = new HashMap<>(120_000);
        LongAdder scanned = new LongAdder();

        try {
            List<Future<Map<String, Map<String, Double>>>> futures = pool.invokeAll(tasks);
            for (int h=0; h< futures.size(); h++){
                Map<String, Map<String, Double>> part = futures.get(h).get();
                // merge
                for (var e : part.entrySet()){
                    Map<String, Double> bucket = acc.computeIfAbsent(e.getKey(), k -> new HashMap<>());
                    e.getValue().forEach((k,v) -> bucket.merge(k, v, Double::sum));
                }
                // 로깅
                long sumRows = part.values().stream().mapToLong(m -> m.values().stream().count()).sum(); // 대략 카운트
                log.info("AGG parallel hour done: day={}, hour={}, groupsPart={}, totalGroupsSoFar={}",
                        day, h, part.size(), acc.size());
                scanned.add(sumRows);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            pool.shutdown();
        }

        // 평균 계산(+ upsert)
        List<Object[]> upserts = new ArrayList<>(acc.size());
        acc.forEach((key,sum)->{
            Map<String, Double> avg = new HashMap<>();
            sum.forEach((k,v)-> avg.put(k, v/24.0));
            String[] p = key.split("\\|",3);
            upserts.add(new Object[]{ p[0], p[1], p[2], day, toJson(avg) });
        });

        String sql = """
      INSERT INTO value_1d(element,measurement,source_id,collect_dt,value_json)
      VALUES (?,?,?,?,?)
      ON DUPLICATE KEY UPDATE value_json=VALUES(value_json), aggregated_at=CURRENT_TIMESTAMP
    """;
        int total=0, batch=1000;
        for (int i=0;i<upserts.size();i+=batch){
            total += Arrays.stream(jdbc.batchUpdate(sql, upserts.subList(i, Math.min(i+batch, upserts.size())))).sum();
            if ((i/batch)%10==0) log.info("AGG upsert progress: {}/{}", i, upserts.size());
        }

        long ms = (System.nanoTime()-t0)/1_000_000;
        log.info("AGG finished(day={}, groups={}, rowsUpserted={}, elapsedMs={})", day, acc.size(), total, ms);
        return total;
    }

    private Map<String, Map<String, Double>> scanHour(LocalDate day, int hour){
        Map<String, Map<String, Double>> local = new HashMap<>(120_000/24+1);
        jdbc.query("""
      SELECT element, measurement, source_id, value_json
      FROM value_m60 WHERE collect_dt=? AND hour_of_day=?
    """, rs -> {
            String key = rs.getString(1)+'|'+rs.getString(2)+'|'+rs.getString(3);
            Map<String, Double> json = parse(rs.getString(4));
            Map<String, Double> bucket = local.computeIfAbsent(key, k->new HashMap<>());
            json.forEach((k,v)-> bucket.merge(k, v==null?0.0:v, Double::sum));
        }, day, hour);
        return local;
    }

    private Map<String, Double> parse(String json){
        try { return om.readValue(json, new TypeReference<Map<String, Double>>(){}); }
        catch(Exception e){ return Collections.emptyMap(); }
    }
    private String toJson(Map<String, Double> m){
        try { return om.writeValueAsString(m); } catch(Exception e){ return "{}"; }
    }
}
