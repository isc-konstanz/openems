package io.openems.edge.rct.cess.battery.enums;

import io.openems.common.types.OptionsEnum;

public enum RunState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	NORMAL(0, "Normal"),
	NO_CHARGE(1, "No Charge"),
	NO_DISCHARGE(2, "No Discharge"),
	STANDBY(3, "Standby"),
	STOPPED(4, "Stopped");

	private final int value;
	private final String name;

	private RunState(int value, String name) {
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
