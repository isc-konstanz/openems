package io.openems.edge.pvinverter.hopewind.statemachine;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.statemachine.StateHandler;
import io.openems.edge.pvinverter.hopewind.CommunicationState;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class GoRunningHandler extends StateHandler<State, Context> {

	@Override
	protected void onEntry(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();
		inverter._setMaxStartAttempts(false);
	}

	@Override
	public State runAndGetNextState(Context context) throws OpenemsNamedException {
		var inverter = context.getParent();

		switch (inverter.getCommunicationState().asEnum()) {
		case CommunicationState.ERROR:
			// Communication currently failed and needs to be restarted
//			IntegerWriteChannel communicationResetChannel = inverter.channel(PvInverterHopewind.ChannelId.COMMUNICATION_RESET);
//			communicationResetChannel.setNextWriteValue(0);
			break;
		case CommunicationState.RUNNING:
			return State.RUNNING;
		default:
			break;
		}
		return State.GO_RUNNING;
	}
}
