package io.openems.edge.rct.cess.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;

public class StopBatteryHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		final var battery = context.battery;

		if (context.hasEssFaults()) {
			return State.ERROR;
		}

		if (battery.isStopped()) {
			return State.STOPPED;
		}

		battery.stop();
		return State.STOP_BATTERY;
	}
}
