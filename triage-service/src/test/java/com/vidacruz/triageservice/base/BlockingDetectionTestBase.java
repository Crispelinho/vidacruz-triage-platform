package com.vidacruz.triageservice.base;
import org.junit.jupiter.api.BeforeAll;
import reactor.tools.agent.ReactorDebugAgent;
public abstract class BlockingDetectionTestBase {
  @BeforeAll
  public static void setupBlockHound() {
    ReactorDebugAgent.init();
  }
}
