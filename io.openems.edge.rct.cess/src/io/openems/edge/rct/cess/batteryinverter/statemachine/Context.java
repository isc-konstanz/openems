package io.openems.edge.rct.cess.batteryinverter.statemachine;

import java.time.Clock;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.rct.cess.batteryinverter.Config;

public class Context extends AbstractContext<RctCessBatteryInverter> {

	protected final Config config;
	protected final Clock clock;

	protected final Battery battery;
	protected final int setActivePower;
	protected final int setReactivePower;

	public Context(RctCessBatteryInverter parent, Config config, Clock clock, Battery battery, int setActivePower, int setReactivePower) {
		super(parent);
		this.clock = clock;
		this.config = config;
		this.battery = battery;
		this.setActivePower = setActivePower;
		this.setReactivePower = setReactivePower;
	}
}
