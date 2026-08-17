package io.openems.edge.pvinverter.hopewind.statemachine;

import java.time.Clock;

import io.openems.edge.common.statemachine.AbstractContext;
import io.openems.edge.pvinverter.hopewind.Config;
import io.openems.edge.pvinverter.hopewind.PvInverterHopewindImpl;

public class Context extends AbstractContext<PvInverterHopewindImpl> {

	protected final Config config;
	protected final Clock clock;

	public Context(PvInverterHopewindImpl parent, Config config, Clock clock) {
		super(parent);
		this.config = config;
		this.clock = clock;
	}

}
