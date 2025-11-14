package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

public enum InverterState implements OptionsEnum {
	UNDEFINED(0, "Undefined"),

	STANDBY(1, "Standby"),
	SELF_TEST(2, "Self Test"),
	STARTING(4, "Starting"),
	ON_GRID(8, "On Grid"),
	RUNNING_ALARM(16, "Running with Alarm"),
	POWER_LIMITED(32, "Power Limited"),
	DISPATCH(64, "Dispatch"),
	FAULT(128, "Fault"),
	SHUTDOWN(256, "Shutdown"),
	NIGHT_SLEEP(8192, "Night Sleep");

	private final int value;
	private final String name;

	private InverterState(int value, String name) {
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
