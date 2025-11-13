package io.openems.edge.rct.cess.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;

public class StartBatteryInverterHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		final var inverter = context.batteryInverter;

		if (context.hasEssFaults()) {
			return State.ERROR;
		}

		if (inverter.isStarted()) {
			return State.STARTED;
		}

		inverter.start();
		return State.START_BATTERY_INVERTER;
	}
}
