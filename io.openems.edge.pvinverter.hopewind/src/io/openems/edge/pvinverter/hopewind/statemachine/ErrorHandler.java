package io.openems.edge.pvinverter.hopewind.statemachine;

import java.time.Duration;
import java.time.Instant;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class ErrorHandler extends StateHandler<State, Context> {

	private Instant entryAt = Instant.MIN;

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.entryAt = Instant.now();
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		if (Duration.between(this.entryAt, Instant.now()).getSeconds() <= 60) {
			return State.ERROR;
		}

		// TODO: Validate if error is communication related and may be reset
		// Try again
		inverter.reset();
		return State.UNDEFINED;
	}

}
