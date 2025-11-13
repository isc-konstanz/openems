package io.openems.edge.rct.cess.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;

public class StopBatteryInverterHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		final var inverter = context.batteryInverter;

		if (context.hasEssFaults()) {
			return State.ERROR;
		}

		if (inverter.isStopped()) {
			return State.STOP_BATTERY;
		}

		inverter.stop();
		return State.STOP_BATTERY_INVERTER;
	}
}
