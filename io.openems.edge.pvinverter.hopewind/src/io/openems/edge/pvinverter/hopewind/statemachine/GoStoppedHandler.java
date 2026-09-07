package io.openems.edge.pvinverter.hopewind.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.InverterState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class GoStoppedHandler extends StateHandler<State, Context> {

	private static final int TIMEOUT_SECONDS = 300;

	private Instant entryAt = Instant.MIN;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();
		inverter._setMaxStopAttempts(false);
		this.entryAt = Instant.now(context.clock);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (inverter.getStartStopTarget() == StartStop.START) {
			// Target changed while stopping -> re-evaluate
			return State.UNDEFINED;
		}

		switch (inverter.getInverterState().asEnum()) {
		case InverterState.FAULT:
			return State.ERROR;

		case InverterState.SHUTDOWN:
			return State.STOPPED;

		case InverterState.STANDBY:
		case InverterState.SELF_TEST:
		case InverterState.STARTING:
		case InverterState.ON_GRID:
		case InverterState.RUNNING_ALARM:
		case InverterState.POWER_LIMITED:
		case InverterState.DISPATCH:
			if (Duration.between(this.entryAt, Instant.now(context.clock)).getSeconds() > TIMEOUT_SECONDS) {
				inverter._setMaxStopAttempts(true);
				return State.ERROR;
			}
			inverter.shutdown();

			return State.GO_STOPPED;

		default:
			break;
		}
		return State.UNDEFINED;
	}
}
