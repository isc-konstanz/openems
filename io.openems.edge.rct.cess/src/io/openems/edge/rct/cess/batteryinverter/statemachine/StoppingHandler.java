package io.openems.edge.rct.cess.batteryinverter.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.timedata.Timeout;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;

public class StoppingHandler extends StateHandler<State, Context> {

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
		case RunState.STOPPED:
			return State.STANDBY;

        case RunState.CHARGING:
        case RunState.CHARGING_DERATED:
        case RunState.DISCHARGING:
        case RunState.DISCHARGING_DERATED:
        	// TODO: Initiate stopping procedure
			// return State.STOPPING;
		default:
			if (this.errorTimeout.elapsed(context.clock)) {
				inverter._setTimeoutStartBatteryInverter(true);
				return State.ERROR;
			}
			if (this.undefinedTimeout.elapsed(context.clock)) {
				return State.UNDEFINED;
			}
			return State.STOPPING;
		}
	}
}
