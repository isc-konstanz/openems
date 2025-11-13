package io.openems.edge.rct.cess.battery.enums;

import io.openems.common.types.OptionsEnum;

public enum RackChargeState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	IDLE(0, "Idle"),
	DISCHARGING(2, "Discharging"),
	CHARGING(1, "Charging");

	private final int value;
	private final String name;

	private RackChargeState(int value, String name) {
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
