package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class GoRunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		switch (battery.getPreChargeState().asEnum()) {
		case PreChargeState.CONNECTION_START:
		case PreChargeState.CONNECTING:
			return State.GO_RUNNING;

		case PreChargeState.CONNECTED:
			return State.RUNNING;

		default:
			return State.UNDEFINED;
		}
	}
}
