package io.openems.edge.meter.eastron.sdm72dm;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_POWER;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.referencetarget.GenerateTargetsFromReferences;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.FloatDoublewordElement;
import io.openems.edge.bridge.modbus.api.task.FC4ReadInputRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Implements the Eastron SDM72D-M (V1) meter.
 *
 * <p>
 * Unlike the SDM630 and SDM72D-M-2 (V2), the SDM72D-M (V1) only provides a
 * small set of input registers (total power and energies; no per-phase values)
 * and rejects requests for more than 30 parameters or for undocumented
 * registers. Therefore only documented registers are read, each in its own
 * small request.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Eastron.SDM72DM", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@GenerateTargetsFromReferences("Modbus")
public class MeterEastronSdm72dmImpl extends AbstractOpenemsModbusComponent
		implements MeterEastronSdm72dm, ElectricityMeter, ModbusComponent, OpenemsComponent, ModbusSlave {

	private Config config;

	@Override
	@Reference(//
			policy = STATIC, policyOption = GREEDY, cardinality = MANDATORY, //
			target = "(&(id=${config.modbus_id})(enabled=true))")
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public MeterEastronSdm72dmImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ModbusComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MeterEastronSdm72dm.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		super.activate(context, config.id(), config.alias(), config.enabled(), config.modbusUnitId());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.config.type();
	}
	
	@Override
	protected ModbusProtocol defineModbusProtocol() {
		final var offset = 30001;
		final var invert = this.config.invert();

		return new ModbusProtocol(this, //
				new FC4ReadInputRegistersTask(30053 - offset, Priority.HIGH, //
						m(ACTIVE_POWER, new FloatDoublewordElement(30053 - offset), //
								INVERT_IF_TRUE(invert))), //
				new FC4ReadInputRegistersTask(30073 - offset, Priority.LOW, //
						// Total Import kWh
						m(invert ? ACTIVE_CONSUMPTION_ENERGY : ACTIVE_PRODUCTION_ENERGY,
								new FloatDoublewordElement(30073 - offset), SCALE_FACTOR_3), //
						// Total Export kWh
						m(invert ? ACTIVE_PRODUCTION_ENERGY : ACTIVE_CONSUMPTION_ENERGY,
								new FloatDoublewordElement(30075 - offset), SCALE_FACTOR_3)));
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(//
				OpenemsComponent.getModbusSlaveNatureTable(accessMode), //
				ElectricityMeter.getModbusSlaveNatureTable(accessMode) //
		);
	}
}
