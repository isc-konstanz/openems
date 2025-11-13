package io.openems.edge.rct.cess.statemachine;

import java.time.Clock;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.rct.cess.Config;
import io.openems.edge.rct.cess.RctCess;

public class Context extends AbstractContext<RctCess> {

	protected final Config config;
	protected final Battery battery;
	protected final ManagedSymmetricBatteryInverter batteryInverter;
	protected final Clock clock;

	public Context(RctCess parent, Config config, Battery battery, ManagedSymmetricBatteryInverter batteryInverter,
			Clock clock) {
		super(parent);
		this.config = config;
		this.battery = battery;
		this.batteryInverter = batteryInverter;
		this.clock = clock;
	}

	/**
	 * CESS has faults.
	 * 
	 * <p>
	 * Check for any faults in the CESS and its dependent battery or battery inverter.
	 * 
	 * @return true on any failure
	 */
	public boolean hasEssFaults() {
		return this.getParent().hasFaults() || this.battery.hasFaults() || this.batteryInverter.hasFaults();
	}

	/**
	 * Is CESS started.
	 * 
	 * <p>
	 * CESS is started when battery and battery-inverter started.
	 * 
	 * @return true if battery and battery-inverter started
	 */
	public boolean isEssStarted() {
		return this.battery.isStarted() && this.batteryInverter.isStarted();
	}

	/**
	 * Is CESS stopped.
	 * 
	 * <p>
	 * CESS is stopped when at least the battery stopped.
	 * 
	 * @return true if the system stopped.
	 */
	public boolean isEssStopped() {
		return this.battery.isStopped();
	}
}
