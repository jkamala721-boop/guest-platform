package com.guest_platform;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GuestPlatformApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
	}

	@Test
	void databaseConnectionIsAvailable() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT 1")) {
			resultSet.next();

			assertThat(resultSet.getInt(1)).isEqualTo(1);
		}
	}

	@Test
	void hostveroDoesNotCreateSpringBootDefaultUserDetailsService() {
		assertThat(applicationContext.getBeansOfType(UserDetailsService.class)).isEmpty();
		assertThat(applicationContext.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
	}

}
