package io.openems.edge.rct.cess;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.ess.api.SymmetricEss;

public class ChannelManager extends AbstractChannelListenerManager {

	private final RctCess parent;

	public ChannelManager(RctCess parent) {
		super();
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param battery       the {@link Battery}
	 * @param inverter      the {@link ManagedSymmetricBatteryInverter}
	 */
	public void activate(Battery battery, ManagedSymmetricBatteryInverter inverter) {
		this.addBatteryListener(battery);
		this.addBatteryInverterListener(inverter);
	}

	private void addBatteryInverterListener(ManagedSymmetricBatteryInverter batteryInverter) {
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY,
				SymmetricEss.ChannelId.ACTIVE_CHARGE_ENERGY);
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY,
				SymmetricEss.ChannelId.ACTIVE_DISCHARGE_ENERGY);
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.ACTIVE_POWER,
				SymmetricEss.ChannelId.ACTIVE_POWER);
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.GRID_MODE,
				SymmetricEss.ChannelId.GRID_MODE);
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.MAX_APPARENT_POWER,
				SymmetricEss.ChannelId.MAX_APPARENT_POWER);
		this.<Long>addCopyListener(batteryInverter,
				SymmetricBatteryInverter.ChannelId.REACTIVE_POWER,
				SymmetricEss.ChannelId.REACTIVE_POWER);

		if (batteryInverter instanceof ElectricityNode) {
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.ACTIVE_POWER_L1,
					ElectricityNode.ChannelId.ACTIVE_POWER_L1);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.ACTIVE_POWER_L2,
					ElectricityNode.ChannelId.ACTIVE_POWER_L2);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.ACTIVE_POWER_L3,
					ElectricityNode.ChannelId.ACTIVE_POWER_L3);

			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.REACTIVE_POWER_L1,
					ElectricityNode.ChannelId.REACTIVE_POWER_L1);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.REACTIVE_POWER_L2,
					ElectricityNode.ChannelId.REACTIVE_POWER_L2);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.REACTIVE_POWER_L3,
					ElectricityNode.ChannelId.REACTIVE_POWER_L3);

			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.VOLTAGE_L1,
					ElectricityNode.ChannelId.VOLTAGE_L1);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.VOLTAGE_L2,
					ElectricityNode.ChannelId.VOLTAGE_L2);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.VOLTAGE_L3,
					ElectricityNode.ChannelId.VOLTAGE_L3);

			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.CURRENT_L1,
					ElectricityNode.ChannelId.CURRENT_L1);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.CURRENT_L2,
					ElectricityNode.ChannelId.CURRENT_L2);
			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.CURRENT_L3,
					ElectricityNode.ChannelId.CURRENT_L3);

			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.FREQUENCY,
					ElectricityNode.ChannelId.FREQUENCY);

			this.<Long>addCopyListener(batteryInverter,
					ElectricityNode.ChannelId.POWER_FACTOR,
					ElectricityNode.ChannelId.POWER_FACTOR);
		}
	}

	private void addBatteryListener(Battery battery) {
		this.addCopyListener(battery,
				Battery.ChannelId.CAPACITY,
				SymmetricEss.ChannelId.CAPACITY);
		this.addCopyListener(battery,
				Battery.ChannelId.SOC,
				SymmetricEss.ChannelId.SOC);
		this.addCopyListener(battery,
				Battery.ChannelId.MIN_CELL_VOLTAGE,
				SymmetricEss.ChannelId.MIN_CELL_VOLTAGE);
		this.addCopyListener(battery,
				Battery.ChannelId.MAX_CELL_VOLTAGE,
				SymmetricEss.ChannelId.MAX_CELL_VOLTAGE);
		this.addCopyListener(battery,
				Battery.ChannelId.MIN_CELL_TEMPERATURE,
				SymmetricEss.ChannelId.MIN_CELL_TEMPERATURE);
		this.addCopyListener(battery,
				Battery.ChannelId.MAX_CELL_TEMPERATURE,
				SymmetricEss.ChannelId.MAX_CELL_TEMPERATURE);
	}

	/**
	 * Adds a Copy-Listener. It listens on setNextValue() and copies the value to the target channel.
	 *
	 * @param <T>             the Channel-Type
	 * @param sourceComponent the source component - Battery or BatteryInverter
	 * @param sourceChannelId the source ChannelId
	 * @param targetChannelId the target ChannelId
	 */
	protected <T> void addCopyListener(OpenemsComponent sourceComponent, ChannelId sourceChannelId,
			ChannelId targetChannelId) {
		this.<T>addOnSetNextValueListener(sourceComponent, sourceChannelId, value -> {
			Channel<T> targetChannel = this.parent.channel(targetChannelId);
			targetChannel.setNextValue(value);
		});
	}

}
