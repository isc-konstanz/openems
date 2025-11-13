package io.openems.edge.rct.cess.batteryinverter;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.rct.cess.ElectricityNode;
import io.openems.edge.rct.cess.batteryinverter.enums.RunState;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.TimedataProvider;

public interface BatteryInverterRctCess extends 
		ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, ElectricityNode,
		OpenemsComponent, ModbusComponent, ModbusSlave, TimedataProvider, StartStoppable {

	public static final int MAX_APPARENT_POWER = 100_000; // [W]

	public static final int APPARENT_POWER_PRECISION = 100; // [W]

	public static final int DC_MIN_VOLTAGE = 200;
	public static final int DC_MAX_VOLTAGE = 950;

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
	 * Gets the Channel for {@link ChannelId#RUN_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<RunState> getRunStateChannel() {
		return this.channel(ChannelId.RUN_STATE);
	}

	/**
	 * Gets the {@link RunState}, see {@link ChannelId#RUN_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<RunState> getRunState() {
		return this.getRunStateChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1_2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1L2Channel() {
		return this.channel(ChannelId.VOLTAGE_L1_2);
	}

	/**
	 * Gets the Voltage L1-L2 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L1_2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1L2() {
		return this.getVoltageL1L2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2_3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2L3Channel() {
		return this.channel(ChannelId.VOLTAGE_L2_3);
	}

	/**
	 * Gets the Voltage L2-L3 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L2_3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2L3() {
		return this.getVoltageL2L3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3_1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3L1Channel() {
		return this.channel(ChannelId.VOLTAGE_L3_1);
	}

	/**
	 * Gets the Voltage L3-L1 in [mV]. See
	 * {@link ChannelId#VOLTAGE_L3_1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3L1() {
		return this.getVoltageL3L1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcVoltageChannel() {
		return this.channel(ChannelId.DC_VOLTAGE);
	}

	/**
	 * Gets the DC Voltage in [V]. See
	 * {@link ChannelId#DC_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcVoltage() {
		return this.getDcVoltageChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_CURRENT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcCurrentChannel() {
		return this.channel(ChannelId.DC_CURRENT);
	}

	/**
	 * Gets the DC Current in [A]. See
	 * {@link ChannelId#DC_CURRENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcCurrent() {
		return this.getDcCurrentChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getDcPowerChannel() {
		return this.channel(ChannelId.DC_POWER);
	}

	/**
	 * Gets the DC Power in [W]. See
	 * {@link ChannelId#DC_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getDcPower() {
		return this.getDcPowerChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#AIR_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getAirTemperatureChannel() {
		return this.channel(ChannelId.AIR_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the Air in [°C]. See
	 * {@link ChannelId#AIR_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getAirTemperature() {
		return this.getAirTemperatureChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#IGBT_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getIgbtTemperatureChannel() {
		return this.channel(ChannelId.IGBT_TEMPERATURE);
	}

	/**
	 * Gets the Temperature of the IGBT in [°C]. See
	 * {@link ChannelId#IGBT_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getIgbtTemperature() {
		return this.getIgbtTemperatureChannel().value();
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),

		RUN_STATE(Doc.of(RunState.values())),

		/**
		 * Voltage L1-L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L1_2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)),
		/**
		 * Voltage L2-3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L2_3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)),
		/**
		 * Voltage L3-L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L3_1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)),

		DC_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_CURRENT(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		DC_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		// TODO: Rename to TEMPERATURE_ENVIRONMENT and *_IGBT to be consistent
		// with other inverters, as soon as the UI supports it.
		AIR_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEGREE_CELSIUS)),
		IGBT_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEGREE_CELSIUS)),

		// Alarm 1
		EP0_FAULT(Doc.of(Level.WARNING)),
		IGBT_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),
		BUSBAR_VOLTAGE_HIGH_FAULT(Doc.of(Level.WARNING)),
		POWER_MODULE_CURRENT_LIMIT_FAULT(Doc.of(Level.WARNING)),
		BALANCE_MODULE_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),

		// Alarm 2
		VOLTAGE_24_FAULT(Doc.of(Level.WARNING)),
		FAN_FAULT(Doc.of(Level.WARNING)),
		CONNECTION_FAULT(Doc.of(Level.WARNING)),
		SPD_FAULT(Doc.of(Level.WARNING)),
		POWER_MODULE_TEMPERATURE_HIGH_FAULT(Doc.of(Level.WARNING)),
		BALANCE_MODULE_TEMPERATURE_HIGH_FAULT(Doc.of(Level.WARNING)),
		VOLTAGE_15_FAULT(Doc.of(Level.WARNING)),
		FIRE_SYSTEM_ALARM(Doc.of(Level.WARNING)),
		BATTERY_DRY_FAULT(Doc.of(Level.WARNING)),
		OVERLOAD_FAULT(Doc.of(Level.WARNING)),

		// Alarm 3
		VOLTAGE_HIGH_L1(Doc.of(Level.INFO)),
		VOLTAGE_HIGH_L2(Doc.of(Level.INFO)),
		VOLTAGE_HIGH_L3(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L1(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L2(Doc.of(Level.INFO)),
		VOLTAGE_LOW_L3(Doc.of(Level.INFO)),
		GRID_FREQUENCY_HIGH(Doc.of(Level.INFO)),
		GRID_FREQUENCY_LOW(Doc.of(Level.INFO)),
		GRID_PHASE_SEQUENCE_FAULT(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L1(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L2(Doc.of(Level.INFO)),
		SOFT_WORK_CURRENT_HIGH_L3(Doc.of(Level.INFO)),
		GRID_VOLTAGE_UNBALANCE(Doc.of(Level.INFO)),
		GRID_CURRENT_UNBALANCE(Doc.of(Level.INFO)),
		GRID_LOSS_PHASE(Doc.of(Level.INFO)),
		N_CURRENT_HIGH(Doc.of(Level.INFO)),

		// Alarm 4
		PRE_CHARGE_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		PRE_CHARGE_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		RUN_BUS_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		RUN_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		POSITIVE_NEGATIVE_BUS_UNBALANCE(Doc.of(Level.INFO)),
		CURRENT_MODE_BUS_VOLTAGE_LOW(Doc.of(Level.INFO)),
		CELL_VOLTAGE_LOW(Doc.of(Level.INFO)),
		CELL_VOLTAGE_HIGH(Doc.of(Level.INFO)),
		AC_PRE_CHARGE_CURRENT_HIGH(Doc.of(Level.INFO)),
		AC_CURRENT_HIGH(Doc.of(Level.INFO)),
		BALANCE_MODULE_SOFTWARE_CURRENT_HIGH(Doc.of(Level.INFO)),
		BATTERY_REVERSE(Doc.of(Level.INFO)),

		// Alarm 5
		PRE_CHARGE_TIMEOUT(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L1(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L2(Doc.of(Level.INFO)),
		PRE_CHARGE_CURRENT_HIGH_L3(Doc.of(Level.INFO)),

		// Alarm 6
		AD_NULL_SHIFT_FAULT(Doc.of(Level.WARNING)),
		BMS_CELL_FAULT(Doc.of(Level.WARNING)),
		STS_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
		BMS_CONNECTION_FAIL(Doc.of(Level.WARNING)),
		CAN_CONNECTION_FAULT(Doc.of(Level.WARNING)),
		EMS_CONNECTION_FAULT(Doc.of(Level.WARNING)),

		// Alarm 7
		PRE_CHARGE_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_CLOSE_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_OPEN_STATUS_FAULT(Doc.of(Level.WARNING)),
		PRE_CHARGE_RELAY_CLOSE_STATUS_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_CLOSE_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_OPEN_STATUS_FAULT(Doc.of(Level.WARNING)),
		MAIN_RELAY_CLOSE_STATUS_FAULT(Doc.of(Level.WARNING)),
		AC_MAIN_RELAY_ADHESIVE_FAULT(Doc.of(Level.WARNING)),
		DC_RELAY_OPEN_FAULT(Doc.of(Level.WARNING)),

		// Alarm 8
		INVERTER_VOLTAGE_HIGH_L1_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_HIGH_L2_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_HIGH_L3_FAULT(Doc.of(Level.WARNING)),
		ISLAND_ENABLE_FAULT(Doc.of(Level.WARNING)),
		SYSTEM_RESONANCE_FAULT(Doc.of(Level.WARNING)),
		SOFT_WORK_VOLTAGE_HIGH_CURRENT_HIGH_FAULT(Doc.of(Level.WARNING)),
		MODULE_DIAL_UP_ADDRESS_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L1_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L2_FAULT(Doc.of(Level.WARNING)),
		INVERTER_VOLTAGE_LOW_L3_FAULT(Doc.of(Level.WARNING)),
		OFFGRID_NO_SYNCHRONIZATION_SIGNAL_FAULT(Doc.of(Level.WARNING)),
		OFFGRID_SHORT_CIRCUIT_FAULT(Doc.of(Level.WARNING)),
		VOLTAGE_LOW_CROSS_OVER_TIME_FAULT(Doc.of(Level.WARNING)),
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

}