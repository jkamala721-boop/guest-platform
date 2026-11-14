package com.guest_platform;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Upgrade an existing V31 database, including legacy payment/link/receipt records. */
class PropertyOfferMigrationTest {
    @Test void v32AndV33PreserveExistingDataAndProtectOffers() throws Exception {
        var postgres = PropertyOfferPostgresIntegrationTest.POSTGRES;
        String database = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = postgres.getPostgresDatabase().getConnection(); var statement = c.createStatement()) {
            statement.execute("create database " + database);
        }
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl("postgres", database), "postgres", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String[] locations = {"classpath:db/core-migration", "classpath:db/postgresql-migration"};
        Flyway.configure().dataSource(dataSource).locations(locations).target("31").load().migrate();
        UUID host = UUID.randomUUID(), property = UUID.randomUUID();
        jdbc.update("insert into hosts(id,email,password_hash,full_name,created_at,updated_at) values (?,?,?,'Legacy host',now(),now())",
                host, "migration@example.com", "test-hash");
        jdbc.update("""
                insert into properties(id,host_id,name,property_type,address,maps_url,max_guests,
                default_nightly_rate,currency,check_in_time,check_out_time,created_at,updated_at)
                values (?,?,'Legacy property','APARTMENT','Nairobi','https://example.com/maps',2,125,'KES','14:00','10:00',now(),now())
                """, property, host);
        for (String status : List.of("DRAFT", "PENDING_CONFIRMATION", "PENDING_PAYMENT", "CONFIRMED", "CHECKED_IN", "COMPLETED", "CANCELLED")) {
            UUID booking = UUID.randomUUID(), payment = UUID.randomUUID();
            jdbc.update("""
                    insert into bookings(id,host_id,property_id,check_in_date,check_out_date,total_amount,currency,status,created_at,updated_at)
                    values (?,?,?,current_date+10,current_date+12,250,'KES',?,now(),now())
                    """, booking, host, property, status);
            jdbc.update("insert into guest_links(id,booking_id,token_hash,state,expires_at,created_at,updated_at) values (?,?,?,'REGISTRATION_OR_PAYMENT',now()+interval '12 days',now(),now())",
                    UUID.randomUUID(), booking, UUID.randomUUID().toString());
            jdbc.update("""
                    insert into payments(id,host_id,booking_id,provider,provider_reference,amount,booking_amount,service_fee,currency,status,paid_at,created_at,updated_at)
                    values (?,?,?,'MPESA',?,250,250,0,'KES','SUCCEEDED',now(),now(),now())
                    """, payment, host, booking, UUID.randomUUID().toString());
            jdbc.update("insert into receipts(id,host_id,booking_id,payment_id,receipt_number,amount,currency,issued_at,created_at) values (?,?,?,?,?,250,'KES',now(),now())",
                    UUID.randomUUID(), host, booking, payment, UUID.randomUUID().toString());
        }
        Map<String, List<String>> before = snapshot(jdbc);
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations(locations).load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();
        assertEquals(before, snapshot(jdbc));
        assertEquals(7, jdbc.queryForObject("select count(*) from bookings where offer_id is null and checkout_hold_expires_at is null and reservation_confirmed_at is null and payment_deadline_at is null", Integer.class));
        assertEquals(7, jdbc.queryForObject("select count(*) from payments where reservation_resolution_required_at is null", Integer.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select relrowsecurity from pg_class where oid='public.property_offers'::regclass", Boolean.class));
        for (String role : List.of("anon", "authenticated")) {
            assertEquals(Boolean.FALSE, jdbc.queryForObject("select has_table_privilege(?, 'public.property_offers', 'SELECT,INSERT,UPDATE,DELETE')", Boolean.class, role));
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    private Map<String, List<String>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : List.of("hosts", "properties", "bookings", "guest_links", "payments", "receipts")) {
            result.put(table, jdbc.queryForList("select (to_jsonb(t) - 'offer_id' - 'checkout_hold_expires_at' - 'reservation_confirmed_at' - 'payment_deadline_at' - 'reservation_resolution_required_at')::text from " + table + " t order by id", String.class));
        }
        return result;
    }
}
