package com.vidacruz.clinical.blockhound;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * Defensive allowlist for Testcontainers setup calls. Container startup runs on JUnit threads, so these exceptions
 * are not normally triggered; they protect reactive paths that resolve container addresses at runtime (TST-RXN).
 */
public final class TestcontainersBlockHoundIntegration implements BlockHoundIntegration {

	@Override
	public void applyTo(BlockHound.Builder builder) {
		builder
				// Kafka reads its version from a JAR in a static initializer; this is classloading, not a reactor leak.
				.allowBlockingCallsInside("org.apache.kafka.common.utils.AppInfoParser", "<clinit>")
				// Linux NativePRNG reads its entropy source from reactor threads; the test JVM forces /dev/urandom.
				.allowBlockingCallsInside("java.security.SecureRandom", "nextBytes")
				.allowBlockingCallsInside("java.security.SecureRandom", "next")
				.allowBlockingCallsInside("sun.security.provider.NativePRNG", "engineNextBytes")
				.allowBlockingCallsInside("sun.security.provider.NativePRNG$RandomIO", "readFully")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "instance")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "lazyClient")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "client")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "checkAndPullImage")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "dockerHostIpAddress")
				.allowBlockingCallsInside("org.testcontainers.DockerClientFactory", "getTransportConfig")

				.allowBlockingCallsInside("org.testcontainers.containers.GenericContainer", "start")
				.allowBlockingCallsInside("org.testcontainers.containers.GenericContainer", "stop")
				.allowBlockingCallsInside("org.testcontainers.containers.GenericContainer", "waitUntilContainerStarted")

				.allowBlockingCallsInside("org.testcontainers.containers.ContainerState", "getHost")
				.allowBlockingCallsInside("org.testcontainers.containers.ContainerState", "getMappedPort")
				.allowBlockingCallsInside("org.testcontainers.containers.ContainerState", "getLogs")
				.allowBlockingCallsInside("org.testcontainers.containers.ContainerState", "isRunning")

				.allowBlockingCallsInside("org.testcontainers.containers.wait.strategy.AbstractWaitStrategy",
						"waitUntilReady")
				.allowBlockingCallsInside("org.testcontainers.containers.wait.strategy.WaitStrategy", "waitUntilReady");
	}

}
