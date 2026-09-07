package io.openems.edge.pvinverter.hopewind;

import io.openems.common.types.OptionsEnum;

/**
 * Active power regulation mode of the inverter, register 40011. There is no
 * vendor documentation for this register; the evidence per value is:
 *
 * <ul>
 * <li>1 = ACTUAL [kW] via 40012 - CONFIRMED. Every bus sniff (HSNV60K-G01)
 * shows the vendor master running mode 1 with kW limits in 40012 (40013 parked
 * at 110 %), and the inverter reported POWER_LIMITED only with a kW limit set.
 * <li>0 = NOT the kW mode - CONFIRMED by the field incident 2026-09: an
 * intermediate driver mapped ACTUAL to 0 and enforced it; the inverter accepted
 * 0 and exported uncurtailed while 40012 was written every cycle. Whether 0
 * means "disabled" or "percent via 40013" is not distinguished yet; both ignore
 * 40012. Named DISABLED in line with the reactive mode register 40002.
 * <li>2 = PROPORTIONAL [%] via 40013 - UNVERIFIED (never observed on the bus).
 * </ul>
 *
 * <p>
 * Do not change this mapping without a bench test that proves the limit is
 * honored in the new mode. The mode-mapping test is in the readme.
 */
public enum ActivePowerLimitMode implements OptionsEnum {
	UNDEFINED(-1, "Undefined"),
	DISABLED(0, "Disabled (no active power regulation)"),
	ACTUAL(1, "Actual Power [kW]"),
	PROPORTIONAL(2, "Proportional Power [%]");

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
