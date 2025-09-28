package com.example.timefold_portfolio.controller;

import com.example.timefold_portfolio.service.BrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final BrowseService browse;

    @GetMapping("/facets/elements")
    public List<String> elements(@RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day){
        return browse.elements(day);
    }

    @GetMapping("/facets/measurements")
    public List<Map<String,Object>> measurements(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam String element){
        return browse.measurements(day, element);
    }

    @GetMapping("/facets/sources")
    public List<String> sources(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam String element,
            @RequestParam String measurement,
            @RequestParam(required=false) String q,
            @RequestParam(defaultValue="50") int limit,
            @RequestParam(defaultValue="0") int offset){
        return browse.sources(day, element, measurement, q, limit, offset);
    }

    @GetMapping("/d1/series")
    public Map<String,Object> series(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam String element,
            @RequestParam String measurement,
            @RequestParam String sourceId){
        return browse.series(day, element, measurement, sourceId);
    }

    @GetMapping("/d1/series-avg")
    public Map<String,Object> seriesAvg(
            @RequestParam @DateTimeFormat(iso= DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam String element,
            @RequestParam String measurement){
        return browse.seriesAvg(day, element, measurement);
    }
}
