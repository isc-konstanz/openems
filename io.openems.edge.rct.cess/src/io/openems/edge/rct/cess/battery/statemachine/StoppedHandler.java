package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var battery = context.getParent();

		if (battery.getPreChargeState().asEnum() != PreChargeState.DISCONNECTED) {
			return State.UNDEFINED;
		}

		// Mark as stopped
		battery._setStartStop(StartStop.STOP);

		return State.STOPPED;
	}
}
