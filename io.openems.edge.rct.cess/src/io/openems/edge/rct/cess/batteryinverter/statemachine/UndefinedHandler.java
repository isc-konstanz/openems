package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var battery = context.getParent();
		return switch (battery.getStartStopTarget()) {
		case UNDEFINED // Stuck in UNDEFINED State
			-> State.UNDEFINED;

		case START // force START
			-> battery.hasFaults() //
					// Has Faults -> error handling
					? State.ERROR
					// No Faults -> start
					: State.GO_RUNNING;

		case STOP // force STOP
			-> State.GO_STOPPED;
		};
	}

}
