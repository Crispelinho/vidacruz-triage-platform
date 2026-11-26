package com.vidacruz.triageservice;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class KafkaAdvancedTest {
  @Container
  static KafkaContainer kafka = new KafkaContainer();
  
  @Test
  void kafkaBootstrapServersAvailable() {
    String bootstrapServers = kafka.getBootstrapServers();
    assertTrue(bootstrapServers.contains(":"));
  }
}
