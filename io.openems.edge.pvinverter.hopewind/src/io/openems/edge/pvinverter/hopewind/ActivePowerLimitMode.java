package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

public enum ActivePowerLimitMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	DISABLED(0, "Disabled"),
	ACTUAL(1, "Actual Power"),
	PROPORTIONAL(2, "Proportional Power");

	private final int value;
	private final String name;

	private ActivePowerLimitMode(int value, String name) {
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
