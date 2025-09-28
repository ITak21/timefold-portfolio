package com.example.timefold_portfolio.controller;

import com.example.timefold_portfolio.repository.Value1DRepo;
import com.example.timefold_portfolio.service.ParallelAggregationService;
import com.example.timefold_portfolio.service.SafeAggregationService;
import com.example.timefold_portfolio.service.SeedService;
import com.example.timefold_portfolio.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dev")
@RequiredArgsConstructor
public class DevController {
    private final SeedService seed;
    private final SafeAggregationService agg;
    private final ParallelAggregationService parallelAggregationService;
    private final ValidationService validationService;
    private final Value1DRepo value1DRepo;

    @PostMapping("/seed")
    public Map<String,Object> seed(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(defaultValue="100000") int groups,
            @RequestParam(defaultValue="3") int keysPerJson,
            @RequestParam(defaultValue="1000") int batchSize) {
        log.info("API seed called: day={}, groups={}, keysPerJson={}, batchSize={}",
                day, groups, keysPerJson, batchSize);
        seed.seed(day, groups, keysPerJson, batchSize);
        return Map.of("ok", true, "day", day, "groups", groups);
    }

    @PostMapping("/aggregate")
    public Map<String,Object> aggregate(@RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day) {
        int n = parallelAggregationService.aggregateDay(day);
        return Map.of("ok", true, "upserts", n);
    }

    @GetMapping("/verify")
    public Map<String,Object> verify(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(defaultValue = "10") int sampleLimit){
        return validationService.verify(day, sampleLimit);
    }


}
