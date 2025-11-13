package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (inverter.hasFaults()) {
			return State.UNDEFINED;
		}

		switch (inverter.getRunState().asEnum()) {
		case RunState.STOPPED:
		case RunState.STANDBY:
		case RunState.FAULT:

			return State.UNDEFINED;
			
		default:
			break;
		}

		// Mark as started
		inverter._setStartStop(StartStop.START);

		return State.RUNNING;
	}

}
