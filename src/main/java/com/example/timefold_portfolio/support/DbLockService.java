package com.example.timefold_portfolio.support;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component @RequiredArgsConstructor
public class DbLockService {
    private final JdbcTemplate jdbc;

    public <T> T withLock(String key, Duration timeout, Supplier<T> body){
        Integer ok = jdbc.queryForObject("SELECT GET_LOCK(?,?)", Integer.class, key, (int) timeout.toSeconds());
        if (ok == null || ok == 0) throw new IllegalStateException("Lock timeout: " + key);
        try { return body.get(); }
        finally { jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, key); }
    }
}
