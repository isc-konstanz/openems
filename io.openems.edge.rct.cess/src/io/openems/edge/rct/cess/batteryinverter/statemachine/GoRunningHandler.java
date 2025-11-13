package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class GoRunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		switch (inverter.getRunState().asEnum()) {
		case RunState.STANDBY:
			return State.GO_RUNNING;

		// FIXME: RunState values > 6 do exist, but are not documented in the protocol documentation.
		case RunState.UNDEFINED:
		case RunState.CHARGING:
		case RunState.CHARGING_DERATED:
		case RunState.DISCHARGING:
		case RunState.DISCHARGING_DERATED:
			return State.RUNNING;
			
		default:
			return State.UNDEFINED;
		}
	}
}
