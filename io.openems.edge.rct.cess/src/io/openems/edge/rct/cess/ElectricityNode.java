package io.openems.edge.rct.cess;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;

public interface ElectricityNode extends OpenemsComponent {

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL1Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L1);
	}

	/**
	 * Gets the Active Power on L1 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL1() {
		return this.getActivePowerL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(Integer value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL1(int value) {
		this.getActivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL2Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L2);
	}

	/**
	 * Gets the Active Power on L2 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL2() {
		return this.getActivePowerL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(Integer value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL2(int value) {
		this.getActivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getActivePowerL3Channel() {
		return this.channel(ChannelId.ACTIVE_POWER_L3);
	}

	/**
	 * Gets the Active Power on L3 in [W]. Negative values for Consumption (power
	 * that is 'leaving the system', e.g. feed-to-grid); positive for Production
	 * (power that is 'entering the system'). See {@link ChannelId#ACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getActivePowerL3() {
		return this.getActivePowerL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(Integer value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#ACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setActivePowerL3(int value) {
		this.getActivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL1Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L1);
	}

	/**
	 * Gets the Reactive Power on L1 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL1() {
		return this.getReactivePowerL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL1(Integer value) {
		this.getReactivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL1(int value) {
		this.getReactivePowerL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL2Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L2);
	}

	/**
	 * Gets the Reactive Power on L2 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL2() {
		return this.getReactivePowerL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL2(Integer value) {
		this.getReactivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL2(int value) {
		this.getReactivePowerL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#REACTIVE_POWER_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getReactivePowerL3Channel() {
		return this.channel(ChannelId.REACTIVE_POWER_L3);
	}

	/**
	 * Gets the Reactive Power on L3 in [var]. See
	 * {@link ChannelId#REACTIVE_POWER_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getReactivePowerL3() {
		return this.getReactivePowerL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL3(Integer value) {
		this.getReactivePowerL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#REACTIVE_POWER_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReactivePowerL3(int value) {
		this.getReactivePowerL3Channel().setNextValue(value);
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
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(Float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactor(float value) {
		this.getPowerFactorChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL1Channel() {
		return this.channel(ChannelId.VOLTAGE_L1);
	}

	/**
	 * Gets the Voltage on L1 in [mV]. See {@link ChannelId#VOLTAGE_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL1() {
		return this.getVoltageL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(Integer value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL1(int value) {
		this.getVoltageL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL2Channel() {
		return this.channel(ChannelId.VOLTAGE_L2);
	}

	/**
	 * Gets the Voltage on L2 in [mV]. See {@link ChannelId#VOLTAGE_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL2() {
		return this.getVoltageL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(Integer value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL2(int value) {
		this.getVoltageL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getVoltageL3Channel() {
		return this.channel(ChannelId.VOLTAGE_L3);
	}

	/**
	 * Gets the Voltage on L3 in [mV]. See {@link ChannelId#VOLTAGE_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getVoltageL3() {
		return this.getVoltageL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(Integer value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#VOLTAGE_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setVoltageL3(int value) {
		this.getVoltageL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL1Channel() {
		return this.channel(ChannelId.CURRENT_L1);
	}

	/**
	 * Gets the Current L1 in [mA]. See
	 * {@link ChannelId#CURRENT_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL1() {
		return this.getCurrentL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL2Channel() {
		return this.channel(ChannelId.CURRENT_L2);
	}

	/**
	 * Gets the Current L2 in [mA]. See
	 * {@link ChannelId#CURRENT_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL2() {
		return this.getCurrentL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCurrentL3Channel() {
		return this.channel(ChannelId.CURRENT_L3);
	}

	/**
	 * Gets the Current L3 in [mA]. See
	 * {@link ChannelId#CURRENT_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCurrentL3() {
		return this.getCurrentL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#FREQUENCY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getFrequencyChannel() {
		return this.channel(ChannelId.FREQUENCY);
	}

	/**
	 * Gets the Frequency in [mHz]. See
	 * {@link ChannelId#FREQUENCY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getFrequency() {
		return this.getFrequencyChannel().value();
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Active Power L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		ACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Active Power L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		ACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Active Power L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#WATT}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		ACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Reactive Power L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Reactive Power L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Reactive Power L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#VOLT_AMPERE_REACTIVE}
		 * </ul>
		 */
		REACTIVE_POWER_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Voltage L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Voltage L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIVOLT}
		 * <li>Range: only positive values
		 * </ul>
		 */
		VOLTAGE_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIVOLT)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Current L1.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		CURRENT_L1(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Current L2.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		CURRENT_L2(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),
		/**
		 * Current L3.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIAMPERE}
		 * <li>Range: see {@link ElectricityNode}
		 * </ul>
		 */
		CURRENT_L3(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIAMPERE)
				.persistencePriority(PersistencePriority.HIGH)),

		/**
		 * Frequency.
		 *
		 * <ul>
		 * <li>Type: {@link OpenemsType#INTEGER}
		 * <li>Unit: {@link Unit#MILLIHERTZ}
		 * <li>Range: only positive values
		 * </ul>
		 */
		FREQUENCY(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.MILLIHERTZ)
				.persistencePriority(PersistencePriority.HIGH)),

		POWER_FACTOR(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)),
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