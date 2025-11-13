package io.openems.edge.rct.cess.meter;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.FloatReadChannel;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterRctCess extends
		ElectricityMeter, OpenemsComponent, ModbusComponent, ModbusSlave, EventHandler {

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
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL1Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L1);
	}

	/**
	 * Gets the Power Factor on L1.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPowerFactorL1() {
		return this.getPowerFactorL1Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL1(Float value) {
		this.getPowerFactorL1Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L1}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL1(float value) {
		this.getPowerFactorL1Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL2Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L2);
	}

	/**
	 * Gets the Power Factor on L2.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPowerFactorL2() {
		return this.getPowerFactorL2Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL2(Float value) {
		this.getPowerFactorL2Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L2}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL2(float value) {
		this.getPowerFactorL2Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#POWER_FACTOR_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getPowerFactorL3Channel() {
		return this.channel(ChannelId.POWER_FACTOR_L3);
	}

	/**
	 * Gets the Power Factor on L3.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getPowerFactorL3() {
		return this.getPowerFactorL3Channel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL3(Float value) {
		this.getPowerFactorL3Channel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#POWER_FACTOR_L3}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPowerFactorL3(float value) {
		this.getPowerFactorL3Channel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL1Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L1 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL1() {
		return this.getVoltageHarmonicDistortionL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL2Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L2 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL2() {
		return this.getVoltageHarmonicDistortionL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getVoltageHarmonicDistortionL3Channel() {
		return this.channel(ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3);
	}

	/**
	 * Gets the Voltage Harmonic Distortion on L3 in [%]. See
	 * {@link ChannelId#VOLTAGE_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getVoltageHarmonicDistortionL3() {
		return this.getVoltageHarmonicDistortionL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL1Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L1);
	}

	/**
	 * Gets the Current Harmonic Distortion on L1 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L1}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL1() {
		return this.getCurrentHarmonicDistortionL1Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL2Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L2);
	}

	/**
	 * Gets the Current Harmonic Distortion on L2 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L2}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL2() {
		return this.getCurrentHarmonicDistortionL2Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel
	 */
	public default FloatReadChannel getCurrentHarmonicDistortionL3Channel() {
		return this.channel(ChannelId.CURRENT_HARMONIC_DISTORTION_L3);
	}

	/**
	 * Gets the Current Harmonic Distortion on L3 in [%]. See
	 * {@link ChannelId#CURRENT_HARMONIC_DISTORTION_L3}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Float> getCurrentHarmonicDistortionL3() {
		return this.getCurrentHarmonicDistortionL3Channel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#VOLTAGE_RATIO}.
	 *
	 * @return the Channel for the voltage ratio
	 */
	public default IntegerReadChannel getVoltageRatioChannel() {
		return this.channel(ChannelId.VOLTAGE_RATIO);
	}

	/**
	 * Gets the Integer Voltage Ratio (PT) for {@link ChannelId#VOLTAGE_RATIO}.
	 *
	 * @return the Channel {@link Value} containing the voltage ratio
	 */
	public default Value<Integer> getVoltageRatio() {
		return this.getVoltageRatioChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#CURRENT_RATIO}.
	 *
	 * @return the Channel for the current ratio
	 */
	public default IntegerReadChannel getCurrentRatioChannel() {
		return this.channel(ChannelId.CURRENT_RATIO);
	}

	/**
	 * Gets the Integer Current Ratio (CT) for {@link ChannelId#CURRENT_RATIO}.
	 *
	 * @return the Channel {@link Value} containing the current ratio
	 */
	public default Value<Integer> getCurrentRatio() {
		return this.getCurrentRatioChannel().value();
	}

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		POWER_FACTOR(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),
		POWER_FACTOR_L1(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),
		POWER_FACTOR_L2(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),
		POWER_FACTOR_L3(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.NONE)
				.persistencePriority(PersistencePriority.HIGH)),
	
		VOLTAGE_HARMONIC_DISTORTION_L1(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
		VOLTAGE_HARMONIC_DISTORTION_L2(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
		VOLTAGE_HARMONIC_DISTORTION_L3(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
	
		CURRENT_HARMONIC_DISTORTION_L1(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
		CURRENT_HARMONIC_DISTORTION_L2(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
		CURRENT_HARMONIC_DISTORTION_L3(Doc.of(OpenemsType.FLOAT)
				.unit(Unit.PERCENT)),
	
		VOLTAGE_RATIO(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.NONE)),
		CURRENT_RATIO(Doc.of(OpenemsType.INTEGER)
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