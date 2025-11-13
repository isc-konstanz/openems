package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class RunningHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		if (battery.hasFaults()) {
			return State.UNDEFINED;
		}
		if (battery.getPreChargeState().asEnum() != PreChargeState.CONNECTED) {
			return State.UNDEFINED;
		}

		// Mark as started
		battery._setStartStop(StartStop.START);

		return State.RUNNING;
	}

}
