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

		switch (inverter.getInverterState().asEnum()) {
		case InverterState.FAULT:
			return State.ERROR;

		case InverterState.SHUTDOWN:
			inverter.startup();

			return State.GO_RUNNING;

		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
			if (Duration.between(this.entryAt, Instant.now()).getSeconds() > 300) {
				// Try again to reset
				return State.ERROR;
			}
			return State.GO_RUNNING;

		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			return State.RUNNING;

		default:
			break;
		}
		return State.UNDEFINED;
	}
}
