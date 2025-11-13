package io.openems.edge.rct.cess.battery.enums;

import io.openems.common.types.OptionsEnum;

public enum PreChargeState implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	DISCONNECTED(0, "Disconnected"),
	CONNECTION_START(1, "Connection start"),
	CONNECTING(2, "Connecting"),
	CONNECTED(3, "Connected"),
	CONNECTION_FAILED(4, "Connection failed");

	private final int value;
	private final String name;

	private PreChargeState(int value, String name) {
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
