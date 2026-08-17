package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class UndefinedHandler extends StateHandler<State, Context> {

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (!inverter.getInverterState().isDefined()) {
			return State.UNDEFINED;
		}
		if (inverter.getInverterState().asEnum() == InverterState.FAULT) {
			return State.ERROR;
		}

		return switch (inverter.getStartStopTarget()) {
		case UNDEFINED
			// Stuck in UNDEFINED state, till first start/stop request
			-> State.UNDEFINED;

		case START -> switch (inverter.getInverterState().asEnum()) {
			case InverterState.SHUTDOWN,
					InverterState.STANDBY,
					InverterState.SELF_TEST,
					InverterState.STARTING
				-> State.GO_RUNNING;

			case InverterState.ON_GRID,
					InverterState.RUNNING_ALARM,
					InverterState.POWER_LIMITED,
					InverterState.DISPATCH
				-> State.RUNNING;

			default -> State.UNDEFINED;
			};

		case STOP -> switch (inverter.getInverterState().asEnum()) {
			case InverterState.SHUTDOWN
				-> State.STOPPED;

			default -> State.GO_STOPPED;
			};
		};
	}

}
