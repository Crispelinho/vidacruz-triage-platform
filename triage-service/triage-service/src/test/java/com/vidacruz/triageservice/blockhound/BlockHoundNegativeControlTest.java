package com.vidacruz.triageservice.blockhound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** TST-RXN negative control: BlockHound must flag a blocking call on a reactor thread. */
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
