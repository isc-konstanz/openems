package io.openems.edge.pvinverter.hopewind;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public interface PvInverterHopewind extends ManagedSymmetricPvInverter, ElectricityMeter,
	ModbusSlave, ModbusComponent, OpenemsComponent, EventHandler, StartStoppable {

	public static final int HEART_BEAT_DEFAULT = 1;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

//		ACTIVE_POWER_LIMIT_TYPE(Doc.of(PowerLimitType.values())
//				.accessMode(AccessMode.READ_WRITE)),
		ACTIVE_POWER_LIMIT_PERC(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.PERCENT)
				.accessMode(AccessMode.READ_WRITE)
				.persistencePriority(PersistencePriority.MEDIUM)),
//		ACTIVE_POWER_LIMIT_FAILED(Doc.of(Level.FAULT)
//				.text("Power-Limit failed"),
	
		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),
		MAX_START_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of start attempts failed")),
		MAX_STOP_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of stop attempts failed")),

		COMMUNICATION_STATE(Doc.of(CommunicationState.values())),

		HEART_BEAT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.WRITE_ONLY)
				.unit(Unit.NONE));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_START_ATTEMPTS}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getMaxStartAttemptsChannel() {
		return this.channel(ChannelId.MAX_START_ATTEMPTS);
	}

	/**
	 * Gets the {@link StateChannel} for {@link ChannelId#MAX_START_ATTEMPTS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getMaxStartAttempts() {
		return this.getMaxStartAttemptsChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_START_ATTEMPTS} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxStartAttempts(Boolean value) {
		this.getMaxStartAttemptsChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_STOP_ATTEMPTS}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getMaxStopAttemptsChannel() {
		return this.channel(ChannelId.MAX_STOP_ATTEMPTS);
	}

	/**
	 * Gets the {@link StateChannel} for {@link ChannelId#MAX_STOP_ATTEMPTS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getMaxStopAttempts() {
		return this.getMaxStopAttemptsChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_STOP_ATTEMPTS}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxStopAttempts(Boolean value) {
		this.getMaxStopAttemptsChannel().setNextValue(value);
	}

	/**
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 *
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();

	/**
	 * Gets the Channel for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel
	 */
	public default Channel<State> getStateMachineChannel() {
		return this.channel(ChannelId.STATE_MACHINE);
	}

	/**
	 * Gets the StateMachine channel value for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<State> getStateMachine() {
		return this.getStateMachineChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#STATE_MACHINE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setStateMachine(State value) {
		this.getStateMachineChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RUN_FAILED}.
	 *
	 * @return the Channel
	 */
	public default Channel<Boolean> getRunFailedChannel() {
		return this.channel(ChannelId.RUN_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RUN_FAILED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRunFailed(boolean value) {
		this.getRunFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#COMMUNICATION_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<CommunicationState> getCommunicationStateChannel() {
		return this.channel(ChannelId.COMMUNICATION_STATE);
	}

	/**
	 * Gets the {@link CommunicationState}, see {@link ChannelId#COMMUNICATION_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<CommunicationState> getCommunicationState() {
		return this.getCommunicationStateChannel().value();
	}

	/**
	 * Sets the HeartBeat value. See {@link ChannelId#HEART_BEAT}.
	 *
	 * @param value the Integer value
	 * @throws OpenemsNamedException on error
	 */
	public default void setHeartBeat(Integer value) throws OpenemsNamedException {
		IntegerWriteChannel heartBeatChannel = this.channel(ChannelId.HEART_BEAT);
		heartBeatChannel.setNextWriteValue(value);
	}

	/**
	 * Sets the HeartBeat value. See {@link ChannelId#HEART_BEAT}.
	 *
	 * @throws OpenemsNamedException on error
	 */
	public default void setHeartBeat() throws OpenemsNamedException {
		this.setHeartBeat(HEART_BEAT_DEFAULT);
	}

}
