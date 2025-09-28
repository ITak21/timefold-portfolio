package com.example.timefold_portfolio.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class LockService {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    private volatile boolean mysql = false;
    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    @PostConstruct
    void detectDb() {
        try (Connection c = dataSource.getConnection()) {
            String product = c.getMetaData().getDatabaseProductName().toLowerCase();
            mysql = product.contains("mysql");
            log.info("LockService init: dbProduct='{}', useMysqlLock={}", product, mysql);
        } catch (SQLException e) {
            log.warn("LockService: DB detection failed, fallback to local lock", e);
            mysql = false;
        }
    }

    public <T> T withLock(String key, Duration timeout, java.util.function.Supplier<T> body) {
        if (mysql) {
            Integer ok = jdbc.queryForObject("SELECT GET_LOCK(?,?)", Integer.class, key, (int) timeout.toSeconds());
            if (ok == null || ok == 0) throw new IllegalStateException("MySQL GET_LOCK timeout: " + key);
            try { return body.get(); }
            finally { jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, key); }
        } else {
            ReentrantLock lock = localLocks.computeIfAbsent(key, k -> new ReentrantLock());
            boolean acquired;
            try { acquired = lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IllegalStateException("Local lock interrupted"); }
            if (!acquired) throw new IllegalStateException("Local lock timeout: " + key);
            try { return body.get(); } finally { lock.unlock(); }
        }
    }
}
