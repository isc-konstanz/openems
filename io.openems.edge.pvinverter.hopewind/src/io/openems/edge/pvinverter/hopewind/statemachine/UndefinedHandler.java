package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var inverter = context.getParent();
		return switch (inverter.getStartStopTarget()) {
		case UNDEFINED // Stuck in UNDEFINED State
			-> State.UNDEFINED;

		case START // force START
			-> inverter.hasFaults()
					// Has Faults -> Error handling
					? State.ERROR
					// No Faults -> Start
					: State.GO_RUNNING;

		case STOP // Force STOP
			-> State.GO_STOPPED;
		};
	}

}
