package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (inverter.hasFaults()) {
			return State.ERROR;
		}

		switch (inverter.getInverterState().asEnum()) {
		case InverterState.FAULT:
			return State.ERROR;

		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			// Mark as started
			inverter._setStartStop(StartStop.START);

			return State.RUNNING;

		default:
			break;
		}
		return State.UNDEFINED;
	}
}
