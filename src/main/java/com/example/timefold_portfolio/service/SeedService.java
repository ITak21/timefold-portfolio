package com.example.timefold_portfolio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeedService {
    private final JdbcTemplate jdbc;
    private final Random rnd = new Random();

    public void seed(LocalDate day, int groups, int keysPerJson, int batchSize) {
        long t0 = System.nanoTime();
        log.info("SEED start: day={}, groupsPerHour={}, keysPerJson={}, batchSize={}",
                day, groups, keysPerJson, batchSize);

        // 그룹 키(=source_id) 사전 생성
        List<String[]> groupKeys = new ArrayList<>(groups);
        for (int i = 0; i < groups; i++) {
            int eIdx = i % 100;
            int mIdx = (i / 100) % 50;   // ← 100개 element 묶음 단위로 measurement 순환
            groupKeys.add(new String[]{ "E"+eIdx, "M"+mIdx, "SRC-"+i });
        }

        String sql = "INSERT INTO value_m60(element,measurement,source_id,collect_dt,hour_of_day,value_json) VALUES (?,?,?,?,?,?)";
        long totalInserted = 0;

        for (int hour = 0; hour < 24; hour++) {
            long hStart = System.nanoTime();
            List<Object[]> args = new ArrayList<>(batchSize);
            long hourInserted = 0;

            for (int i = 0; i < groups; i++) {
                String[] g = groupKeys.get(i);
                args.add(new Object[]{ g[0], g[1], g[2], day, hour, buildJson(keysPerJson) });
                if (args.size() == batchSize) {
                    jdbc.batchUpdate(sql, args);
                    hourInserted += args.size();
                    args.clear();
                }
            }
            if (!args.isEmpty()) {
                jdbc.batchUpdate(sql, args);
                hourInserted += args.size();
            }
            totalInserted += hourInserted;
            long ms = (System.nanoTime() - hStart) / 1_000_000;
            log.info("SEED hour done: day={}, hour={}, inserted={}, elapsedMs={}", day, hour, hourInserted, ms);
        }

        long totalMs = (System.nanoTime() - t0) / 1_000_000;
        log.info("SEED finished: day={}, totalInserted={}, elapsedMs={}", day, totalInserted, totalMs);
    }

    private String buildJson(int keys) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 1; i <= keys; i++) {
            if (i > 1) sb.append(',');
            sb.append("\"k").append(i).append("\":").append(rnd.nextInt(1000) / 10.0);
        }
        return sb.append('}').toString();
    }
}
