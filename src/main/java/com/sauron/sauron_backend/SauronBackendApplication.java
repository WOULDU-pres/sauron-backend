package com.sauron.sauron_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.sauron")
@EnableJpaRepositories(basePackages = "com.sauron")
@EntityScan(basePackages = "com.sauron")
public class SauronBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SauronBackendApplication.class, args);
	}

}
