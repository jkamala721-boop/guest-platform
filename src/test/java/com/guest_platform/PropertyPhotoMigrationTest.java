package com.guest_platform;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class PropertyPhotoMigrationTest {
    @Test void populatedV33UpgradeIsAdditiveAndProtected() throws Exception {
        var pg=PropertyOfferPostgresIntegrationTest.POSTGRES;
        String name="photos_"+UUID.randomUUID().toString().replace("-","");
        try(var c=pg.getPostgresDatabase().getConnection();var s=c.createStatement()) { s.execute("create database "+name); }
        var ds=new DriverManagerDataSource(pg.getJdbcUrl("postgres",name),"postgres","");
        String[] locations={"classpath:db/core-migration","classpath:db/postgresql-migration"};
        Flyway.configure().dataSource(ds).locations(locations).target("33").load().migrate();
        var db=new JdbcTemplate(ds);
        UUID host=UUID.randomUUID(),property=UUID.randomUUID(),offer=UUID.randomUUID();
        db.update("insert into hosts(id,email,password_hash,full_name,created_at,updated_at) values (?,?,'test','Host',now(),now())",host,"upgrade@example.com");
        db.update("insert into properties(id,host_id,name,property_type,address,maps_url,max_guests,default_nightly_rate,currency,check_in_time,check_out_time,created_at,updated_at) values (?,?,'Legacy','APARTMENT','Nairobi','https://example.com',2,100,'KES','14:00','10:00',now(),now())",property,host);
        db.update("insert into property_offers(id,property_id,token_hash,nightly_price,currency,created_at,expires_at) values (?,?,?,100,'KES',now(),now()+interval '7 days')",offer,property,"a".repeat(64));
        String before=db.queryForObject("select to_jsonb(p)::text from properties p where id=?",String.class,property);
        String offerBefore=db.queryForObject("select to_jsonb(o)::text from property_offers o where id=?",String.class,offer);
        var flyway=Flyway.configure().dataSource(ds).locations(locations).load();
        assertEquals(2,flyway.migrate().migrationsExecuted); flyway.validate();
        assertEquals(before,db.queryForObject("select to_jsonb(p)::text from properties p where id=?",String.class,property));
        assertEquals(offerBefore,db.queryForObject("select to_jsonb(o)::text from property_offers o where id=?",String.class,offer));
        assertEquals(0,db.queryForObject("select count(*) from property_photos",Integer.class));
        assertEquals(Boolean.TRUE,db.queryForObject("select relrowsecurity from pg_class where oid='public.property_photos'::regclass",Boolean.class));
        for(String role: new String[]{"anon","authenticated"}) assertEquals(Boolean.FALSE,db.queryForObject("select has_table_privilege(?,'public.property_photos','SELECT,INSERT,UPDATE,DELETE')",Boolean.class,role));
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
}
