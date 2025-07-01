package com.sauron.sauron_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.sauron")
public class SauronBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SauronBackendApplication.class, args);
	}

}
