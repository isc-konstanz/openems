package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.element.WordOrder.LSWMSW;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.concurrent.atomic.AtomicReference;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.types.MeterType;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.StringUtils;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ElementToChannelScaleFactorConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.StringWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedDoublewordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.task.FC16WriteRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
import io.openems.edge.pvinverter.hopewind.RunningState;
import io.openems.edge.pvinverter.hopewind.statemachine.Context;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;


@Designate(ocd = Config.class, factory = true)
@Component(
		name = "PV-Inverter.Hopewind",
		immediate = true,
		configurationPolicy = REQUIRE,
		property = {
				"type=PRODUCTION"
		})
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class PvInverterHopewindImpl extends AbstractOpenemsModbusComponent
		implements PvInverterHopewind, ManagedSymmetricPvInverter, ElectricityMeter,
		ModbusSlave, ModbusComponent, OpenemsComponent, EventHandler, StartStoppable, TimedataProvider {

	private static final int ACTIVE_POWER_MAX = 60000;

	private final Logger logger = LoggerFactory.getLogger(PvInverterHopewindImpl.class);

	private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);

	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

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

	int heart_beat_index = 0;

	public PvInverterHopewindImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
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
		this._setMaxApparentPower(ACTIVE_POWER_MAX);
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
			this.handleStateMachine();
			this.calculateMeterChannels();




			this.calculateProductionEnergy.update(this.getActivePower().get());
			break;
		}
	}


	private void calculateMeterChannels(){
		Integer V1 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.VOLTAGE_L1).value());
		Integer V2 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.VOLTAGE_L2).value());
		Integer V3 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.VOLTAGE_L3).value());

		Integer I1 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.CURRENT_L1).value());
		Integer I2 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.CURRENT_L2).value());
		Integer I3 = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(ElectricityMeter.ChannelId.CURRENT_L3).value());

		Integer power_factor = TypeUtils.getAsType(OpenemsType.INTEGER, this.channel(PvInverterHopewind.ChannelId.POWER_FACTOR).value());

		if (V1 != null && V2 != null && V3 != null) {
			this.channel(ElectricityMeter.ChannelId.VOLTAGE).setNextValue((V1 + V2 + V3) / 3);
		}
		if (I1 != null && I2 != null && I3 != null) {
			this.channel(ElectricityMeter.ChannelId.CURRENT).setNextValue(I1 + I2 + I3);
		}
		if (V1 != null &&  I1 != null && power_factor != null) {
			this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L1).setNextValue((V1/1000 * I1/1000 * power_factor/100));
		}
		if (V2 != null &&  I2 != null && power_factor != null) {
			this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L2).setNextValue((V2/1000 * I2/1000 * power_factor/100));
		}
		if (V3 != null &&  I3 != null && power_factor != null) {
			this.channel(ElectricityMeter.ChannelId.ACTIVE_POWER_L3).setNextValue((V3/1000 * I3/1000 * power_factor/100));
		}

		ActivePowerLimitState active_state = this.channel(PvInverterHopewind.ChannelId.ACTIVE_REGULATION_MODE).value().asEnum();
		
		if (active_state != null) {
			switch (active_state) {
			case ActivePowerLimitState.UNDEFINED:
				this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(null);
				break;
			case ActivePowerLimitState.DISABLED:
				this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(ACTIVE_POWER_MAX);
				break;
			case ActivePowerLimitState.ACTUAL:
				Integer actual_power = TypeUtils.getAsType(
						OpenemsType.INTEGER, 
						this.channel(PvInverterHopewind.ChannelId.ACTIVE_POWER_REGULATION).value());
				if (actual_power != null) {
					this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(actual_power);
				} else {
					this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(null);
				}
				break;
			case ActivePowerLimitState.PROPORTIONAL:
				Float percent_power = TypeUtils.getAsType(
						OpenemsType.FLOAT, 
						this.channel(PvInverterHopewind.ChannelId.ACTIVE_PERCENT_REGULATION).value());
				if (percent_power != null) {
					int limited_power = Math.round(ACTIVE_POWER_MAX * percent_power / 100f);
					this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(limited_power);
				} else {
					this.channel(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT).setNextValue(null);
				}
				break;
			}
		}
	}

	protected static final ElementToChannelConverter STRING_CONVERTER = new ElementToChannelConverter(v -> {
		if (v == null) {
				return null;
		}
		String value = TypeUtils.getAsType(OpenemsType.STRING, v);
		var result = new StringBuilder();
		for (var i = 0; i <= value.length() - 2; i += 2) {
				result.append(StringUtils.reverse(value.substring(i, i + 2)));
		}
		return result.toString();
	});

	protected static final ElementToChannelConverter VOLTAGE_CONVERTER = new ElementToChannelConverter(v -> {
		if (v == null) {
				return null;
		}
		int value = TypeUtils.getAsType(OpenemsType.INTEGER, v);
		return (int) Math.round(value * 100 / 1.732);
	});

	protected static final ElementToChannelConverter SCALE_FACTOR_MINUS_4 = new ElementToChannelScaleFactorConverter(-4);

	private void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		if (this.startStopTarget.get() != StartStop.STOP && 
				this.config.startStop() != StartStopConfig.STOP) {
			try {
				if (this.heart_beat_index++ >= 30) {
					System.out.println("Setting HeartBeat");
					this.heart_beat_index = 0;
					this.setHeartBeat();
				}				
			} catch (IllegalArgumentException | OpenemsNamedException e) {
				this.logError(this.logger, "Setting HeartBeat failed: " + e.getMessage());
				e.printStackTrace();
			}
		}

		// Prepare the Context
		var context = new Context(this, this.config);

		// Call the StateMachine
		try {
			this.stateMachine.run(context);
			this._setRunFailed(false);

		} catch (OpenemsNamedException e) {
			this._setRunFailed(true);
			this.logError(this.logger, "StateMachine failed: " + e.getMessage());
		}
	}
	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
//				new FC3ReadRegistersTask(1080, Priority.HIGH,
//						m(PvInverterHopewind.ChannelId.REG_1080,
//								new UnsignedWordElement(1080)),
//						m(PvInverterHopewind.ChannelId.REG_1081,
//								new UnsignedWordElement(1081)),
//						m(PvInverterHopewind.ChannelId.REG_1082,
//								new UnsignedWordElement(1082))),

//				new FC3ReadRegistersTask(30000, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_30000,
//								new UnsignedWordElement(30000)),
//						m(PvInverterHopewind.ChannelId.REG_30001,
//								new UnsignedWordElement(30001)),
//						m(PvInverterHopewind.ChannelId.TIME,
//								new UnsignedDoublewordElement(30002).wordOrder(LSWMSW), SCALE_FACTOR_1)),

//				new FC3ReadRegistersTask(30021, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_30021,
//								new UnsignedWordElement(30021)),
//						m(PvInverterHopewind.ChannelId.REG_30022,
//								new UnsignedWordElement(30022)),
//						m(PvInverterHopewind.ChannelId.REG_30023,
//								new UnsignedWordElement(30023))),
				
//				new FC3ReadRegistersTask(30026, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_30026,
//								new UnsignedWordElement(30026)),
//						m(PvInverterHopewind.ChannelId.REG_30027,
//								new UnsignedWordElement(30027)),
//						m(PvInverterHopewind.ChannelId.REG_30028,
//								new UnsignedWordElement(30028)),
//						m(PvInverterHopewind.ChannelId.REG_30029,
//								new UnsignedWordElement(30029)),
//						m(PvInverterHopewind.ChannelId.REG_30030,
//								new UnsignedWordElement(30030)),
//						m(PvInverterHopewind.ChannelId.REG_30031,
//								new UnsignedWordElement(30031)),
//						m(PvInverterHopewind.ChannelId.REG_30032,
//								new UnsignedWordElement(30032)),
//						m(PvInverterHopewind.ChannelId.REG_30033,
//								new UnsignedWordElement(30033)),
//						m(PvInverterHopewind.ChannelId.REG_30034,
//								new UnsignedWordElement(30034)),
//						m(PvInverterHopewind.ChannelId.REG_30035,
//								new UnsignedWordElement(30035)),
//						m(PvInverterHopewind.ChannelId.REG_30036,
//								new UnsignedWordElement(30036))),
				
//				new FC3ReadRegistersTask(30056, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_30056,
//								new UnsignedWordElement(30056)),
//						m(PvInverterHopewind.ChannelId.REG_30057,
//								new UnsignedWordElement(30057)),
//						m(PvInverterHopewind.ChannelId.REG_30058,
//								new UnsignedWordElement(30058)),
//						m(PvInverterHopewind.ChannelId.REG_30059,
//								new UnsignedWordElement(30059)),
//						m(PvInverterHopewind.ChannelId.REG_30060,
//								new UnsignedWordElement(30060)),
//						m(PvInverterHopewind.ChannelId.REG_30061,
//								new UnsignedWordElement(30061)),
//						m(PvInverterHopewind.ChannelId.REG_30062,
//								new UnsignedWordElement(30062)),
//						m(PvInverterHopewind.ChannelId.REG_30063,
//								new UnsignedWordElement(30063)),
//						m(PvInverterHopewind.ChannelId.REG_30064,
//								new UnsignedWordElement(30064)),
//						m(PvInverterHopewind.ChannelId.REG_30065,
//								new UnsignedWordElement(30065)),
//						m(PvInverterHopewind.ChannelId.REG_30066,
//								new UnsignedWordElement(30066)),
//						m(PvInverterHopewind.ChannelId.REG_30067,
//								new UnsignedWordElement(30067)),
//						m(PvInverterHopewind.ChannelId.REG_30068,
//								new UnsignedWordElement(30068)),
//						m(PvInverterHopewind.ChannelId.REG_30069,
//								new UnsignedWordElement(30069)),
//						m(PvInverterHopewind.ChannelId.REG_30070,
//								new UnsignedWordElement(30070)),
//						m(PvInverterHopewind.ChannelId.REG_30071,
//								new UnsignedWordElement(30071)),
//						m(PvInverterHopewind.ChannelId.REG_30072,
//								new UnsignedWordElement(30072)),
//						m(PvInverterHopewind.ChannelId.REG_30073,
//								new UnsignedWordElement(30073))),

//				new FC3ReadRegistersTask(30079, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_30079,
//								new UnsignedWordElement(30079)),
//						m(PvInverterHopewind.ChannelId.REG_30080,
//								new UnsignedWordElement(30080)),
//						m(PvInverterHopewind.ChannelId.REG_30081,
//								new UnsignedWordElement(30081)),
//						m(PvInverterHopewind.ChannelId.REG_30082,
//								new UnsignedWordElement(30082)),
//						m(PvInverterHopewind.ChannelId.REG_30083,
//								new UnsignedWordElement(30083)),
//						m(PvInverterHopewind.ChannelId.REG_30084,
//								new UnsignedWordElement(30084)),
//						m(PvInverterHopewind.ChannelId.REG_30085,
//								new UnsignedWordElement(30085)),
//						m(PvInverterHopewind.ChannelId.REG_30086,
//								new UnsignedWordElement(30086)),
//						m(PvInverterHopewind.ChannelId.REG_30087,
//								new UnsignedWordElement(30087)),
//						m(PvInverterHopewind.ChannelId.REG_30088,
//								new UnsignedWordElement(30088)),
//						m(PvInverterHopewind.ChannelId.REG_30089,
//								new UnsignedWordElement(30089)),
//						m(PvInverterHopewind.ChannelId.REG_30090,
//								new UnsignedWordElement(30090)),
//						m(PvInverterHopewind.ChannelId.REG_30091,
//								new UnsignedWordElement(30091)),
//						m(PvInverterHopewind.ChannelId.REG_30092,
//								new UnsignedWordElement(30092)),
//						m(PvInverterHopewind.ChannelId.REG_30093,
//								new UnsignedWordElement(30093)),
//						m(PvInverterHopewind.ChannelId.REG_30094,
//								new UnsignedWordElement(30094)),
//						m(PvInverterHopewind.ChannelId.REG_30095,
//								new UnsignedWordElement(30095)),
//						m(PvInverterHopewind.ChannelId.REG_30096,
//								new UnsignedWordElement(30096)),
//						m(PvInverterHopewind.ChannelId.REG_30097,
//								new UnsignedWordElement(30097)),
//						m(PvInverterHopewind.ChannelId.REG_30098,
//								new UnsignedWordElement(30098)),
//						m(PvInverterHopewind.ChannelId.REG_30099,
//								new UnsignedWordElement(30099)),
//						m(PvInverterHopewind.ChannelId.REG_30100,
//								new UnsignedWordElement(30100)),
//						m(PvInverterHopewind.ChannelId.REG_30101,
//								new UnsignedWordElement(30101)),
//						m(PvInverterHopewind.ChannelId.REG_30102,
//								new UnsignedWordElement(30102)),
//						m(PvInverterHopewind.ChannelId.REG_30103,
//								new UnsignedWordElement(30103)),
//						m(PvInverterHopewind.ChannelId.REG_30104,
//								new UnsignedWordElement(30104)),
//						m(PvInverterHopewind.ChannelId.REG_30105,
//								new UnsignedWordElement(30105)),
//						m(PvInverterHopewind.ChannelId.REG_30106,
//								new UnsignedWordElement(30106)),
//						m(PvInverterHopewind.ChannelId.REG_30107,
//								new UnsignedWordElement(30107)),
//						m(PvInverterHopewind.ChannelId.REG_30108,
//								new UnsignedWordElement(30108)),
//						m(PvInverterHopewind.ChannelId.REG_30109,
//								new UnsignedWordElement(30109)),
//						m(PvInverterHopewind.ChannelId.REG_30110,
//								new UnsignedWordElement(30110)),
//						m(PvInverterHopewind.ChannelId.REG_30111,
//								new UnsignedWordElement(30111)),
//						m(PvInverterHopewind.ChannelId.REG_30112,
//								new UnsignedWordElement(30112)),
//						m(PvInverterHopewind.ChannelId.REG_30113,
//								new UnsignedWordElement(30113)),
//						m(PvInverterHopewind.ChannelId.REG_30114,
//								new UnsignedWordElement(30114)),
//						m(PvInverterHopewind.ChannelId.REG_30115,
//								new UnsignedWordElement(30115))),

				new FC3ReadRegistersTask(30117, Priority.LOW,
						m(PvInverterHopewind.ChannelId.DAILY_GENERATION,
								new UnsignedDoublewordElement(30117).wordOrder(LSWMSW), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.CUM_GENERATION,
								new UnsignedDoublewordElement(30119).wordOrder(LSWMSW), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.REG_30121,
								new UnsignedWordElement(30121)),
						m(PvInverterHopewind.ChannelId.REG_30122,
								new UnsignedWordElement(30122)),
						m(PvInverterHopewind.ChannelId.REG_30123,
								new UnsignedWordElement(30123))),

//				new FC3ReadRegistersTask(31112, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_31112,
//								new UnsignedWordElement(31112)),
//						m(PvInverterHopewind.ChannelId.REG_31113,
//								new UnsignedWordElement(31113)),
//						m(PvInverterHopewind.ChannelId.REG_31114,
//								new UnsignedWordElement(31114)),
//						m(PvInverterHopewind.ChannelId.REG_31115,
//								new UnsignedWordElement(31115))),

//				new FC3ReadRegistersTask(34000, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_34000,
//								new UnsignedWordElement(34000)),
//						m(PvInverterHopewind.ChannelId.REG_34001,
//								new UnsignedWordElement(34001)),
//						m(PvInverterHopewind.ChannelId.REG_34002,
//								new UnsignedWordElement(34002)),
//						m(PvInverterHopewind.ChannelId.REG_34003,
//								new UnsignedWordElement(34003))),

//				new FC3ReadRegistersTask(34005, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_34005,
//								new UnsignedWordElement(34005))),
			  
				new FC3ReadRegistersTask(40000, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.REG_40000,
								new UnsignedWordElement(40000)),
						m(PvInverterHopewind.ChannelId.REG_40001,
								new UnsignedWordElement(40001)),
						m(PvInverterHopewind.ChannelId.REACTIVE_REGULATION_MODE,
								new UnsignedWordElement(40002)),
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_FACTOR_REGULATION,
								new UnsignedWordElement(40003), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION,
								new UnsignedWordElement(40004), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.REACTIVE_PERCENT_REGULATION,
								new UnsignedWordElement(40005), SCALE_FACTOR_2)),

				new FC3ReadRegistersTask(40011, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.ACTIVE_REGULATION_MODE,
								new UnsignedWordElement(40011)),
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_REGULATION,
								new UnsignedWordElement(40012), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.ACTIVE_PERCENT_REGULATION,
								new UnsignedWordElement(40013), SCALE_FACTOR_MINUS_2)),

				new FC3ReadRegistersTask(40200, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.STARTUP,
								new UnsignedWordElement(40200)),
						m(PvInverterHopewind.ChannelId.SHUTDOWN,
								new UnsignedWordElement(40201)),
						m(PvInverterHopewind.ChannelId.RESET,
								new UnsignedWordElement(40202))),

				new FC3ReadRegistersTask(40500, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.MPPT_1_VOLTAGE,
								new UnsignedWordElement(40500), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.MPPT_2_VOLTAGE,
								new UnsignedWordElement(40501), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.MPPT_3_VOLTAGE,
								new UnsignedWordElement(40502), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.MPPT_4_VOLTAGE,
								new UnsignedWordElement(40503), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.REG_40504,
								new UnsignedWordElement(40504)),
						m(PvInverterHopewind.ChannelId.REG_40505,
								new UnsignedWordElement(40505)),
						m(PvInverterHopewind.ChannelId.REG_40506,
								new UnsignedWordElement(40506)),
						m(PvInverterHopewind.ChannelId.REG_40507,
								new UnsignedWordElement(40507)),
						m(PvInverterHopewind.ChannelId.STRING_1_CURRENT,
								new UnsignedWordElement(40508), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_2_CURRENT,
								new UnsignedWordElement(40509), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_3_CURRENT,
								new UnsignedWordElement(40510), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_4_CURRENT,
								new UnsignedWordElement(40511), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_5_CURRENT,
								new UnsignedWordElement(40512), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_6_CURRENT,
								new UnsignedWordElement(40513), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_7_CURRENT,
								new UnsignedWordElement(40514), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_8_CURRENT,
								new UnsignedWordElement(40515), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_9_CURRENT,
								new UnsignedWordElement(40516), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_10_CURRENT,
								new UnsignedWordElement(40517), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_11_CURRENT,
								new UnsignedWordElement(40518), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_12_CURRENT,
								new UnsignedWordElement(40519), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_13_CURRENT,
								new UnsignedWordElement(40520), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_14_CURRENT,
								new UnsignedWordElement(40521), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_15_CURRENT,
								new UnsignedWordElement(40522), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.STRING_16_CURRENT,
								new UnsignedWordElement(40523), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.MPPT_1_POWER,
								new UnsignedWordElement(40524), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.MPPT_2_POWER,
								new UnsignedWordElement(40525), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.MPPT_3_POWER,
								new UnsignedWordElement(40526), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.MPPT_4_POWER,
								new UnsignedWordElement(40527), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.REG_40528,
								new UnsignedWordElement(40528)),
						m(PvInverterHopewind.ChannelId.REG_40529,
								new UnsignedWordElement(40529)),
						m(PvInverterHopewind.ChannelId.REG_40530,
								new UnsignedWordElement(40530)),
						m(PvInverterHopewind.ChannelId.REG_40531,
								new UnsignedWordElement(40531)),
						m(ElectricityMeter.ChannelId.VOLTAGE_L1,
								new UnsignedWordElement(40532), VOLTAGE_CONVERTER),
						m(ElectricityMeter.ChannelId.VOLTAGE_L2,
								new UnsignedWordElement(40533), VOLTAGE_CONVERTER),
						m(ElectricityMeter.ChannelId.VOLTAGE_L3,
								new UnsignedWordElement(40534), VOLTAGE_CONVERTER),
						m(ElectricityMeter.ChannelId.CURRENT_L1,
								new UnsignedWordElement(40535), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.CURRENT_L2,
								new UnsignedWordElement(40536), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.CURRENT_L3,
								new UnsignedWordElement(40537), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.FREQUENCY,
								new UnsignedWordElement(40538), SCALE_FACTOR_1),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER,
								new UnsignedWordElement(40539), SCALE_FACTOR_1),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER,
								new UnsignedWordElement(40540), SCALE_FACTOR_MINUS_3),
						m(PvInverterHopewind.ChannelId.APPERENT_POWER,
								new UnsignedWordElement(40541), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.POWER_FACTOR,
								new UnsignedWordElement(40542), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.REG_40543,
								new UnsignedWordElement(40543)),
						m(PvInverterHopewind.ChannelId.TEMPERATURE,
								new UnsignedWordElement(40544), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.INSULATION_RESISTANCE,
								new UnsignedWordElement(40545), SCALE_FACTOR_2),
						m(PvInverterHopewind.ChannelId.RUNNING_STATE,
								new UnsignedWordElement(40546)),
						m(PvInverterHopewind.ChannelId.INVERTER_STATE,
								new UnsignedWordElement(40547)),
						m(PvInverterHopewind.ChannelId.TODAY_YIELD,
								new UnsignedDoublewordElement(40548).wordOrder(LSWMSW), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.REG_40550,
								new UnsignedWordElement(40550)),
						m(PvInverterHopewind.ChannelId.REG_40551,
								new UnsignedWordElement(40551)),
						m(PvInverterHopewind.ChannelId.CO2_REDUCTION,
								new UnsignedDoublewordElement(40552).wordOrder(LSWMSW), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DAILY_RUNTIME,
								new UnsignedWordElement(40554), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.TOTAL_RUNTIME,
								new UnsignedDoublewordElement(40555).wordOrder(LSWMSW), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.REG_40557,
								new UnsignedWordElement(40557)),
						m(PvInverterHopewind.ChannelId.REG_40558,
								new UnsignedWordElement(40558)),
						m(PvInverterHopewind.ChannelId.REG_40559,
								new UnsignedWordElement(40559)),
						m(PvInverterHopewind.ChannelId.REG_40560,
								new UnsignedWordElement(40560)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_1,
								new UnsignedWordElement(40561)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_2,
								new UnsignedWordElement(40562)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_3,
								new UnsignedWordElement(40563)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_4,
								new UnsignedWordElement(40564)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_5,
								new UnsignedWordElement(40565)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_6,
								new UnsignedWordElement(40566)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_7,
								new UnsignedWordElement(40567)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_8,
								new UnsignedWordElement(40568)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_1,
								new UnsignedWordElement(40569)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_2,
								new UnsignedWordElement(40570)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_3,
								new UnsignedWordElement(40571)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_4,
								new UnsignedWordElement(40572)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_5,
								new UnsignedWordElement(40573)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_6,
								new UnsignedWordElement(40574)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_7,
								new UnsignedWordElement(40575)),
						m(PvInverterHopewind.ChannelId.ALARM_WORD_8,
								new UnsignedWordElement(40576)),
						m(PvInverterHopewind.ChannelId.FAULT_WORD_9,
								new UnsignedWordElement(40577)),
						m(PvInverterHopewind.ChannelId.FAULT_CODE,
								new UnsignedWordElement(40578)),
						m(PvInverterHopewind.ChannelId.ALARM_CODE,
								new UnsignedWordElement(40579)),
						m(PvInverterHopewind.ChannelId.ARCING_WORD_1,
								new UnsignedWordElement(40580)),
						m(PvInverterHopewind.ChannelId.ARCING_WORD_2,
								new UnsignedWordElement(40581)),
						m(PvInverterHopewind.ChannelId.REG_40582,
								new UnsignedWordElement(40582)),
						m(PvInverterHopewind.ChannelId.REG_40583,
								new UnsignedWordElement(40583)),
						m(PvInverterHopewind.ChannelId.REG_40584,
								new UnsignedWordElement(40584)),
						m(PvInverterHopewind.ChannelId.REG_40585,
								new UnsignedWordElement(40585)),
						m(PvInverterHopewind.ChannelId.REG_40586,
								new UnsignedWordElement(40586)),
						m(PvInverterHopewind.ChannelId.REG_40587,
								new UnsignedWordElement(40587)),
						m(PvInverterHopewind.ChannelId.REG_40588,
								new UnsignedWordElement(40588)),
						m(PvInverterHopewind.ChannelId.REG_40589,
								new UnsignedWordElement(40589)),
						m(PvInverterHopewind.ChannelId.REG_40590,
								new UnsignedWordElement(40590)),
						m(PvInverterHopewind.ChannelId.REG_40591,
								new UnsignedWordElement(40591)),
						m(PvInverterHopewind.ChannelId.REG_40592,
								new UnsignedWordElement(40592)),
						m(PvInverterHopewind.ChannelId.REG_40593,
								new UnsignedWordElement(40593)),
						m(PvInverterHopewind.ChannelId.DC_POWER,
								new UnsignedWordElement(40594), SCALE_FACTOR_1)),

				new FC3ReadRegistersTask(40601, Priority.LOW,
						m(PvInverterHopewind.ChannelId.UNIT_ID,
							new StringWordElement(40601, 27), STRING_CONVERTER),
						m(PvInverterHopewind.ChannelId.REG_40628,
							new UnsignedWordElement(40628)),
						m(PvInverterHopewind.ChannelId.REG_40629,
							new UnsignedWordElement(40629)),
						m(PvInverterHopewind.ChannelId.REG_40630,
							new UnsignedWordElement(40630)),
						m(PvInverterHopewind.ChannelId.REG_40631,
							new UnsignedWordElement(40631)),
						m(PvInverterHopewind.ChannelId.REG_40632,
							new UnsignedWordElement(40632)),
						m(PvInverterHopewind.ChannelId.REG_40633,
							new UnsignedWordElement(40633)),
						m(PvInverterHopewind.ChannelId.REG_40634,
							new UnsignedWordElement(40634)),
						m(PvInverterHopewind.ChannelId.REG_40635,
							new UnsignedWordElement(40635)),
						m(PvInverterHopewind.ChannelId.REG_40636,
							new UnsignedWordElement(40636)),
						m(PvInverterHopewind.ChannelId.REG_40637,
							new UnsignedWordElement(40637)),
						m(PvInverterHopewind.ChannelId.REG_40638,       
							new UnsignedWordElement(40638)),
						m(PvInverterHopewind.ChannelId.REG_40639,
							new UnsignedWordElement(40639)),
						m(PvInverterHopewind.ChannelId.REG_40640,
							new UnsignedWordElement(40640)),
						m(PvInverterHopewind.ChannelId.REG_40641,
							new UnsignedWordElement(40641)),
						m(PvInverterHopewind.ChannelId.REG_40642,
							new UnsignedWordElement(40642)),
						m(PvInverterHopewind.ChannelId.REG_40643,
							new UnsignedWordElement(40643)),
						m(PvInverterHopewind.ChannelId.REG_40644,
							new UnsignedWordElement(40644))),

				new FC3ReadRegistersTask(40646, Priority.LOW,
						m(PvInverterHopewind.ChannelId.RATED_POWER,
							new UnsignedWordElement(40646), SCALE_FACTOR_3),
						m(PvInverterHopewind.ChannelId.RATED_VOLTAGE,
							new UnsignedWordElement(40647))),

				new FC3ReadRegistersTask(41000, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.STRING_1_POWER,
								new UnsignedWordElement(41000), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_2_POWER,
								new UnsignedWordElement(41001), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_3_POWER,
								new UnsignedWordElement(41002), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_4_POWER,
								new UnsignedWordElement(41003), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_5_POWER,
								new UnsignedWordElement(41004), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_6_POWER,
								new UnsignedWordElement(41005), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_7_POWER,
								new UnsignedWordElement(41006), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_8_POWER,
								new UnsignedWordElement(41007), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_9_POWER,
								new UnsignedWordElement(41008), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_10_POWER,
								new UnsignedWordElement(41009), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_11_POWER,
								new UnsignedWordElement(41010), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_12_POWER,
								new UnsignedWordElement(41011), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_13_POWER,
								new UnsignedWordElement(41012), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_14_POWER,
								new UnsignedWordElement(41013), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_15_POWER,
								new UnsignedWordElement(41014), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.STRING_16_POWER,
								new UnsignedWordElement(41015), SCALE_FACTOR_1),
						new DummyRegisterElement(41016, 41019)),

				new FC16WriteRegistersTask(1083,
						m(PvInverterHopewind.ChannelId.REG_1083,
								new UnsignedWordElement(1083)),
						m(PvInverterHopewind.ChannelId.REG_1084,
								new UnsignedWordElement(1084)),
						m(PvInverterHopewind.ChannelId.REG_1085,
								new UnsignedWordElement(1085))),
				
				new FC6WriteRegisterTask(32014,
						m(PvInverterHopewind.ChannelId.HEART_BEAT,
								new UnsignedWordElement(32014))),

				new FC16WriteRegistersTask(40002,
						m(PvInverterHopewind.ChannelId.REACTIVE_REGULATION_MODE,
								new UnsignedWordElement(40002)),
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_FACTOR_REGULATION,
								new SignedWordElement(40003), SCALE_FACTOR_MINUS_2),
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION,
								new UnsignedWordElement(40004), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.REACTIVE_PERCENT_REGULATION,
								new UnsignedWordElement(40005), SCALE_FACTOR_MINUS_2)),

				new FC16WriteRegistersTask(40011,
						m(PvInverterHopewind.ChannelId.ACTIVE_REGULATION_MODE,
								new UnsignedWordElement(40011)),
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_REGULATION,
								new UnsignedWordElement(40012), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.ACTIVE_PERCENT_REGULATION,
								new UnsignedWordElement(40013), SCALE_FACTOR_MINUS_2)),
				
				new FC16WriteRegistersTask(40200,
						m(PvInverterHopewind.ChannelId.STARTUP,
								new UnsignedWordElement(40200)),
						m(PvInverterHopewind.ChannelId.SHUTDOWN,
								new UnsignedWordElement(40201)),
						m(PvInverterHopewind.ChannelId.RESET,
								new UnsignedWordElement(40202)))

		);
	}








	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
//		PvInverterHopewind.getModbusSlaveNatureTable(accessMode));
	}

	@Override
	public void setStartStop(StartStop value) {
		if (this.startStopTarget.getAndSet(value) != value) {
			// Set only if value changed
			this.stateMachine.forceNextState(State.UNDEFINED);
		}
	}

	@Override
	public StartStop getStartStopTarget() {
		return switch (this.config.startStop()) {
		case AUTO -> this.startStopTarget.get(); // read StartStop-Channel
		case START -> StartStop.START; // force START
		case STOP -> StartStop.STOP; // force STOP
		};
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
		return new StringBuilder()
				.append(stateMachine.debugLog())
				.toString();
	}

}
