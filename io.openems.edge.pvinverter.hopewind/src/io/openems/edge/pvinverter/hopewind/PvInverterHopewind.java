package io.openems.edge.pvinverter.hopewind;

import java.util.function.Function;

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
import io.openems.edge.common.channel.EnumWriteChannel;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
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
import io.openems.edge.timedata.api.TimedataProvider;

public interface PvInverterHopewind extends ManagedSymmetricPvInverter,
		ElectricityMeter, OpenemsComponent, ModbusComponent, ModbusSlave,
		TimedataProvider, EventHandler, StartStoppable {

	public static final int ACTIVE_POWER_MAX = 66000;
	public static final int APPARENT_POWER_MAX = (int) Math.round(ACTIVE_POWER_MAX / 0.8);
	public static final int REACTIVE_POWER_MAX = (int) Math.round(APPARENT_POWER_MAX * Math.sin(Math.acos(0.8)));

	public static final int WATCH_DOG_TIMER_DEFAULT = 60;
	public static final int HEART_BEAT_DEFAULT = 0;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),
		MAX_START_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of start attempts failed")),
		MAX_STOP_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of stop attempts failed")),

		HEART_BEAT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.WRITE_ONLY)
				.unit(Unit.NONE)),

		RUNNING_STATE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Inverter Running State")),
		INVERTER_STATE(Doc.of(InverterState.values())
				.accessMode(AccessMode.READ_ONLY)
				.text("Inverter State")),

		STARTUP(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Start the Inverter")),
		SHUTDOWN(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Stop the Inverter")),
		RESET(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Reset the Inverter")),

//		TIME(Doc.of(OpenemsType.DOUBLE)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.NONE)),

//		DAILY_GENERATION(Doc.of(OpenemsType.LONG)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.KILOWATT_HOURS)),
//		CUMULATIVE_GENERATION(Doc.of(OpenemsType.LONG)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.KILOWATT_HOURS)),
	
//		NIGHT_SLEEP(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("Night Sleep Status")),
//		RSD_ENABLED(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("RSP Enable Status")),
//		DRM_ENABLED(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("DRM Enable Status")),
//		RIPPLE_CONTROL_ENABLED(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("Ripple Control Enable Status")),

//		NS_PROTECTION_ENABLED(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("Night Sleep Protection Status")),
//		NS_PROTECTION_SWITCH(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("Night Sleep Protection Switch")),

		ACTIVE_POWER_LIMIT_MODE(Doc.of(ActivePowerLimitMode.values())
				.accessMode(AccessMode.READ_WRITE)
				.text("Active Power Limit Mode")
				.persistencePriority(PersistencePriority.MEDIUM)),
		ACTIVE_POWER_LIMIT_PERCENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.PERCENT)
				.persistencePriority(PersistencePriority.MEDIUM)),

		REACTIVE_POWER_REGULATION_MODE(Doc.of(ReactivePowerLimitMode.values())
				.accessMode(AccessMode.READ_WRITE)
				.text("Reactive Power Regulation Mode")
				.persistencePriority(PersistencePriority.MEDIUM)),
		REACTIVE_POWER_REGULATION(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.MEDIUM)),
		REACTIVE_POWER_REGULATION_PERCENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.PERCENT)
				.persistencePriority(PersistencePriority.MEDIUM)),
		REACTIVE_POWER_FACTOR_REGULATION(Doc.of(OpenemsType.FLOAT)
				.accessMode(AccessMode.READ_WRITE)
				.persistencePriority(PersistencePriority.MEDIUM)),

		DC_VOLTAGE_MPPT_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_VOLTAGE_MPPT_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_VOLTAGE_MPPT_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_VOLTAGE_MPPT_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		DC_CURRENT_STRING_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_5(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_6(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_7(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_8(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_9(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)), 
		DC_CURRENT_STRING_10(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_11(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_12(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_13(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_14(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_15(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT_STRING_16(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),

		DC_POWER_MPPT_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_MPPT_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_MPPT_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_MPPT_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		DC_POWER_STRING_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_5(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_6(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_7(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_8(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_9(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_10(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_11(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_12(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_13(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_14(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_15(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER_STRING_16(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		DC_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.KILOWATT)
				.persistencePriority(PersistencePriority.HIGH)),

		APPERENT_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT_AMPERE)
				.persistencePriority(PersistencePriority.HIGH)),

		POWER_FACTOR(Doc.of(OpenemsType.FLOAT)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),

		TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.DEZIDEGREE_CELSIUS)
				.persistencePriority(PersistencePriority.HIGH)),
		INSULATION_RESISTANCE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.OHM)
				.persistencePriority(PersistencePriority.MEDIUM)),

//		UNIT_ID(Doc.of(OpenemsType.STRING)
//				.accessMode(AccessMode.READ_ONLY)
//				.text("The Modbus Unit ID of the Inverter")),

		RATED_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		RATED_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),

//		TODAY_YIELD(Doc.of(OpenemsType.LONG)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.KILOWATT_HOURS)),
//		CO2_REDUCTION(Doc.of(OpenemsType.LONG)
//				.accessMode(AccessMode.READ_ONLY)
//				.text("CO2 Reduction in kg")),
//		DAILY_RUNTIME(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.SECONDS)),
//		TOTAL_RUNTIME(Doc.of(OpenemsType.LONG)
//				.accessMode(AccessMode.READ_ONLY)
//				.unit(Unit.SECONDS)),

		FAULT_1(Doc.of(OpenemsType.INTEGER)),
		FAULT_2(Doc.of(OpenemsType.INTEGER)),
		FAULT_3(Doc.of(OpenemsType.INTEGER)),
		FAULT_4(Doc.of(OpenemsType.INTEGER)),
		FAULT_5(Doc.of(OpenemsType.INTEGER)),
		FAULT_6(Doc.of(OpenemsType.INTEGER)),
		FAULT_7(Doc.of(OpenemsType.INTEGER)),
		FAULT_8(Doc.of(OpenemsType.INTEGER)),
		FAULT_9(Doc.of(OpenemsType.INTEGER)),
		FAULT_CODE(Doc.of(OpenemsType.INTEGER)),

		ALARM_1(Doc.of(OpenemsType.INTEGER)),
		ALARM_2(Doc.of(OpenemsType.INTEGER)),
		ALARM_3(Doc.of(OpenemsType.INTEGER)),
		ALARM_4(Doc.of(OpenemsType.INTEGER)),
		ALARM_5(Doc.of(OpenemsType.INTEGER)),
		ALARM_6(Doc.of(OpenemsType.INTEGER)),
		ALARM_7(Doc.of(OpenemsType.INTEGER)),
		ALARM_8(Doc.of(OpenemsType.INTEGER)),
		ALARM_CODE(Doc.of(OpenemsType.INTEGER)),

		ARCING_1(Doc.of(OpenemsType.INTEGER)),
		ARCING_2(Doc.of(OpenemsType.INTEGER)),
		;

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
	 * Sets the Startup value. See {@link ChannelId#STARTUP}.
	 */
	public default void startup() throws OpenemsNamedException {
		IntegerWriteChannel startupChannel = this.channel(ChannelId.STARTUP);
		startupChannel.setNextWriteValue(1);
	}

	/**
	 * Sets the Shutdown value. See {@link ChannelId#SHUTDOWN}.
	 */
	public default void shutdown() throws OpenemsNamedException {
		IntegerWriteChannel shutdownChannel = this.channel(ChannelId.SHUTDOWN);
		shutdownChannel.setNextWriteValue(1);
	}

	/**
	 * Sets the Reset value. See {@link ChannelId#RESET}.
	 */
	public default void reset() throws OpenemsNamedException {
		IntegerWriteChannel resetChannel = this.channel(ChannelId.RESET);
		resetChannel.setNextWriteValue(1);
	}

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
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 *
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();

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
	 * Gets the Channel for {@link ChannelId#INVERTER_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<InverterState> getInverterStateChannel() {
		return this.channel(ChannelId.INVERTER_STATE);
	}

	/**
	 * Gets the {@link InverterState}, see {@link ChannelId#INVERTER_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<InverterState> getInverterState() {
		return this.getInverterStateChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default FloatReadChannel getPowerFactorChannel() {
		return this.channel(ChannelId.POWER_FACTOR);
	}

	/**
	 * Gets the Power Factor.
	 *
	 * @return the Channel {@link Value} containing the power factor
	 */
	public default Value<Float> getPowerFactor() {
		return this.getPowerFactorChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_LIMIT_MODE}.
	 *
	 * @return the Channel
	 */
	public default Channel<ActivePowerLimitMode> getActivePowerLimitModeChannel() {
		return this.channel(ChannelId.ACTIVE_POWER_LIMIT_MODE);
	}

	/**
	 * Gets the {@link ActivePowerLimitMode} channel value for {@link ChannelId#ACTIVE_POWER_LIMIT_MODE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<ActivePowerLimitMode> getActivePowerLimitMode() {
		return this.getActivePowerLimitModeChannel().value();
	}

	/**
	 * Sets the  {@link ActivePowerLimitMode} value. See {@link ChannelId#ACTIVE_POWER_LIMIT_MODE}.
	 *
	 * @param value the {@link ActivePowerLimitMode} value
	 * @throws OpenemsNamedException on error
	 */
	public default void setActivePowerLimitMode(ActivePowerLimitMode value) throws OpenemsNamedException {
		EnumWriteChannel activePowerModeChannel = this.channel(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_MODE);
		activePowerModeChannel.setNextWriteValue(value);
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

	public static void calculatePhasePowersFromVoltageAndCurrent(PvInverterHopewind meter) {
		var powerFactor = meter.getPowerFactorChannel();

		calculateL1PowersFromVoltageAndCurrent(meter, powerFactor);
		calculateL2PowersFromVoltageAndCurrent(meter, powerFactor);
		calculateL3PowersFromVoltageAndCurrent(meter, powerFactor);
	}

	private static void calculateL1PowersFromVoltageAndCurrent(ElectricityMeter meter, FloatReadChannel powerFactorL1) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL1,
				ElectricityMeter::getActivePowerL1Channel,
				ElectricityMeter::getReactivePowerL1Channel,
				ElectricityMeter::getVoltageL1Channel,
				ElectricityMeter::getCurrentL1Channel
	    );
	}

	private static void calculateL2PowersFromVoltageAndCurrent(ElectricityMeter meter, FloatReadChannel powerFactorL2) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL2,
				ElectricityMeter::getActivePowerL2Channel,
				ElectricityMeter::getReactivePowerL2Channel,
				ElectricityMeter::getVoltageL2Channel,
				ElectricityMeter::getCurrentL2Channel
	    );
	}

	private static void calculateL3PowersFromVoltageAndCurrent(ElectricityMeter meter, FloatReadChannel powerFactorL3) {
		_calculatePhasePowersFromVoltageAndCurrent(meter, powerFactorL3,
				ElectricityMeter::getActivePowerL3Channel,
				ElectricityMeter::getReactivePowerL3Channel,
				ElectricityMeter::getVoltageL3Channel,
				ElectricityMeter::getCurrentL3Channel
	    );
	}

	public static <M> void _calculatePhasePowersFromVoltageAndCurrent(
	        M meter,
	        FloatReadChannel powerFactorChannel,
	        Function<M, IntegerReadChannel> activePower,
	        Function<M, IntegerReadChannel> reactivePower,
	        Function<M, IntegerReadChannel> voltage,
	        Function<M, IntegerReadChannel> current
	) {
		IntegerReadChannel activePowerChannel = activePower.apply(meter);
		IntegerReadChannel reactivePowerChannel = reactivePower.apply(meter);
		IntegerReadChannel voltageChannel = voltage.apply(meter);
		IntegerReadChannel currentChannel = current.apply(meter);

	    powerFactorChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		voltageChannel.value().get(), currentChannel.value().get(), value.get());
	    });
	    voltageChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		value.get(), currentChannel.value().get(), powerFactorChannel.value().get());
	    });
	    currentChannel.onSetNextValue(value -> {
	        calculatePhasePowersFromVoltageAndCurrent(activePowerChannel, reactivePowerChannel,
	        		voltageChannel.value().get(), value.get(), powerFactorChannel.value().get());
	    });
	}

    private static void calculatePhasePowersFromVoltageAndCurrent(IntegerReadChannel activePower, IntegerReadChannel reactivePower,
    		Integer voltage, Integer current, Float PowerFactor) {
    	if (voltage == null || current == null || PowerFactor == null) {
    		return;
    	}
    	var apparentPower = calcualteApparentPhasePowerFromVoltageAndCurrent(voltage, current);

    	activePower.setNextValue(calculateActivePhasePowerFromApparentPower(apparentPower, PowerFactor));
    	reactivePower.setNextValue(calculateReactivePhasePowerFromApparentPower(apparentPower, PowerFactor));
    }

	private static int calcualteApparentPhasePowerFromVoltageAndCurrent(int voltage, int current) {
		return (int) (((double) voltage / 1000.0) * ((double) current / 1000.0));
	}

	private static int calculateActivePhasePowerFromApparentPower(int apparentPower, float powerFactor) {
		return (int) (apparentPower * powerFactor);
	}

	private static int calculateReactivePhasePowerFromApparentPower(int apparentPower, float powerFactor) {
		double phi = Math.acos(powerFactor);
		return (int) (apparentPower * Math.sin(phi));
	}
}
