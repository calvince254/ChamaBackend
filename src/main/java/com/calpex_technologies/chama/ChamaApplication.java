package com.calpex_technologies.chama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChamaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChamaApplication.class, args);
	}

}
