package io.openems.edge.pvinverter.hopewind;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

public interface PvInverterHopewind extends ManagedSymmetricPvInverter, ElectricityMeter,
		ModbusComponent, OpenemsComponent, EventHandler, ModbusSlave {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		WATCH_DOG(Doc.of(OpenemsType.INTEGER)),

//		ACTIVE_POWER_LIMIT_TYPE(Doc.of(PowerLimitType.values())
//				.accessMode(AccessMode.READ_WRITE)),
		ACTIVE_POWER_LIMIT_PERC(Doc.of(OpenemsType.INTEGER)
				.unit(Unit.PERCENT)
				.accessMode(AccessMode.READ_WRITE)
				.persistencePriority(PersistencePriority.MEDIUM));

//		ACTIVE_POWER_LIMIT_FAILED(Doc.of(Level.FAULT)
//				.text("Power-Limit failed"));

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
