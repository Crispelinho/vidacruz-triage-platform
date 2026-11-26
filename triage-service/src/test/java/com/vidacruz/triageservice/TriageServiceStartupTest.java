package com.vidacruz.triageservice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
@SpringBootTest
class TriageServiceStartupTest {
  @Autowired
  private ApplicationContext ctx;
  
  @Test
  void applicationContextLoads() {
    assertNotNull(ctx);
  }
  
  @Test
  void webfluxBasePathConfigured() {
    String basePath = ctx.getEnvironment().getProperty("spring.webflux.base-path");
    assertNotNull(basePath);
  }
}
