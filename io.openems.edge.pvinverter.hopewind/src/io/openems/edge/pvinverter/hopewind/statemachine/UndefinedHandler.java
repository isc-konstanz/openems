package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (!inverter.getInverterState().isDefined()){
			return State.UNDEFINED;
		}
		
		switch (inverter.getInverterState().asEnum()) {
		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
			return State.GO_RUNNING;

		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			return State.RUNNING;

		case InverterState.SHUTDOWN:
			return State.STOPPED;

		case InverterState.FAULT:
			return State.ERROR;

		default:
			return State.ERROR;
		}
	}

}
