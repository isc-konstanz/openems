package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

/**
 * Active power regulation mode of the inverter, register 40011.
 *
 * <p>
 * Mapping verified against the reverse-engineered register map (bus sniff,
 * HSNV60K-G01): 0 = regulation by actual power [kW] via register 40012, 1 =
 * regulation by proportional power [%] via register 40013.
 */
public enum ActivePowerLimitMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	ACTUAL(0, "Actual Power [kW]"),
	PROPORTIONAL(1, "Proportional Power [%]");

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
