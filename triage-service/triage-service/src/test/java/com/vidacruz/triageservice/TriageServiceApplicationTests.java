package com.vidacruz.triageservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TriageTestContainers.class)
@SpringBootTest
class TriageServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
