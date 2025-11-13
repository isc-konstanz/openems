package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

public enum ReactivePowerLimitState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	DISABLED(0, "Disabled reactive output"),
	POWER_FACTOR(1, "Power factor regulation"),
	KVAR(2, "Reactive power regulation"),
	PROPORTIONAL(3, "Reactive proportional regulation"),
	QP(4, "Q(P) regulation"),
	QU(5, "Q(U) regulation");

	private final int value;
	private final String name;

	private ReactivePowerLimitState(int value, String name) {
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

