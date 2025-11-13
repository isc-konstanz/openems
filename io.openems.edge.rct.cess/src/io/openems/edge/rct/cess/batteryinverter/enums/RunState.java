package io.openems.edge.rct.cess.batteryinverter.enums;

import io.openems.common.types.OptionsEnum;

public enum RunState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	STOPPED(0, "Stop"),
	STANDBY(1, "Standby"),
	FAULT(2, "Fault"),
	CHARGING(3, "Charging"),
	DISCHARGING(4, "Discharging"),
	CHARGING_DERATED(5, "Charging derated"),
	DISCHARGING_DERATED(6, "Discharging derated");

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
