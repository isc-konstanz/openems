package io.openems.edge.rct.cess;

import java.util.List;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.jsonapi.ComponentJsonApi;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.ess.api.EssErrorAcknowledge;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.rct.cess.battery.RctCessBattery;
import io.openems.edge.rct.cess.batteryinverter.RctCessBatteryInverter;
import io.openems.edge.rct.cess.charger.RctCessDcCharger;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.TimedataProvider;

public interface RctCess extends HybridEss, ManagedSymmetricEss, SymmetricEss, EssErrorAcknowledge,
		ElectricityNode, OpenemsComponent, ModbusComponent, ModbusSlave, ComponentJsonApi,
		CycleProvider, TimedataProvider, EventHandler, StartStoppable {

	/**
	 * Efficiency factor to calculate AC Charge/Discharge limits from DC. Used at
	 * {@link ChannelManager}.
	 */
	public static final float EFFICIENCY_FACTOR = 0.95F;

	/**
	 * Allow a maximum power increase per second.
	 *
	 * <p>
	 * 5 % of possible allowed charge/discharge power
	 */
	public static final float MAX_POWER_INCREASE_PERCENTAGE = 0.05F;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.FAULT)
				.text("Running the Logic failed")),

		PV_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.persistencePriority(PersistencePriority.MEDIUM)),

		/**
		 * Sets the Active Power in [W].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: W
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_ACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.WATT)
				.accessMode(AccessMode.WRITE_ONLY)),
		/**
		 * Sets the Reactive Power in [var].
		 *
		 * <ul>
		 * <li>Type: Integer
		 * <li>Unit: var
		 * <li>Range: negative values for Charge; positive for Discharge
		 * </ul>
		 */
		SET_REACTIVE_POWER(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.VOLT_AMPERE_REACTIVE)
				.accessMode(AccessMode.WRITE_ONLY)),
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
	 * Gets the Channel for {@link ChannelId#PV_POWER}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getPvPowerChannel() {
		return this.channel(ChannelId.PV_POWER);
	}

	/**
	 * Gets the Photovoltaics Power in [W]. See
	 * {@link ChannelId#PV_POWER}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getPvPower() {
		return this.getPvPowerChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_PV_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPvPower(Integer value) {
		this.getPvPowerChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#DC_PV_POWER} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPvPower(int value) {
		this.getPvPowerChannel().setNextValue(value);
	}

	/**
	 * Returns whether this {@link ManagedSymmetricBatteryInverter} has {@link EssDcCharger} available or not.
	 *
	 * @return the list of {@link EssDcCharger}
	 */
	public boolean hasDcChargers();

	/**
	 * Gets the list of {@link EssDcCharger} of this {@link ManagedSymmetricBatteryInverter}.
	 *
	 * @return the list of {@link EssDcCharger}
	 */
	public List<RctCessDcCharger> getDcChargers();

	/**
	 * Gets the {@link Battery} of this {@link ManagedSymmetricEss}.
	 *
	 * @return the {@link Battery}
	 */
	public RctCessBattery getBattery();

	/**
	 * Gets the {@link SymmetricBatteryInverter} of this {@link ManagedSymmetricEss}.
	 *
	 * @return the {@link SymmetricBatteryInverter}
	 */
	public RctCessBatteryInverter getBatteryInverter();

}
