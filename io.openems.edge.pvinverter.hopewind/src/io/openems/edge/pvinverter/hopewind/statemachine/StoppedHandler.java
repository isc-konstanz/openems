package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		// Mark as stopped
		var inverter = context.getParent();
		inverter._setStartStop(StartStop.STOP);

		return State.STOPPED;
	}
}
