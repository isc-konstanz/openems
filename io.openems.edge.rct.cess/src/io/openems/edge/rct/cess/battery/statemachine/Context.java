package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.rct.cess.battery.BatteryRctCess;
import io.openems.edge.rct.cess.battery.Config;

public class Context extends AbstractContext<BatteryRctCess> {

	protected final Config config;

	public Context(BatteryRctCess parent, Config config) {
		super(parent);
		this.config = config;
	}
}
