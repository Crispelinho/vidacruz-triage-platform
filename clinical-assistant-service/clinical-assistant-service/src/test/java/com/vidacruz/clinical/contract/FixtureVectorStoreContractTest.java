package com.vidacruz.clinical.contract;

import com.vidacruz.clinical.domain.ports.VectorStore;
import com.vidacruz.clinical.infrastructure.adapters.FixtureVectorStoreAdapter;

class FixtureVectorStoreContractTest extends VectorStoreContract {

	private final VectorStore store = new FixtureVectorStoreAdapter();

	@Override
	protected VectorStore store() {
		return store;
	}

}
