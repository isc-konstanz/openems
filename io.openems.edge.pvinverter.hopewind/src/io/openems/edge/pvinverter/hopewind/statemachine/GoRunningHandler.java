package io.openems.edge.pvinverter.hopewind.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class GoRunningHandler extends StateHandler<State, Context> {

	private Instant entryAt = Instant.MIN;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();
		inverter._setMaxStartAttempts(false);
		this.entryAt = Instant.now();
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (Duration.between(this.entryAt, Instant.now()).getSeconds() > 300) {
			// Try again
			return State.ERROR;
		}

		switch (inverter.getInverterState().asEnum()) {
		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
			// TODO: implement a timer here
			break;

		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			// TODO: reset timer here
			return State.RUNNING;

		case InverterState.SHUTDOWN:
			return State.STOPPED;

		case InverterState.FAULT:
			return State.ERROR;

		default:
			return State.ERROR;
		}

		return State.GO_RUNNING;
	}
}
