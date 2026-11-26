package com.vidacruz.clinicalassistantservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// The RAG slice lives in com.vidacruz.clinical (domain / application / infrastructure), outside this class's package.
@SpringBootApplication(scanBasePackages = { "com.vidacruz.clinicalassistantservice", "com.vidacruz.clinical" })
public class ClinicalAssistantServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClinicalAssistantServiceApplication.class, args);
	}

}
