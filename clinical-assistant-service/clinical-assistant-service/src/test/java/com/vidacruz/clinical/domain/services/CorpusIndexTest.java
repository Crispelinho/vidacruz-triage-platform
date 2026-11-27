package com.vidacruz.clinical.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vidacruz.clinical.domain.models.ProtocolSection;

class CorpusIndexTest {

	@Test
	void addAndRefreshTrackTheCurrentCorpus() {
		CorpusIndex index = new CorpusIndex();
		ProtocolSection a = ProtocolSection.of("PROTO-001", "Medidas iniciales");
		ProtocolSection b = ProtocolSection.of("PROTO-002", "Seguimiento");

		index.add(a);
		assertThat(index.contains(a)).isTrue();
		assertThat(index.contains(b)).isFalse();

		index.refresh(List.of(b));
		assertThat(index.contains(a)).isFalse();
		assertThat(index.contains(b)).isTrue();
		assertThat(index.size()).isEqualTo(1);
	}

}
