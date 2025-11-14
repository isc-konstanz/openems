package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var inverter = context.getParent();

		switch (inverter.getInverterState().asEnum()) {
			case InverterState.FAULT:
				return State.ERROR;

			case InverterState.SHUTDOWN:
				// Mark as stopped
				inverter._setStartStop(StartStop.STOP);

				return State.STOPPED;
			default:
				break;
		}
		return State.UNDEFINED;
	}
}
