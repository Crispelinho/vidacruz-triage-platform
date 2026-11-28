package com.vidacruz.clinical.domain.services;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort redaction of direct identifiers from free clinical text before it leaves the cluster for an external
 * LLM provider (M-5, Law 1581). Clinical content (age, symptoms, doses, vital signs) is kept: the question must still
 * be answerable. Only identifiers are replaced with fixed placeholders.
 *
 * <p>Covered: e-mail addresses, labelled identity documents (CC, TI, CE, NIT, pasaporte, documento, cédula, NUIP,
 * historia clínica), phone numbers (Colombian mobiles, landlines, {@code +57}), any remaining bare run of 7-12 digits,
 * and capitalised names after common cues ("paciente Juan Pérez", "Sra. Ana", "se llama Luis", "nombre: ...").
 *
 * <p>This is a heuristic. A name with no cue ("Juan Pérez llegó con dolor") is not detected, so this filter reduces
 * exposure but is not a guarantee: the production answer is an in-cluster model or a paid tier with a DPA. Unlabelled
 * dotted numbers such as "2.400.000 UI" are also left intact to preserve doses, so an identifier written the same way
 * would pass through; labelled documents are still redacted.
 */
public final class PiiRedactor {

	public static final String EMAIL = "[EMAIL]";

	public static final String DOCUMENT = "[DOCUMENTO]";

	public static final String PHONE = "[TELEFONO]";

	public static final String NAME = "[NOMBRE]";

	/** Capital then lower case: "Pérez" is a name word, an acronym such as "HIV" or "EPOC" is clinical content. */
	private static final String NAME_WORD = "\\p{Lu}\\p{Ll}[\\p{L}'-]*";

	private static final String CONNECTOR = "(?:de|del|de la|de los|y)";

	/** One to four capitalised words, allowing Spanish connectors between them ("María de los Ángeles Ruiz"). */
	private static final String FULL_NAME = NAME_WORD + "(?:\\s+(?:" + CONNECTOR + "\\s+)?" + NAME_WORD + "){0,3}";

	private static final List<Rule> RULES = List.of(
			new Rule(Pattern.compile("[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[\\p{L}]{2,}"), EMAIL, 0),
			new Rule(Pattern.compile("(?iu)\\b((?:c\\.?\\s?c|t\\.?\\s?i|c\\.?\\s?e|nit|nuip|pasaporte|documento"
					+ "(?:\\s+de\\s+identidad)?|c[ée]dula(?:\\s+de\\s+ciudadan[íi]a)?|historia\\s+cl[íi]nica|h\\.?\\s?c)"
					+ "\\.?\\s*(?:n[°ºo.]*\\s*|n[uú]mero\\s*|:\\s*)?)[A-Z]{0,2}[0-9][0-9.\\- ]{3,18}[0-9]"), DOCUMENT, 1),
			new Rule(Pattern.compile("(?:\\+\\s?57[\\s.-]?)?\\b3[0-9]{2}[\\s.-]?[0-9]{3}[\\s.-]?[0-9]{4}\\b"), PHONE, 0),
			new Rule(Pattern.compile("(?:\\+\\s?57[\\s.-]?)?\\(?\\b60[1-8]\\)?[\\s.-]?[0-9]{3}[\\s.-]?[0-9]{4}\\b"), PHONE,
					0),
			// Unlabelled: only a bare run of digits. Dotted thousands are left alone: "2.400.000 UI" is a dose.
			new Rule(Pattern.compile("\\b[0-9]{7,12}\\b"), DOCUMENT, 0),
			new Rule(Pattern.compile("(?u)(\\b(?i:paciente|pte\\.?|se[ñn]ora?|sra?\\.|srta\\.?|don|do[ñn]a|"
					+ "llamad[oa]|se\\s+llama|nombre\\s*:?|mi\\s+(?:hij[oa]|madre|padre|espos[oa]))\\s+)" + FULL_NAME),
					NAME, 1));

	private PiiRedactor() {
	}

	/** @return the text with direct identifiers replaced; {@code null} stays {@code null} */
	public static String redact(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String redacted = text;
		for (Rule rule : RULES) {
			redacted = rule.apply(redacted);
		}
		return redacted;
	}

	/** {@code keepGroup} is the capture group (the cue) kept in front of the placeholder; 0 replaces the whole match. */
	private record Rule(Pattern pattern, String placeholder, int keepGroup) {

		String apply(String text) {
			Matcher matcher = pattern.matcher(text);
			StringBuilder out = new StringBuilder();
			while (matcher.find()) {
				String kept = keepGroup > 0 ? matcher.group(keepGroup) : "";
				matcher.appendReplacement(out, Matcher.quoteReplacement(kept + placeholder));
			}
			matcher.appendTail(out);
			return out.toString();
		}

	}

}
