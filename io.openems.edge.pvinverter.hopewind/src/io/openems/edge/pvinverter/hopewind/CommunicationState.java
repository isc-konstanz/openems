package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

public enum CommunicationState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"), //
	STANDBY(0, "Standby"), //
	RUNNING(1, "Running"), //
	ERROR(2, "Error");

	private final int value;
	private final String name;

	private CommunicationState(int value, String name) {
		this.value = value;
		this.name = name;
	}

	@Override
	public int getValue() {
		return this.value;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public OptionsEnum getUndefined() {
		return UNDEFINED;
	}
}
