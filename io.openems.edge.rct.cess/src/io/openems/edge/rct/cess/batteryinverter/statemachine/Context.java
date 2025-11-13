package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.rct.cess.batteryinverter.BatteryInverterRctCess;
import io.openems.edge.rct.cess.batteryinverter.Config;

public class Context extends AbstractContext<BatteryInverterRctCess> {

	protected final Config config;
	protected final Battery battery;
	protected final int setActivePower;
	protected final int setReactivePower;

	public Context(BatteryInverterRctCess parent, Config config, Battery battery, int setActivePower,
			int setReactivePower) {
		super(parent);
		this.config = config;
		this.battery = battery;
		this.setActivePower = setActivePower;
		this.setReactivePower = setReactivePower;
	}
}
