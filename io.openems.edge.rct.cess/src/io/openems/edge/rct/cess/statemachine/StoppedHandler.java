package io.openems.edge.rct.cess.statemachine;

import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;

public class StoppedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		final var ess = context.getParent();

		if (context.hasEssFaults()) {
			return State.ERROR;
		}

		if (!context.isEssStopped()) {
			return State.ERROR;
		}

		ess._setStartStop(StartStop.STOP);
		return State.STOPPED;
	}

}
