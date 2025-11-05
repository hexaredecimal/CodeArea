package org.libcode.intelisense;

/**
 *
 * @author hexaredecimal
 */
public class IntellisenseItem {

	private final String name;
	private final String signature;
	private final String documentation;

	public IntellisenseItem(String name, String signature, String documentation) {
		this.name = name;
		this.signature = signature;
		this.documentation = documentation;
	}

	public String getName() {
		return name;
	}

	public String getSignature() {
		return signature;
	}

	public String getDocumentation() {
		return documentation;
	}
}
