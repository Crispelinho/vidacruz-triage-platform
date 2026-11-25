package com.vidacruz.clinical_assistant_service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClinicalAssistantServiceApplicationTests {

	// The PgVector store requires an EmbeddingModel bean; no provider is configured in the scaffold
	@MockitoBean
	EmbeddingModel embeddingModel;

	@Test
	void contextLoads() {
	}

}
