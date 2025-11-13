package io.openems.edge.pvinverter.hopewind.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class ErrorHandler extends StateHandler<State, Context> {

	private Instant entryAt = Instant.MIN;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.entryAt = Instant.now();
	}

	@Override
	protected void onExit(Context context) throws OpenemsNamedException {
		var battery = context.getParent();
		battery._setMaxStartAttempts(false);
		battery._setMaxStopAttempts(false);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();


		switch (inverter.getInverterState().asEnum()) {
		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
			return State.GO_RUNNING;

		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			return State.RUNNING;

		case InverterState.SHUTDOWN:
			return State.STOPPED;

		case InverterState.FAULT:
			inverter.reset();
			return State.ERROR;

		default:
			return State.ERROR;
		}
	}

}
