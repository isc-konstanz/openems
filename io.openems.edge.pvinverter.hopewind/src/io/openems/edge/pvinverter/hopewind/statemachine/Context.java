package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.pvinverter.hopewind.Config;
import io.openems.edge.pvinverter.hopewind.PvInverterHopewindImpl;

public class Context extends AbstractContext<PvInverterHopewindImpl> {

	protected final Config config;

	public Context(PvInverterHopewindImpl parent, Config config) {
		super(parent);
		this.config = config;
	}

}
