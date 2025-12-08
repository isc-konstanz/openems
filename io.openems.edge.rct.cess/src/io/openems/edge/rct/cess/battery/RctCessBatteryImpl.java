package io.openems.edge.rct.cess.battery;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.DIRECT_1_TO_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.rct.cess.battery.statemachine.StateMachine.State.UNDEFINED;

import java.util.concurrent.atomic.AtomicReference;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.battery.api.BatteryErrorAcknowledge;
import io.openems.edge.battery.protection.BatteryVoltageProtection;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.rct.cess.battery.statemachine.Context;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine;
import io.openems.edge.rct.cess.battery.statemachine.StateMachine.State;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "RCT.CESS.200.Battery",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
//		EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE,
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE
})
public class RctCessBatteryImpl extends AbstractOpenemsModbusComponent implements 
		RctCessBattery, Battery, BatteryErrorAcknowledge,
		OpenemsComponent, ModbusComponent, EventHandler, StartStoppable {

	private static final int NUMBER_OF_RACK_UNITS = 5;
	private static final int CAPACITY_PER_RACK_UNIT = 46592;

	// FIXME: Validate these boundaries with RCT BMS documentation
	private static final int MIN_ALLOWED_VOLTAGE_PER_RACK_UNIT = 133;
	private static final int MAX_ALLOWED_VOLTAGE_PER_RACK_UNIT = 192;

	private final Logger log = LoggerFactory.getLogger(RctCessBatteryImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	private Config config = null;

//	private BatteryProtection batteryProtection = null;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public RctCessBatteryImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				Battery.ChannelId.values(),
				BatteryErrorAcknowledge.ChannelId.values(),
				BatteryVoltageProtection.ChannelId.values(),
				RctCessBattery.ChannelId.values()
		);

		var maxVoltage = NUMBER_OF_RACK_UNITS * MAX_ALLOWED_VOLTAGE_PER_RACK_UNIT;
		this._setChargeMaxVoltage(maxVoltage);

		var minVoltage = NUMBER_OF_RACK_UNITS * MIN_ALLOWED_VOLTAGE_PER_RACK_UNIT;
		this._setDischargeMinVoltage(minVoltage);

		var capacity = NUMBER_OF_RACK_UNITS * CAPACITY_PER_RACK_UNIT;
		this._setCapacity(capacity);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		if (super.activate(context, config.id(), config.alias(), config.enabled(), 1, this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}
		RctCessBattery.calculatePowerFromVoltageAndCurrent(this);

//		this.batteryProtection = BatteryProtection.create(this)
//				.applyBatteryProtectionDefinition(new BatteryProtectionDefinition(), this.componentManager)
//				.build();
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
//			case EdgeEventConstants.TOPIC_CYCLE_BEFORE_PROCESS_IMAGE:
//				this.batteryProtection.apply();
//				break;
			case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
				this.handleStateMachine();
				break;
		}
	}

	@Override
	public void executeBatteryErrorAcknowledge() {
		try {
			this._setTimeoutStartBattery(false);
			this._setTimeoutStopBattery(false);

			this.stateMachine.forceNextState(UNDEFINED);
		} catch (Exception e) {
			this.logError(this.log, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// Prepare Context
		var context = new Context(this, this.config, this.componentManager.getClock());

		// Call the StateMachine
		try {
			this.stateMachine.run(context);
			this._setRunFailed(false);

		} catch (OpenemsNamedException e) {
			this._setRunFailed(true);
			this.logError(this.log, "StateMachine failed: " + e.getMessage());
		}
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
			case AUTO -> this.startStopTarget.get(); // Read StartStop-Channel
			case START -> StartStop.START; // Force START
			case STOP -> StartStop.STOP; // Force STOP
		};
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC3ReadRegistersTask(0x0000, Priority.HIGH,
						m(RctCessBattery.ChannelId.RUN_STATE, new UnsignedWordElement(0x0000)),
						m(RctCessBattery.ChannelId.PRE_CHARGE_STATE, new UnsignedWordElement(0x0001)),

						m(new BitsWordElement(0x0002, this)
								.bit(0, RctCessBattery.ChannelId.CONTACTOR_POSITIVE_STATE)
								.bit(1, RctCessBattery.ChannelId.PRE_CHARGE_CONNTACTOR_STATE)
								.bit(2, RctCessBattery.ChannelId.CONTACTOR_NEGATIVE_STATE)
								.bit(3, RctCessBattery.ChannelId.DISCONNECTOR_STATE)),
						m(new BitsWordElement(0x0003, this)
								.bit(0, RctCessBattery.ChannelId.BMU_HARDWARE_FAULT)
								.bit(1, RctCessBattery.ChannelId.BCU_HARDWARE_FAULT)
								.bit(2, RctCessBattery.ChannelId.FUSE_PROTECTOR_FAULT)
								.bit(3, RctCessBattery.ChannelId.CONTACTOR_ADHESION_FAULT)
								.bit(4, RctCessBattery.ChannelId.BMU_COMMUNICATION_FAULT)
								.bit(5, RctCessBattery.ChannelId.BAU_COMMUNICATION_FAULT)
								.bit(6, RctCessBattery.ChannelId.CURRENT_SENSOR_FAULT)
								.bit(7, RctCessBattery.ChannelId.INSULATION_MONITOR_FAULT)
								.bit(8, RctCessBattery.ChannelId.DISCONNECTOR_ABNORMAL_FAULT))),

				new FC3ReadRegistersTask(0x0004, Priority.LOW,
						m(new BitsWordElement(0x0004, this)
								.bit(0, RctCessBattery.ChannelId.TOTAL_VOLTAGE_HIGH_WARNING)
								.bit(1, RctCessBattery.ChannelId.TOTAL_VOLTAGE_LOW_WARNING)
								.bit(2, RctCessBattery.ChannelId.CELL_VOLTAGE_HIGH_WARNING)
								.bit(3, RctCessBattery.ChannelId.CELL_VOLTAGE_LOW_WARNING)
								.bit(4, RctCessBattery.ChannelId.CELL_CURRENT_DISCHARGE_HIGH_WARNING)
								.bit(5, RctCessBattery.ChannelId.CELL_CURRENT_CHARGE_HIGH_WARNING)
								.bit(6, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_HIGH_WARNING)
								.bit(7, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_LOW_WARNING)
								.bit(8, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_HIGH_WARNING)
								.bit(9, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_LOW_WARNING)
								.bit(10, RctCessBattery.ChannelId.INSULATION_LOW_WARNING)
								.bit(11, RctCessBattery.ChannelId.INSULATION_HIGH_WARNING)
								.bit(12, RctCessBattery.ChannelId.SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_WARNING)
								.bit(13, RctCessBattery.ChannelId.CELL_VOLTAGE_DIFFERENCE_HIGH_WARNING)
								.bit(14, RctCessBattery.ChannelId.CELL_TEMPERATURE_DIFFERENCE_HIGH_WARNING)
								.bit(15, RctCessBattery.ChannelId.SOC_LOW_WARNING)),
						m(new BitsWordElement(0x0005, this)
								.bit(0, RctCessBattery.ChannelId.TOTAL_VOLTAGE_HIGH_ALARM)
								.bit(1, RctCessBattery.ChannelId.TOTAL_VOLTAGE_LOW_ALARM)
								.bit(2, RctCessBattery.ChannelId.CELL_VOLTAGE_HIGH_ALARM)
								.bit(3, RctCessBattery.ChannelId.CELL_VOLTAGE_LOW_ALARM)
								.bit(4, RctCessBattery.ChannelId.CELL_CURRENT_DISCHARGE_HIGH_ALARM)
								.bit(5, RctCessBattery.ChannelId.CELL_CURRENT_CHARGE_HIGH_ALARM)
								.bit(6, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_HIGH_ALARM)
								.bit(7, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_LOW_ALARM)
								.bit(8, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_HIGH_ALARM)
								.bit(9, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_LOW_ALARM)
								.bit(10, RctCessBattery.ChannelId.INSULATION_LOW_ALARM)
								.bit(11, RctCessBattery.ChannelId.INSULATION_HIGH_ALARM)
								.bit(12, RctCessBattery.ChannelId.SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_ALARM)
								.bit(13, RctCessBattery.ChannelId.CELL_VOLTAGE_DIFFERENCE_HIGH_ALARM)
								.bit(14, RctCessBattery.ChannelId.CELL_TEMPERATURE_DIFFERENCE_HIGH_ALARM)
								.bit(15, RctCessBattery.ChannelId.SOC_LOW_ALARM)),
						m(new BitsWordElement(0x0006, this)
								.bit(0, RctCessBattery.ChannelId.TOTAL_VOLTAGE_HIGH_CRITICAL)
								.bit(1, RctCessBattery.ChannelId.TOTAL_VOLTAGE_LOW_CRITICAL)
								.bit(2, RctCessBattery.ChannelId.CELL_VOLTAGE_HIGH_CRITICAL)
								.bit(3, RctCessBattery.ChannelId.CELL_VOLTAGE_LOW_CRITICAL)
								.bit(4, RctCessBattery.ChannelId.CELL_CURRENT_DISCHARGE_HIGH_CRITICAL)
								.bit(5, RctCessBattery.ChannelId.CELL_CURRENT_CHARGE_HIGH_CRITICAL)
								.bit(6, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_HIGH_CRITICAL)
								.bit(7, RctCessBattery.ChannelId.CELL_TEMPERATURE_DISCHARGE_LOW_CRITICAL)
								.bit(8, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_HIGH_CRITICAL)
								.bit(9, RctCessBattery.ChannelId.CELL_TEMPERATURE_CHARGE_LOW_CRITICAL)
								.bit(10, RctCessBattery.ChannelId.INSULATION_LOW_CRITICAL)
								.bit(11, RctCessBattery.ChannelId.INSULATION_HIGH_CRITICAL)
								.bit(12, RctCessBattery.ChannelId.SWITCH_BOX_CONNECTOR_TEMPERATURE_HIGH_CRITICAL)
								.bit(13, RctCessBattery.ChannelId.CELL_VOLTAGE_DIFFERENCE_HIGH_CRITICAL)
								.bit(14, RctCessBattery.ChannelId.CELL_TEMPERATURE_DIFFERENCE_HIGH_CRITICAL)
								.bit(15, RctCessBattery.ChannelId.SOC_LOW_CRITICAL))),

				new FC3ReadRegistersTask(0x0008, Priority.HIGH,
						m(Battery.ChannelId.VOLTAGE,
								new UnsignedWordElement(0x0008), SCALE_FACTOR_MINUS_1),
						m(Battery.ChannelId.CURRENT,
								new SignedWordElement(0x0009), SCALE_FACTOR_MINUS_1),
						m(RctCessBattery.ChannelId.RACK_CHARGE_STATE,
								new SignedWordElement(0x000A)),
						m(Battery.ChannelId.SOC,
								new UnsignedWordElement(0x000B), SCALE_FACTOR_MINUS_1),
						m(Battery.ChannelId.SOH,
								new UnsignedWordElement(0x000C), SCALE_FACTOR_MINUS_1),
						m(RctCessBattery.ChannelId.INSULATION_VALUE,
								new UnsignedWordElement(0x000D), SCALE_FACTOR_3),
						m(RctCessBattery.ChannelId.INSULATION_POSITIVE_VALUE,
								new UnsignedWordElement(0x000E), SCALE_FACTOR_3),
						m(RctCessBattery.ChannelId.INSULATION_NEGATIVE_VALUE,
								new UnsignedWordElement(0x000F), SCALE_FACTOR_3),
						m(Battery.ChannelId.CHARGE_MAX_CURRENT,
								new UnsignedWordElement(0x0010), SCALE_FACTOR_MINUS_1),
						m(Battery.ChannelId.DISCHARGE_MAX_CURRENT,
								new UnsignedWordElement(0x0011), SCALE_FACTOR_MINUS_1),
//						m(BatteryVoltageProtection.ChannelId.BVP_CHARGE_BMS,
//								new UnsignedWordElement(0x0010), SCALE_FACTOR_MINUS_1),
//						m(BatteryVoltageProtection.ChannelId.BVP_DISCHARGE_BMS,
//								new UnsignedWordElement(0x0011), SCALE_FACTOR_MINUS_1),
//						m(BatteryProtection.ChannelId.BP_CHARGE_BMS,
//								new UnsignedWordElement(0x0010), SCALE_FACTOR_MINUS_1),
//						m(BatteryProtection.ChannelId.BP_DISCHARGE_BMS,
//								new UnsignedWordElement(0x0011), SCALE_FACTOR_MINUS_1),
						m(RctCessBattery.ChannelId.MAX_CELL_VOLTAGE_INDEX,
								new SignedWordElement(0x0012), DIRECT_1_TO_1),
						m(Battery.ChannelId.MAX_CELL_VOLTAGE,
								new UnsignedWordElement(0x0013), DIRECT_1_TO_1),
						m(RctCessBattery.ChannelId.MIN_CELL_VOLTAGE_INDEX,
								new SignedWordElement(0x0014), DIRECT_1_TO_1),
						m(Battery.ChannelId.MIN_CELL_VOLTAGE,
								new UnsignedWordElement(0x0015), DIRECT_1_TO_1),
						m(RctCessBattery.ChannelId.MAX_CELL_TEMPERATURE_INDEX,
								new SignedWordElement(0x0016), DIRECT_1_TO_1),
						m(Battery.ChannelId.MAX_CELL_TEMPERATURE,
								new UnsignedWordElement(0x0017), SCALE_FACTOR_MINUS_1),
						m(RctCessBattery.ChannelId.MIN_CELL_TEMPERATURE_INDEX,
								new SignedWordElement(0x0018), DIRECT_1_TO_1),
						m(Battery.ChannelId.MIN_CELL_TEMPERATURE,
								new UnsignedWordElement(0x0019), SCALE_FACTOR_MINUS_1)),

				new FC3ReadRegistersTask(0x001A, Priority.LOW,
						m(RctCessBattery.ChannelId.MEAN_CELL_VOLTAGE,
								new UnsignedWordElement(0x001A), DIRECT_1_TO_1),
						m(RctCessBattery.ChannelId.MEAN_CELL_TEMPERATURE,
								new UnsignedWordElement(0x001B), SCALE_FACTOR_MINUS_1),
						m(RctCessBattery.ChannelId.SUM_CELL_VOLTAGE,
								new UnsignedWordElement(0x001C), SCALE_FACTOR_2),
						m(RctCessBattery.ChannelId.SWITCH_BOX_TEMPERATURE,
								new UnsignedWordElement(0x001D), SCALE_FACTOR_MINUS_1)));
	}

	@Override
	public String debugLog() {
		return Battery.generateDebugLog(this, this.stateMachine);
	}

	@Override
	public String toString() {
		return toStringHelper(this)
				.addValue(this.id())
				.toString();
	}

}
