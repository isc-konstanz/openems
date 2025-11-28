package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class ErrorHandler extends StateHandler<State, Context> {

	private final Timeout undefinedTimeout = Timeout.ofSeconds(StateMachine.WAIT_IN_ERROR_STATE_SECONDS);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.undefinedTimeout.start(context.clock);
	}

	@Override
	protected void onExit(Context context) throws OpenemsNamedException {
		final var inverter = context.getParent();

		inverter.clearBatteryInverterTimeoutFailure();
	}

	@Override
	public State runAndGetNextState(Context context) {
		if (this.undefinedTimeout.elapsed(context.clock)) {
			// Try again
			return State.UNDEFINED;
		}
		return State.ERROR;
	}

}
