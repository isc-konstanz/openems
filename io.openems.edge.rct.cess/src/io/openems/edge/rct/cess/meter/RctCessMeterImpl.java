package io.openems.edge.rct.cess.meter;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.INVERT_IF_TRUE;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.InvalidValueException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "RCT.CESS.Meter",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({ //
	EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE, //
})
public class RctCessMeterImpl extends AbstractOpenemsModbusComponent implements RctCessMeter,
		ElectricityMeter, OpenemsComponent, ModbusComponent, ModbusSlave, EventHandler, TimedataProvider {

	private MeterType meterType = MeterType.CONSUMPTION_METERED;
	private boolean invert;

	private final CalculateEnergyFromPower calculateAcConsumptionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);
	private final CalculateEnergyFromPower calculateAcProductionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public RctCessMeterImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				RctCessMeter.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.invert = config.invert();
		this.meterType = config.type();
		if (super.activate(context, config.id(), config.alias(), config.enabled(), 1, this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
		RctCessMeter.calculatePhasePowerFactorsFromHarmonics(this);
		RctCessMeter.calculatePhasePowersFromVoltageAndCurrent(this);
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
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculateEnergy();
			break;
		}
	}

	private void calculateEnergy() {
		/*
		 * Calculate AC Energy
		 */
		var acActivePower = this.getActivePowerChannel().getNextValue().get();
		if (acActivePower == null) {
			// Not available
			this.calculateAcConsumptionEnergy.update(null);
			this.calculateAcProductionEnergy.update(null);
		} else {
			if (this.invert) {
				acActivePower *= -1;
			}
			if (acActivePower > 0) {
				// Import-from-grid
				this.calculateAcConsumptionEnergy.update(0);
				this.calculateAcProductionEnergy.update(acActivePower * -1);
			} else {
				// Export-to-Grid
				this.calculateAcConsumptionEnergy.update(acActivePower);
				this.calculateAcProductionEnergy.update(0);
			}
		}
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode)
		);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		var modbusProtocol = new ModbusProtocol(this,
				new FC3ReadRegistersTask(0x0006, Priority.HIGH,
						m(ElectricityMeter.ChannelId.VOLTAGE_L1, new UnsignedWordElement(0x0006),
								chain(SCALE_FACTOR_2, multiplyByVoltageRatio())),
						m(ElectricityMeter.ChannelId.VOLTAGE_L2, new UnsignedWordElement(0x0007),
								chain(SCALE_FACTOR_2, multiplyByVoltageRatio())),
						m(ElectricityMeter.ChannelId.VOLTAGE_L3, new UnsignedWordElement(0x0008),
								chain(SCALE_FACTOR_2, multiplyByVoltageRatio())),

						m(ElectricityMeter.ChannelId.CURRENT_L1, new UnsignedWordElement(0x0009),
								chain(SCALE_FACTOR_1, multiplyByCurrentRatio())),
						m(ElectricityMeter.ChannelId.CURRENT_L2, new UnsignedWordElement(0x000A),
								chain(SCALE_FACTOR_1, multiplyByCurrentRatio())),
						m(ElectricityMeter.ChannelId.CURRENT_L3, new UnsignedWordElement(0x000B),
								chain(SCALE_FACTOR_1, multiplyByCurrentRatio())),

						m(ElectricityMeter.ChannelId.ACTIVE_POWER, new SignedWordElement(0x000C),
								chain(INVERT_IF_TRUE(this.invert), multiplyByVoltageRatio(), multiplyByCurrentRatio())),
//						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, new SignedWordElement(0x0000),
//							chain(INVERT_IF_TRUE(this.invert))),
//						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, new SignedWordElement(0x0000),
//							chain(INVERT_IF_TRUE(this.invert))),
//						m(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, new SignedWordElement(0x0000),
//							chain(INVERT_IF_TRUE(this.invert))),

						m(ElectricityMeter.ChannelId.REACTIVE_POWER, new SignedWordElement(0x000D),
								chain(INVERT_IF_TRUE(this.invert), multiplyByVoltageRatio(), multiplyByCurrentRatio())),
//						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L1, new SignedWordElement(0x0000),
//								INVERT_IF_TRUE(this.invert)),
//						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L2, new SignedWordElement(0x0000),
//								INVERT_IF_TRUE(this.invert)),
//						m(ElectricityMeter.ChannelId.REACTIVE_POWER_L3, new SignedWordElement(0x0000),
//								INVERT_IF_TRUE(this.invert)),

						m(RctCessMeter.ChannelId.POWER_FACTOR, new UnsignedWordElement(0x000E),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_3)),
						new DummyRegisterElement(0x000F, 0x0012),
						m(RctCessMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x0013),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(RctCessMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x0014),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(RctCessMeter.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x0015),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(RctCessMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x0016),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(RctCessMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x0017),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(RctCessMeter.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x0018),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2))),

				new FC3ReadRegistersTask(0x000F, Priority.LOW,
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(0x000F),
							SCALE_FACTOR_1),
						m(RctCessMeter.ChannelId.VOLTAGE_RATIO, new UnsignedWordElement(0x0010)),
						m(RctCessMeter.ChannelId.CURRENT_RATIO, new UnsignedWordElement(0x0011))));

//		if (!this.invert) {
//			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0002, Priority.LOW,
//					m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, new UnsignedDoublewordElement(0x0002),
//							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio())),
//					m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new UnsignedDoublewordElement(0x0004),
//							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio()))));
//		} else {
//			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0002, Priority.LOW,
//					m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new UnsignedDoublewordElement(0x0004),
//							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio())),
//					m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, new UnsignedDoublewordElement(0x0002),
//							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio()))));
//		}

		return modbusProtocol;
	}

	private ElementToChannelConverter multiplyByVoltageRatio() {
		return new ElementToChannelConverter(value -> {
			var intValue = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
			if (intValue != null) {
				try {
					return intValue * this.getVoltageRatioChannel().getNextValue().getOrError();

				} catch (InvalidValueException e) {
					// PT Value not yet available
				}
			}
			return null;
		});
	}

	private ElementToChannelConverter multiplyByCurrentRatio() {
		return new ElementToChannelConverter(value -> {
			var intValue = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
			if (intValue != null) {
				try {
					return intValue * this.getCurrentRatioChannel().getNextValue().getOrError();

				} catch (InvalidValueException e) {
					// CT Value not yet available
				}
			}
			return null;
		});
	}

	private static final ElementToChannelConverter CONVERT_FLOAT = new ElementToChannelConverter(v -> {
		if (v == null) {
			return null;
		}
		if (v instanceof Number n) {
			return n.floatValue();
		}
		if (v instanceof String s) {
			return Float.valueOf(s);
		}
		throw new IllegalArgumentException(
			"Type [" + v.getClass().getName() + "] not supported by float converter");
	});

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

}