package io.openems.edge.rct.cess.battery.statemachine;

import io.openems.common.types.OptionsEnum;
import io.openems.edge.common.statemachine.AbstractStateMachine;
import io.openems.edge.common.statemachine.StateHandler;

public class StateMachine extends AbstractStateMachine<StateMachine.State, Context> {

	public static final int WAIT_IN_UNDEFINED_STATE_SECONDS = 60;
	public static final int WAIT_IN_ERROR_STATE_SECONDS = 120;

	public enum State implements io.openems.edge.common.statemachine.State<State>, OptionsEnum {
		UNDEFINED(-1),

		STARTING(10),
		RUNNING(11),

		STOPPING(20),
		STANDBY(21),

		ERROR(30),
		;

		private final int value;

		private State(int value) {
			this.value = value;
		}

		@Override
		public int getValue() {
			return this.value;
		}

		@Override
		public String getName() {
			return this.name();
		}

		@Override
		public OptionsEnum getUndefined() {
			return UNDEFINED;
		}

		@Override
		public State[] getStates() {
			return State.values();
		}
	}

	public StateMachine(State initialState) {
		super(initialState);
	}

	@Override
	public StateHandler<State, Context> getStateHandler(State state) {
		return switch (state) {
		case UNDEFINED -> new UndefinedHandler();
		case STARTING -> new StartingHandler();
		case RUNNING -> new RunningHandler();
		case STOPPING -> new StoppingHandler();
		case STANDBY -> new StandbyHandler();
		case ERROR -> new ErrorHandler();
		};
	}
}