package com.vidacruz.clinical.blockhound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * TST-RXN negative control. If this test passes without throwing, BlockHound is not instrumenting the test JVM and no
 * positive BlockHound result may be cited.
 */
class BlockHoundNegativeControlTest {

	@Test
	void blockingCallOnParallelThreadIsDetected() {
		assertThatThrownBy(() -> Mono.fromCallable(() -> {
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
			return "blocked";
		})
				.subscribeOn(Schedulers.parallel())
				.block())
				.hasRootCauseInstanceOf(BlockingOperationError.class);
	}

}
