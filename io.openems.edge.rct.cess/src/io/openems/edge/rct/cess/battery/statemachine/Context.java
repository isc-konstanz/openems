package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.rct.cess.battery.RctCessBattery;
import io.openems.edge.rct.cess.battery.Config;

public class Context extends AbstractContext<RctCessBattery> {

	protected final Config config;

	public Context(RctCessBattery parent, Config config) {
		super(parent);
		this.config = config;
	}
}
