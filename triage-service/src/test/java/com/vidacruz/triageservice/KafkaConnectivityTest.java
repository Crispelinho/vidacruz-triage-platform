package com.vidacruz.triageservice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
class KafkaConnectivityTest {
  @Container
  static KafkaContainer kafka = new KafkaContainer();
  
  @Test
  void testKafkaStarts() {
    assertNotNull(kafka);
  }
}
