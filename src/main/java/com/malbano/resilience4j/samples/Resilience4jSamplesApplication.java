package com.malbano.resilience4j.samples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class Resilience4jSamplesApplication {

	public static void main(String[] args) {
		SpringApplication.run(Resilience4jSamplesApplication.class, args);
	}

}
