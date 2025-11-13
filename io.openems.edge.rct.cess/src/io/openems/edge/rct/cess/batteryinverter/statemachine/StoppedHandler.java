package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var inverter = context.getParent();

		switch (inverter.getRunState().asEnum()) {
		case RunState.CHARGING:
		case RunState.CHARGING_DERATED:
		case RunState.DISCHARGING:
		case RunState.DISCHARGING_DERATED:
			
			return State.UNDEFINED;

		default:
			break;
		}

		// Mark as stopped
		inverter._setStartStop(StartStop.STOP);

		return State.STOPPED;
	}
}
