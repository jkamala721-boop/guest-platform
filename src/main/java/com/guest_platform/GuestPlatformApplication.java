package com.guest_platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GuestPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(GuestPlatformApplication.class, args);
	}

}
