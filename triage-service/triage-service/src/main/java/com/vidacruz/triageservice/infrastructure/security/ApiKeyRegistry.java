package com.vidacruz.triageservice.infrastructure.security;

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
 * <p>Format of the multi-key property: entries separated by {@code ;}, each entry {@code id|scope,scope|key}. The key
 * is the last field, so it may contain {@code |} but not {@code ;}. Example:
 * {@code dashboard|triage:read|<key-1>;ops|triage:read,triage:write|<key-2>}.
 *
 * <p>Backward compatibility: when the multi-key property is blank, the single legacy key is accepted with id
 * {@value #LEGACY_KEY_ID} and every scope. Only key ids are ever logged; key values are kept as SHA-256 digests.
 */
public final class ApiKeyRegistry {

	public static final String LEGACY_KEY_ID = "default";

	private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	private final List<Entry> entries;

	private ApiKeyRegistry(List<Entry> entries) {
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("at least one API key must be configured");
		}
		this.entries = List.copyOf(entries);
	}

	/** One key with every known scope: the pre-M-2 behaviour. */
	public static ApiKeyRegistry single(String key, Set<String> allScopes) {
		return new ApiKeyRegistry(List.of(new Entry(LEGACY_KEY_ID, digest(requireKey(key, LEGACY_KEY_ID)),
				Set.copyOf(allScopes))));
	}

	/**
	 * @param multiKeys   the {@code id|scopes|key;...} property; blank falls back to {@code legacyKey}
	 * @param legacyKey   the single key used when {@code multiKeys} is blank
	 * @param knownScopes scopes this service understands; anything else fails startup
	 */
	public static ApiKeyRegistry parse(String multiKeys, String legacyKey, Set<String> knownScopes) {
		if (multiKeys == null || multiKeys.isBlank()) {
			return single(legacyKey, knownScopes);
		}
		List<Entry> entries = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<String> digests = new HashSet<>();
		for (String raw : multiKeys.split(";")) {
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
			if (!ids.add(id)) {
				throw new IllegalArgumentException("duplicate API key id: " + id);
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
			byte[] digest = digest(requireKey(fields[2].strip(), id));
			if (!digests.add(Arrays.toString(digest))) {
				throw new IllegalArgumentException("API key id " + id + " reuses the value of another key");
			}
			entries.add(new Entry(id, digest, Set.copyOf(scopes)));
		}
		return new ApiKeyRegistry(entries);
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
