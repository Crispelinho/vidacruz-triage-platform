package com.vidacruz.clinicalassistantservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The accepted API keys, each with an id and a set of scopes (M-2, ADR-07).
 *
 * <p>Three sources, all additive:
 * <ul>
 * <li>the owner key (the existing single key), always accepted with id {@value #OWNER_KEY_ID} and every scope, so
 * current deployments keep working with no configuration change;</li>
 * <li>an optional reviewer key, id {@value #REVIEWER_KEY_ID}, with a fixed scope set that never includes
 * administrative scopes; revoke it by unsetting it;</li>
 * <li>optional extra scoped keys: entries separated by {@code ;}, each {@code id|scope,scope|key}. The key is the last
 * field, so it may contain {@code |} but not {@code ;}. Example: {@code frontend|assistant:query|<key-1>}.</li>
 * </ul>
 *
 * <p>Only key ids are ever logged; key values are kept as SHA-256 digests.
 */
public final class ApiKeyRegistry {

	public static final String OWNER_KEY_ID = "owner";

	public static final String REVIEWER_KEY_ID = "reviewer";

	private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	private final List<Entry> entries;

	private ApiKeyRegistry(List<Entry> entries) {
		this.entries = List.copyOf(entries);
	}

	/** Only the owner key, with every known scope: the pre-M-2 behaviour. */
	public static ApiKeyRegistry single(String ownerKey, Set<String> allScopes) {
		return create(ownerKey, null, Set.of(), null, allScopes);
	}

	/**
	 * @param ownerKey       the existing single key; required, always gets every known scope
	 * @param reviewerKey    optional; blank means no reviewer key
	 * @param reviewerScopes scopes of the reviewer key
	 * @param extraKeys      optional {@code id|scopes|key;...} entries
	 * @param knownScopes    scopes this service understands; anything else fails startup
	 */
	public static ApiKeyRegistry create(String ownerKey, String reviewerKey, Set<String> reviewerScopes,
			String extraKeys, Set<String> knownScopes) {
		List<Entry> entries = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<String> digests = new HashSet<>();
		add(entries, ids, digests, OWNER_KEY_ID, Set.copyOf(knownScopes), requireKey(ownerKey, OWNER_KEY_ID));
		if (reviewerKey != null && !reviewerKey.isBlank()) {
			if (!knownScopes.containsAll(reviewerScopes)) {
				throw new IllegalArgumentException("reviewer scopes must be known scopes");
			}
			add(entries, ids, digests, REVIEWER_KEY_ID, Set.copyOf(reviewerScopes), reviewerKey.strip());
		}
		if (extraKeys == null || extraKeys.isBlank()) {
			return new ApiKeyRegistry(entries);
		}
		for (String raw : extraKeys.split(";")) {
			if (raw.isBlank()) {
				continue;
			}
			String[] fields = raw.strip().split("\\|", 3);
			if (fields.length != 3) {
				throw new IllegalArgumentException("API key entry must be id|scopes|key (entry #" + (entries.size() + 1)
						+ ")");
			}
			String id = fields[0].strip();
			if (!KEY_ID.matcher(id).matches()) {
				throw new IllegalArgumentException("API key id must match " + KEY_ID.pattern() + ": " + id);
			}
			Set<String> scopes = new LinkedHashSet<>();
			for (String scope : fields[1].split(",")) {
				String trimmed = scope.strip();
				if (trimmed.isEmpty()) {
					continue;
				}
				if (!knownScopes.contains(trimmed)) {
					throw new IllegalArgumentException("unknown scope '" + trimmed + "' for API key id " + id);
				}
				scopes.add(trimmed);
			}
			if (scopes.isEmpty()) {
				throw new IllegalArgumentException("API key id " + id + " has no scope");
			}
			add(entries, ids, digests, id, Set.copyOf(scopes), requireKey(fields[2].strip(), id));
		}
		return new ApiKeyRegistry(entries);
	}

	private static void add(List<Entry> entries, Set<String> ids, Set<String> digests, String id, Set<String> scopes,
			String key) {
		if (!ids.add(id)) {
			throw new IllegalArgumentException("duplicate API key id: " + id);
		}
		byte[] digest = digest(key);
		if (!digests.add(Arrays.toString(digest))) {
			throw new IllegalArgumentException("API key id " + id + " reuses the value of another key");
		}
		entries.add(new Entry(id, digest, scopes));
	}

	/**
	 * Compares the provided key with every configured key in constant time per entry, so the response time does not
	 * reveal which entry, or which prefix, matched.
	 */
	public Optional<Principal> authenticate(String providedKey) {
		if (providedKey == null || providedKey.isBlank()) {
			return Optional.empty();
		}
		byte[] provided = digest(providedKey);
		Entry match = null;
		for (Entry entry : entries) {
			if (MessageDigest.isEqual(provided, entry.digest()) && match == null) {
				match = entry;
			}
		}
		return Optional.ofNullable(match).map(entry -> new Principal(entry.id(), entry.scopes()));
	}

	private static String requireKey(String key, String id) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("API key must not be blank (id " + id + ")");
		}
		return key;
	}

	private static byte[] digest(String key) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}

	/** The authenticated caller: an id that is safe to log, never the key value. */
	public record Principal(String keyId, Set<String> scopes) {
	}

	private record Entry(String id, byte[] digest, Set<String> scopes) {
	}

}
