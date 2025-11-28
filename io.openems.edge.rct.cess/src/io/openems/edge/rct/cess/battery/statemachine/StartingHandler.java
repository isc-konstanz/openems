package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.battery.RctCessBattery;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public class StartingHandler extends StateHandler<State, Context> {

	private final Timeout undefinedTimeout = Timeout.ofSeconds(StateMachine.WAIT_IN_UNDEFINED_STATE_SECONDS);
	private final Timeout errorTimeout = Timeout.ofSeconds(RctCessBattery.TIMEOUT);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.undefinedTimeout.start(context.clock);
		this.errorTimeout.start(context.clock);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var battery = context.getParent();

		switch (battery.getPreChargeState().asEnum()) {
		case PreChargeState.CONNECTED:
			return State.RUNNING;

        case PreChargeState.CONNECTION_START:
        case PreChargeState.CONNECTING:
        	// TODO: Initiate starting procedure
			// return State.STARTING;
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				battery._setTimeoutStartBattery(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
			return State.STARTING;
		}
	}
}
