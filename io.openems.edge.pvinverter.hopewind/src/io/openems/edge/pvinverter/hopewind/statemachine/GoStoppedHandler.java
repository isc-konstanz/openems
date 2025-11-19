package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class GoStoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		switch (inverter.getInverterState().asEnum()) {
		case InverterState.FAULT:
			return State.ERROR;

		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			inverter.shutdown();

			return State.GO_STOPPED;

		default:
			break;
		}
		return State.UNDEFINED;
	}
}
