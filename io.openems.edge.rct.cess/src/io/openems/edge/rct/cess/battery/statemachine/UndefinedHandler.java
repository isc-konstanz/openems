package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) {
		var battery = context.getParent();
		return switch (battery.getStartStopTarget()) {
		case UNDEFINED // Stuck in UNDEFINED State
			-> State.UNDEFINED;

		case START // Force START
			-> battery.hasFaults()
					// Has Faults -> Error handling
					? State.ERROR
					// No Faults -> Start
					: State.STARTING;

		case STOP // force STOP
			-> State.STOPPING;
		};
	}

}
