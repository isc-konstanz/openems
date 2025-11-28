package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class StartingHandler extends StateHandler<State, Context> {

	private final Timeout undefinedTimeout = Timeout.ofSeconds(StateMachine.WAIT_IN_UNDEFINED_STATE_SECONDS);
	private final Timeout errorTimeout = Timeout.ofSeconds(RctCessBatteryInverter.TIMEOUT);

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		this.undefinedTimeout.start(context.clock);
		this.errorTimeout.start(context.clock);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		switch (inverter.getRunState().asEnum()) {
		// FIXME: RunState values > 6 do exist, but are not documented in the protocol documentation.
		case RunState.UNDEFINED:
		case RunState.CHARGING:
		case RunState.CHARGING_DERATED:
		case RunState.DISCHARGING:
		case RunState.DISCHARGING_DERATED:
			return State.RUNNING;

		case RunState.STANDBY:
        	// TODO: Initiate starting procedure
			// return State.STARTING;
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				inverter._setTimeoutStartBatteryInverter(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
			return State.STARTING;
		}
	}
}
