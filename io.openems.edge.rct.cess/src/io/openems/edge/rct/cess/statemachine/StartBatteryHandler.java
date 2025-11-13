package io.openems.edge.rct.cess.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;

public class StartBatteryHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		final var battery = context.battery;

		if (battery.isStarted()) {
			return State.START_BATTERY_INVERTER;
		}

		battery.start();
		return State.START_BATTERY;
	}
}
