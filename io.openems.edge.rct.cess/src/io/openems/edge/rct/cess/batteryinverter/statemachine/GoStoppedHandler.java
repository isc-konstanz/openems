package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class GoStoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		switch (inverter.getRunState().asEnum()) {
		case RunState.CHARGING:
		case RunState.CHARGING_DERATED:
		case RunState.DISCHARGING:
		case RunState.DISCHARGING_DERATED:
			return State.GO_STOPPED;

		case RunState.STOPPED:
			return State.STOPPED;

		default:
			return State.UNDEFINED;
		}
	}
}
