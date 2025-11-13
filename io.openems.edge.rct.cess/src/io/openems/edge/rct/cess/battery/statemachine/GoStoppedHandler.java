package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class GoStoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		switch (battery.getPreChargeState().asEnum()) {
		case PreChargeState.CONNECTED:
			return State.GO_STOPPED;

		case PreChargeState.DISCONNECTED:
			return State.STOPPED;

		default:
			return State.UNDEFINED;
		}
	}
}
