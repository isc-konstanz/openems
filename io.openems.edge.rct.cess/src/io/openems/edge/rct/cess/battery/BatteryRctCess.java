package io.openems.edge.rct.cess.battery;

import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.rct.cess.battery.enums.PreChargeState;
import io.openems.edge.rct.cess.battery.enums.RackChargeState;
import io.openems.edge.rct.cess.battery.enums.RunState;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

public interface BatteryRctCess extends Battery,
		OpenemsComponent, ModbusComponent, ModbusSlave, StartStoppable {

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
	 * Gets the Channel for {@link ChannelId#PRE_CHARGE_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<PreChargeState> getPreChargeStateChannel() {
		return this.channel(ChannelId.PRE_CHARGE_STATE);
	}

	/**
	 * Gets the {@link PreChargeState}, see {@link ChannelId#PRE_CHARGE_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<PreChargeState> getPreChargeState() {
		return this.getPreChargeStateChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#PRE_CHARGE_TOTAL_VOLT}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getPreChargeTotalVoltChannel() {
		return this.channel(ChannelId.PRE_CHARGE_TOTAL_VOLT);
	}

	/**
	 * Gets the Pre-Charge total voltage in [V]. See
	 * {@link ChannelId#PRE_CHARGE_TOTAL_VOLT}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getPreChargeTotalVolt() {
		return this.getPreChargeTotalVoltChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getPowerChannel() {
		return this.channel(ChannelId.POWER);
	}

	/**
	 * Gets the Power in [W]. See
	 * {@link ChannelId#POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getPower() {
		return this.getPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPower(Integer value) {
		this.getPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPower(int value) {
		this.getPowerChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#INSULATION_VALUE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getInsulationValueChannel() {
		return this.channel(ChannelId.INSULATION_VALUE);
	}

	/**
	 * Gets the insulation resistance value in [Ohm]. See
	 * {@link ChannelId#INSULATION_VALUE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInsulationValue() {
		return this.getInsulationValueChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#INSULATION_POSITIVE_VALUE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getInsulationPositiveValueChannel() {
		return this.channel(ChannelId.INSULATION_POSITIVE_VALUE);
	}

	/**
	 * Gets the positive insulation resistance value in [Ohm]. See
	 * {@link ChannelId#INSULATION_POSITIVE_VALUE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInsulationPositiveValue() {
		return this.getInsulationPositiveValueChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#INSULATION_NEGATIVE_VALUE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getInsulationNegativeValueChannel() {
		return this.channel(ChannelId.INSULATION_NEGATIVE_VALUE);
	}

	/**
	 * Gets the negative insulation resistance value in [Ohm]. See
	 * {@link ChannelId#INSULATION_NEGATIVE_VALUE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getInsulationNegativeValue() {
		return this.getInsulationNegativeValueChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxCellTemperatureIndexChannel() {
		return this.channel(ChannelId.MAX_CELL_TEMPERATURE_INDEX);
	}

	/**
	 * Gets the Max Cell Temperature Index. See
	 * {@link ChannelId#MAX_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxCellTemperatureIndex() {
		return this.getMaxCellTemperatureIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMaxCellVoltageIndexChannel() {
		return this.channel(ChannelId.MAX_CELL_VOLTAGE_INDEX);
	}

	/**
	 * Gets the Max Cell Voltage Index. See
	 * {@link ChannelId#MAX_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMaxCellVoltageIndex() {
		return this.getMaxCellVoltageIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinCellTemperatureIndexChannel() {
		return this.channel(ChannelId.MIN_CELL_TEMPERATURE_INDEX);
	}

	/**
	 * Gets the Min Cell Temperature Index. See
	 * {@link ChannelId#MIN_CELL_TEMPERATURE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinCellTemperatureIndex() {
		return this.getMinCellTemperatureIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMinCellVoltageIndexChannel() {
		return this.channel(ChannelId.MIN_CELL_VOLTAGE_INDEX);
	}

	/**
	 * Gets the Min Cell Voltage Index. See
	 * {@link ChannelId#MIN_CELL_VOLTAGE_INDEX}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMinCellVoltageIndex() {
		return this.getMinCellVoltageIndexChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#SUM_CELL_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSumCellVoltageChannel() {
		return this.channel(ChannelId.SUM_CELL_VOLTAGE);
	}

	/**
	 * Gets the Sum Cell Voltage in [mV]. See
	 * {@link ChannelId#SUM_CELL_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSumCellVoltage() {
		return this.getSumCellVoltageChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MEAN_CELL_VOLTAGE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMeanCellVoltageChannel() {
		return this.channel(ChannelId.MEAN_CELL_VOLTAGE);
	}

	/**
	 * Gets the Mean Cell Voltage in [mV]. See
	 * {@link ChannelId#MEAN_CELL_VOLTAGE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMeanCellVoltage() {
		return this.getMeanCellVoltageChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#MEAN_CELL_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getMeanCellTemperatureChannel() {
		return this.channel(ChannelId.MEAN_CELL_TEMPERATURE);
	}

	/**
	 * Gets the Mean Cell Temperature in [°C]. See
	 * {@link ChannelId#MEAN_CELL_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getMeanCellTemperature() {
		return this.getMeanCellTemperatureChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#SWITCH_BOX_TEMPERATURE}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getSwitchBoxTemperatureChannel() {
		return this.channel(ChannelId.SWITCH_BOX_TEMPERATURE);
	}

	/**
	 * Gets the Switch Box Temperature in [°C]. See
	 * {@link ChannelId#SWITCH_BOX_TEMPERATURE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getSwitchBoxTemperature() {
		return this.getSwitchBoxTemperatureChannel().value();
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),

		RUN_STATE(Doc.of(RunState.values())),

		RACK_CHARGE_STATE(Doc.of(RackChargeState.values())),

		// Connection States
		DISCONNECTOR_STATE(Doc.of(OpenemsType.BOOLEAN)),
		CONTACTOR_POSITIVE_STATE(Doc.of(OpenemsType.BOOLEAN)),
		CONTACTOR_NEGATIVE_STATE(Doc.of(OpenemsType.BOOLEAN)),
		PRE_CHARGE_CONNTACTOR_STATE(Doc.of(OpenemsType.BOOLEAN)),

		PRE_CHARGE_STATE(Doc.of(PreChargeState.values())),

		PRE_CHARGE_TOTAL_VOLT(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT)),

		POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		INSULATION_VALUE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.OHM)),

		INSULATION_POSITIVE_VALUE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.OHM)),

		INSULATION_NEGATIVE_VALUE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.OHM)),

		MAX_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)),

		MAX_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)),

		MIN_CELL_TEMPERATURE_INDEX(Doc.of(OpenemsType.INTEGER)),

		MIN_CELL_VOLTAGE_INDEX(Doc.of(OpenemsType.INTEGER)),

		SUM_CELL_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)),

		MEAN_CELL_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)),

		MEAN_CELL_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEGREE_CELSIUS)),

		SWITCH_BOX_TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.DEGREE_CELSIUS)),

		// Rack Faults
		BMU_HARDWARE_FAULT(Doc.of(Level.WARNING)),
		BCU_HARDWARE_FAULT(Doc.of(Level.WARNING)),
		FUSE_PROTECTOR_FAULT(Doc.of(Level.WARNING)),
		CONTACTOR_ADHESION_FAULT(Doc.of(Level.WARNING)),
		BMU_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
		BAU_COMMUNICATION_FAULT(Doc.of(Level.WARNING)),
		CURRENT_SENSOR_FAULT(Doc.of(Level.WARNING)),
		INSULATION_MONITOR_FAULT(Doc.of(Level.WARNING)),
		DISCONNECTOR_ABNORMAL_FAULT(Doc.of(Level.WARNING)),

		// Rack Warnings
		TOTAL_VOLTAGE_HIGH_WARNING(Doc.of(Level.INFO)),
		TOTAL_VOLTAGE_LOW_WARNING(Doc.of(Level.INFO)),
		CELL_VOLTAGE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_VOLTAGE_LOW_WARNING(Doc.of(Level.INFO)),
		CELL_CURRENT_DISCHARGE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_CURRENT_CHARGE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_TEMPERATURE_DISCHARGE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_TEMPERATURE_DISCHARGE_LOW_WARNING(Doc.of(Level.INFO)),
		CELL_TEMPERATURE_CHARGE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_TEMPERATURE_CHARGE_LOW_WARNING(Doc.of(Level.INFO)),
		INSULATION_LOW_WARNING(Doc.of(Level.INFO)),
		INSULATION_HIGH_WARNING(Doc.of(Level.INFO)),
		SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_VOLTAGE_DIFFERENCE_HIGH_WARNING(Doc.of(Level.INFO)),
		CELL_TEMPERATURE_DIFFERENCE_HIGH_WARNING(Doc.of(Level.INFO)),
		SOC_LOW_WARNING(Doc.of(Level.INFO)),

		// Rack Alarms
		TOTAL_VOLTAGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		TOTAL_VOLTAGE_LOW_ALARM(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_LOW_ALARM(Doc.of(Level.WARNING)),
		CELL_CURRENT_DISCHARGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_CURRENT_CHARGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DISCHARGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DISCHARGE_LOW_ALARM(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_CHARGE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_CHARGE_LOW_ALARM(Doc.of(Level.WARNING)),
		INSULATION_LOW_ALARM(Doc.of(Level.WARNING)),
		INSULATION_HIGH_ALARM(Doc.of(Level.WARNING)),
		SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_DIFFERENCE_HIGH_ALARM(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DIFFERENCE_HIGH_ALARM(Doc.of(Level.WARNING)),
		SOC_LOW_ALARM(Doc.of(Level.WARNING)),

		// Rack Critical Alarms
		TOTAL_VOLTAGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		TOTAL_VOLTAGE_LOW_CRITICAL(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_LOW_CRITICAL(Doc.of(Level.WARNING)),
		CELL_CURRENT_DISCHARGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_CURRENT_CHARGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DISCHARGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DISCHARGE_LOW_CRITICAL(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_CHARGE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_CHARGE_LOW_CRITICAL(Doc.of(Level.WARNING)),
		INSULATION_LOW_CRITICAL(Doc.of(Level.WARNING)),
		INSULATION_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_VOLTAGE_DIFFERENCE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		CELL_TEMPERATURE_DIFFERENCE_HIGH_CRITICAL(Doc.of(Level.WARNING)),
		SOC_LOW_CRITICAL(Doc.of(Level.WARNING)),
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