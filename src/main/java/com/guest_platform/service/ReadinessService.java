package com.guest_platform.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Performs a minimal database check without returning database details. */
@Service
public class ReadinessService {

    private final JdbcTemplate jdbcTemplate;

    public ReadinessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isReady() {
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            return Integer.valueOf(1).equals(value);
        } catch (org.springframework.dao.DataAccessException exception) {
            return false;
        }
    }
}
