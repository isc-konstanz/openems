package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "PV-Inverter.Hopewind",
		immediate = true,
		configurationPolicy = REQUIRE,
		property = {
				"type=PRODUCTION"
		})
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE,
})
public class PvInverterHopewindImpl extends AbstractOpenemsModbusComponent
		implements PvInverterHopewind, ManagedSymmetricPvInverter, ElectricityMeter,
		ModbusComponent, OpenemsComponent, EventHandler, ModbusSlave, TimedataProvider {

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	@Override
	@Reference(policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	protected Config config;

	public PvInverterHopewindImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				ManagedSymmetricPvInverter.ChannelId.values(),
				PvInverterHopewind.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		if (super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId(), this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
		this.config = config;
		if (!config.enabled()) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic() == EdgeEventConstants.TOPIC_CYCLE_EXECUTE_WRITE) {
			this.channel(PvInverterHopewind.ChannelId.WATCH_DOG).setNextValue(1);
		}
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC3ReadRegistersTask(12, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.POWER_LIMIT,
								new UnsignedWordElement(11), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.POWER_LIMIT_PERC,
								new UnsignedWordElement(12), SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(535, Priority.HIGH,
						m(ElectricityMeter.ChannelId.CURRENT_L1,
								new UnsignedWordElement(535), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.CURRENT_L2,
								new UnsignedWordElement(536), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.CURRENT_L3,
								new UnsignedWordElement(537), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.FREQUENCY,
								new UnsignedWordElement(538), SCALE_FACTOR_MINUS_2),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER,
								new UnsignedWordElement(539), SCALE_FACTOR_MINUS_2),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER,
								new UnsignedWordElement(540), SCALE_FACTOR_MINUS_2)),
				new FC6WriteRegisterTask(12,
						m(PvInverterHopewind.ChannelId.POWER_LIMIT,
								new UnsignedWordElement(12), SCALE_FACTOR_MINUS_2)),
				new FC6WriteRegisterTask(32014,
						m(PvInverterHopewind.ChannelId.WATCH_DOG,
								new UnsignedWordElement(32014), SCALE_FACTOR_MINUS_2)));
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.PRODUCTION;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

}
