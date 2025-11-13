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
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Meter.RCT.CESS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
	EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE
})
public class MeterRctCessImpl extends AbstractOpenemsModbusComponent implements MeterRctCess,
		ElectricityMeter, OpenemsComponent, ModbusComponent, ModbusSlave, EventHandler {

	private MeterType meterType = MeterType.CONSUMPTION_METERED;
	private boolean invert;

	@Reference
	private ConfigurationAdmin cm;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public MeterRctCessImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				ElectricityMeter.ChannelId.values(),
				MeterRctCess.ChannelId.values()
		);
		ElectricityMeter.calculatePhasesFromActivePower(this);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.invert = config.invert();
		this.meterType = config.type();
		if (super.activate(context, config.id(), config.alias(), config.enabled(), 1, this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic() == EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE) {
			this.calculatePhasePowerFactors();
			this.calculatePhasePowers();
		}
	}

	private void calculatePhasePowerFactors() {
		if (!this.getPowerFactor().isDefined()) {
			return;
		}
		var powerFactor = this.getPowerFactor().get();

		if (this.getCurrentHarmonicDistortionL1().isDefined()) {
			this._setPowerFactorL1(_calculatePhasePowerFactor(this.getCurrentHarmonicDistortionL1().get(), powerFactor));
		}
		if (this.getCurrentHarmonicDistortionL2().isDefined()) {
			this._setPowerFactorL2(_calculatePhasePowerFactor(this.getCurrentHarmonicDistortionL2().get(), powerFactor));
		}
		if (this.getCurrentHarmonicDistortionL3().isDefined()) {
			this._setPowerFactorL3(_calculatePhasePowerFactor(this.getCurrentHarmonicDistortionL3().get(), powerFactor));
		}
	}

	private Float _calculatePhasePowerFactor(float totalHarmonicDistortion, float totalPowerFactor) {
		return Math.min((float) (totalPowerFactor * Math.sqrt(1 + Math.pow(totalHarmonicDistortion / 100., 2))), 1f);
	}

	private void calculatePhasePowers() {
		if (this.getPowerFactorL1Channel().getNextValue().isDefined()) {
			this._calculatePhasePowers(1, this.getPowerFactorL1Channel().getNextValue().get());
		}
		if (this.getPowerFactorL2Channel().getNextValue().isDefined()) {
			this._calculatePhasePowers(2, this.getPowerFactorL2Channel().getNextValue().get());
		}
		if (this.getPowerFactorL3Channel().getNextValue().isDefined()) {
			this._calculatePhasePowers(3, this.getPowerFactorL3Channel().getNextValue().get());
		}
	}

	private void _calculatePhasePowers(int phase, float powerFactor) {
		Integer apparentPower = this._calculateApparentPhasePower(phase);
		if (apparentPower == null) {
			return;
		}
		double phi = Math.acos(powerFactor);

		int activePower = (int) (apparentPower * powerFactor);
		int reactivePower = (int) (apparentPower * Math.sin(phi));
		switch (phase) {
			case 1 -> {
				this._setActivePowerL1(activePower);
				this._setReactivePowerL1(reactivePower);
			}
			case 2 -> {
				this._setActivePowerL2(activePower);
				this._setReactivePowerL2(reactivePower);
			}
			case 3 -> {
				this._setActivePowerL3(activePower);
				this._setReactivePowerL3(reactivePower);
			}
		}
	}

	private Integer _calculateApparentPhasePower(int phase) {
		Integer voltage;
		Integer current;
		switch (phase) {
			case 1 -> {
				voltage = this.getVoltageL1().get();
				current = this.getCurrentL1().get();
			}
			case 2 -> {
				voltage = this.getVoltageL2().get();
				current = this.getCurrentL2().get();
			}
			case 3 -> {
				voltage = this.getVoltageL3().get();
				current = this.getCurrentL3().get();
			}
			default -> {
				return null;
			}
		}
		if (voltage == null || current == null) {
			return null;
		}
		return (int) (((double) voltage / 1000.0) * ((double) current / 1000.0));
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

						m(MeterRctCess.ChannelId.POWER_FACTOR, new UnsignedWordElement(0x000E),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_3)),
						new DummyRegisterElement(0x000F, 0x0012),
						m(MeterRctCess.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x0013),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(MeterRctCess.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x0014),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(MeterRctCess.ChannelId.VOLTAGE_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x0015),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(MeterRctCess.ChannelId.CURRENT_HARMONIC_DISTORTION_L1, new UnsignedWordElement(0x0016),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(MeterRctCess.ChannelId.CURRENT_HARMONIC_DISTORTION_L2, new UnsignedWordElement(0x0017),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2)),
						m(MeterRctCess.ChannelId.CURRENT_HARMONIC_DISTORTION_L3, new UnsignedWordElement(0x0018),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_2))),

				new FC3ReadRegistersTask(0x000F, Priority.LOW,
						m(ElectricityMeter.ChannelId.FREQUENCY, new UnsignedWordElement(0x000F),
							SCALE_FACTOR_1),
						m(MeterRctCess.ChannelId.VOLTAGE_RATIO, new UnsignedWordElement(0x0010)),
						m(MeterRctCess.ChannelId.CURRENT_RATIO, new UnsignedWordElement(0x0011))));

		if (!this.invert) {
			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0002, Priority.LOW,
					m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, new UnsignedDoublewordElement(0x0002),
							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio())),
					m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new UnsignedDoublewordElement(0x0004),
							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio()))));
		} else {
			modbusProtocol.addTask(new FC3ReadRegistersTask(0x0002, Priority.LOW,
					m(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, new UnsignedDoublewordElement(0x0004),
							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio())),
					m(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, new UnsignedDoublewordElement(0x0002),
							chain(SCALE_FACTOR_1, multiplyByVoltageRatio(), multiplyByCurrentRatio()))));
		}

		return modbusProtocol;
	}

	private ElementToChannelConverter multiplyByVoltageRatio() {
		return new ElementToChannelConverter(value -> {
			var intValue = TypeUtils.<Integer>getAsType(OpenemsType.INTEGER, value);
			if (intValue != null) {
				try {
					return intValue * this.getVoltageRatio().getOrError();

				} catch (InvalidValueException e) {
					// CT Value not yet available
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
					return intValue * this.getCurrentRatio().getOrError();

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