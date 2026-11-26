package com.vidacruz.clinical.infrastructure.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.EvidenceFragment;
import com.vidacruz.clinical.infrastructure.db.VectorRepository;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * CRUD and search through the adapter, with the repository mocked: no database. The SQL itself is covered against a
 * real pgvector by {@code VectorRepositoryTest} when Docker is available.
 */
class PgvectorVectorStoreTest {

	private final VectorRepository repository = mock(VectorRepository.class);

	private Scheduler scheduler;

	private PgvectorVectorStoreAdapter adapter;

	private final EvidenceFragment fragment = EvidenceFragment.create(UUID.randomUUID(), 0, "PROTO-001",
			"Medidas iniciales", "texto", new float[] { 0.1f, 0.2f });

	@BeforeEach
	void setUp() {
		scheduler = Schedulers.newBoundedElastic(2, 10, "pgvector-jdbc-test");
		adapter = new PgvectorVectorStoreAdapter(repository, scheduler);
	}

	@AfterEach
	void tearDown() {
		scheduler.dispose();
	}

	@Test
	void saveDelegatesToUpsertAndEmitsTheFragment() {
		when(repository.upsert(fragment)).thenReturn(fragment);

		StepVerifier.create(adapter.save(fragment)).expectNext(fragment).verifyComplete();

		verify(repository).upsert(fragment);
	}

	@Test
	void findByIdEmitsTheFragmentWhenPresent() {
		when(repository.findById(fragment.id())).thenReturn(Optional.of(fragment));

		StepVerifier.create(adapter.findById(fragment.id())).expectNext(fragment).verifyComplete();
	}

	@Test
	void findByIdCompletesEmptyWhenAbsent() {
		when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

		StepVerifier.create(adapter.findById(UUID.randomUUID())).verifyComplete();
	}

	@Test
	void deleteByIdReportsWhetherARowWasDeleted() {
		UUID missing = UUID.randomUUID();
		when(repository.deleteById(fragment.id())).thenReturn(true);
		when(repository.deleteById(missing)).thenReturn(false);

		StepVerifier.create(adapter.deleteById(fragment.id())).expectNext(true).verifyComplete();
		StepVerifier.create(adapter.deleteById(missing)).expectNext(false).verifyComplete();
	}

	@Test
	void similaritySearchReturnsTheRepositoryOrder() {
		float[] query = { 0.3f, 0.4f };
		EvidenceFragment second = EvidenceFragment.create(UUID.randomUUID(), 1, "PROTO-001", "Seguimiento", "otro",
				new float[] { 0.3f, 0.4f });
		when(repository.searchSimilar(query, 2)).thenReturn(List.of(fragment, second));

		StepVerifier.create(adapter.similaritySearch(query, 2)).expectNext(List.of(fragment, second))
				.verifyComplete();
	}

	@Test
	void repositoryErrorsSurfaceAsErrorSignals() {
		when(repository.searchSimilar(any(), org.mockito.ArgumentMatchers.anyInt()))
				.thenThrow(new IllegalStateException("db down"));

		StepVerifier.create(adapter.similaritySearch(new float[] { 1f }, 1)).expectError(IllegalStateException.class)
				.verify();
	}

	@Test
	void blockingCallRunsOnTheBoundedSchedulerNotOnTheCallerThread() {
		AtomicReference<String> repositoryThread = new AtomicReference<>();
		when(repository.findById(any(UUID.class))).thenAnswer(invocation -> {
			repositoryThread.set(Thread.currentThread().getName());
			return Optional.of(fragment);
		});

		StepVerifier.create(adapter.findById(fragment.id())).expectNext(fragment).verifyComplete();

		assertThat(repositoryThread.get()).startsWith("pgvector-jdbc-test").isNotEqualTo(Thread.currentThread().getName());
	}

	@Test
	void saturatedSchedulerFailsFastInsteadOfQueueingWithoutLimit() throws InterruptedException {
		// One thread and one queue slot: the first call runs, the second queues, the third must be rejected.
		Scheduler tiny = Schedulers.newBoundedElastic(1, 1, "pgvector-jdbc-tiny");
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(repository.findById(any(UUID.class))).thenAnswer(invocation -> {
			started.countDown();
			release.await(5, TimeUnit.SECONDS);
			return Optional.of(fragment);
		});
		PgvectorVectorStoreAdapter saturable = new PgvectorVectorStoreAdapter(repository, tiny);
		try {
			saturable.findById(fragment.id()).subscribe();
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
			saturable.findById(fragment.id()).subscribe();

			StepVerifier.create(saturable.findById(fragment.id())).expectError(RejectedExecutionException.class)
					.verify();
		}
		finally {
			release.countDown();
			tiny.dispose();
		}
	}

}
