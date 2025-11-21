package io.openems.edge.rct.cess.batteryinverter;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static io.openems.edge.common.sum.GridMode.ON_GRID;

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

import io.openems.common.channel.AccessMode;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.ManagedSymmetricBatteryInverter;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.bridge.modbus.api.AbstractOpenemsModbusComponent;
import io.openems.edge.bridge.modbus.api.BridgeModbus;
import io.openems.edge.bridge.modbus.api.ElementToChannelConverter;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.BitsWordElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.rct.cess.ElectricityNode;
import io.openems.edge.rct.cess.batteryinverter.statemachine.Context;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine;
import io.openems.edge.rct.cess.batteryinverter.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "RCT.CESS.200.Battery-Inverter",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
	EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class RctCessBatteryInverterImpl extends AbstractOpenemsModbusComponent implements RctCessBatteryInverter, 
		ManagedSymmetricBatteryInverter, SymmetricBatteryInverter, ElectricityNode,
		OpenemsComponent, ModbusComponent, ModbusSlave, TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(RctCessBatteryInverterImpl.class);
	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	private final CalculateEnergyFromPower calculateAcChargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateAcDischargeEnergy = new CalculateEnergyFromPower(this,
			SymmetricBatteryInverter.ChannelId.ACTIVE_DISCHARGE_ENERGY);

	private Config config = null;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public RctCessBatteryInverterImpl() {
		super(OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				ElectricityNode.ChannelId.values(),
				SymmetricBatteryInverter.ChannelId.values(),
				ManagedSymmetricBatteryInverter.ChannelId.values(),
				RctCessBatteryInverter.ChannelId.values());
		this._setMaxApparentPower(MAX_APPARENT_POWER);
		this._setDcMinVoltage(DC_MIN_VOLTAGE);
		this._setDcMaxVoltage(DC_MAX_VOLTAGE);
		this._setGridMode(ON_GRID);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
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
	public void run(Battery battery, int setActivePower, int setReactivePower) throws OpenemsNamedException {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// TODO Set battery limits and other business logic

		// Prepare Context
		var context = new Context(this, this.config, battery, setActivePower, setReactivePower);

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
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:

			// Calculate the Phase Voltages from Phase to Phase Voltages
			this.calculateAcPhaseVoltages();

			// Calculate the Phase Powers from Voltage, Current and Power Factor
			this.calculateAcPhasePowers();

			// Calculate the Energy values from DC and AC Power.
			this.calculateAcEnergy();
			break;
		}
	}

	private void calculateAcPhaseVoltages() {
		if (this.getVoltageL1L2().isDefined()) {
			this._calculateAcPhaseVoltage(1, this.getVoltageL1L2().get());
		}
		if (this.getVoltageL2L3().isDefined()) {
			this._calculateAcPhaseVoltage(2, this.getVoltageL2L3().get());
		}
		if (this.getVoltageL3L1().isDefined()) {
			this._calculateAcPhaseVoltage(3, this.getVoltageL3L1().get());
		}
	}

	private void _calculateAcPhaseVoltage(int phase, int phaseToPhaseVoltage) {
		var voltage = (int) Math.round(phaseToPhaseVoltage / Math.sqrt(3));
		switch (phase) {
			case 1 -> {
				this._setVoltageL1(voltage);
			}
			case 2 -> {
				this._setVoltageL2(voltage);
			}
			case 3 -> {
				this._setVoltageL3(voltage);
			}
		}
	}

	private void calculateAcPhasePowers() {
		if (!this.getPowerFactor().isDefined()) {
			return;
		}
		// FIXME: Validate and implement a way to have per-phase Power Factor
		var powerFactor = this.getPowerFactor().get();

		this._calculateAcPhasePowers(1, powerFactor);
		this._calculateAcPhasePowers(2, powerFactor);
		this._calculateAcPhasePowers(3, powerFactor);
	}

	private Integer _calculateApparentPhasePower(int phase) {
		Integer voltage;
		Integer current;
		switch (phase) {
			case 1 -> {
				voltage = this.getVoltageL1Channel().getNextValue().get();
				current = this.getCurrentL1().get();
			}
			case 2 -> {
				voltage = this.getVoltageL2Channel().getNextValue().get();
				current = this.getCurrentL2().get();
			}
			case 3 -> {
				voltage = this.getVoltageL3Channel().getNextValue().get();
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

	private void _calculateAcPhasePowers(int phase, float powerFactor) {
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

	/**
	 * Calculate the Energy values from ActivePower.
	 */
	private void calculateAcEnergy() {
		var dischargePower = this.getActivePower().get();
		if (dischargePower == null) {
			// Not available
			this.calculateAcChargeEnergy.update(null);
			this.calculateAcDischargeEnergy.update(null);
		} else if (dischargePower > 0) {
			// Load-From-Grid
			this.calculateAcChargeEnergy.update(0);
			this.calculateAcDischargeEnergy.update(dischargePower);
		} else {
			// Feed-To-Grid
			this.calculateAcChargeEnergy.update(dischargePower * -1);
			this.calculateAcDischargeEnergy.update(0);
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
			case AUTO -> this.startStopTarget.get(); // read StartStop-Channel
			case START -> StartStop.START; // force START
			case STOP -> StartStop.STOP; // force STOP
		};
	}

	@Override
	public int getPowerPrecision() {
		return APPARENT_POWER_PRECISION;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricBatteryInverter.getModbusSlaveNatureTable(accessMode),
				ModbusSlaveNatureTable.of(RctCessBatteryInverter.class, accessMode, 100)
						.build()
		);
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC3ReadRegistersTask(0x0000, Priority.HIGH,
						m(RctCessBatteryInverter.ChannelId.RUN_STATE, new UnsignedWordElement(0x0000))),

				new FC3ReadRegistersTask(0x0001, Priority.LOW,
						m(RctCessBatteryInverter.ChannelId.VOLTAGE_L1_2,
								new UnsignedWordElement(0x0001), SCALE_FACTOR_2),
						m(RctCessBatteryInverter.ChannelId.VOLTAGE_L2_3,
								new UnsignedWordElement(0x0002), SCALE_FACTOR_2),
						m(RctCessBatteryInverter.ChannelId.VOLTAGE_L3_1,
								new UnsignedWordElement(0x0003), SCALE_FACTOR_2),
						m(ElectricityNode.ChannelId.CURRENT_L1,
								new UnsignedWordElement(0x0004), SCALE_FACTOR_2),
						m(ElectricityNode.ChannelId.CURRENT_L2,
								new UnsignedWordElement(0x0005), SCALE_FACTOR_2),
						m(ElectricityNode.ChannelId.CURRENT_L3,
								new UnsignedWordElement(0x0006), SCALE_FACTOR_2),
						m(ElectricityNode.ChannelId.FREQUENCY,
								new UnsignedWordElement(0x0007), SCALE_FACTOR_1)),

				new FC3ReadRegistersTask(0x0008, Priority.HIGH,
						m(SymmetricBatteryInverter.ChannelId.APPARENT_POWER,
								new UnsignedWordElement(0x0008), SCALE_FACTOR_2),
						m(SymmetricBatteryInverter.ChannelId.ACTIVE_POWER,
								new SignedWordElement(0x0009), SCALE_FACTOR_2),
						m(SymmetricBatteryInverter.ChannelId.REACTIVE_POWER,
								new SignedWordElement(0x000A), SCALE_FACTOR_2),
						m(ElectricityNode.ChannelId.POWER_FACTOR,
								new UnsignedWordElement(0x000B),
								chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_1))),

				new FC3ReadRegistersTask(0x000F, Priority.LOW,
						m(RctCessBatteryInverter.ChannelId.IGBT_TEMPERATURE,
								new UnsignedWordElement(0x000F), SCALE_FACTOR_MINUS_1),
						m(RctCessBatteryInverter.ChannelId.AIR_TEMPERATURE,
								new SignedWordElement(0x0010), SCALE_FACTOR_MINUS_1),

						m(new BitsWordElement(0x0011, this)
								.bit(0, RctCessBatteryInverter.ChannelId.EP0_FAULT)
								.bit(1, RctCessBatteryInverter.ChannelId.IGBT_CURRENT_HIGH_FAULT)
								.bit(2, RctCessBatteryInverter.ChannelId.BUSBAR_VOLTAGE_HIGH_FAULT)
								.bit(4, RctCessBatteryInverter.ChannelId.POWER_MODULE_CURRENT_LIMIT_FAULT)
								.bit(5, RctCessBatteryInverter.ChannelId.BALANCE_MODULE_CURRENT_HIGH_FAULT)),
						m(new BitsWordElement(0x0012, this)
								.bit(0, RctCessBatteryInverter.ChannelId.VOLTAGE_24_FAULT)
								.bit(1, RctCessBatteryInverter.ChannelId.FAN_FAULT)
								.bit(2, RctCessBatteryInverter.ChannelId.CONNECTION_FAULT)
								.bit(6, RctCessBatteryInverter.ChannelId.SPD_FAULT)
								.bit(8, RctCessBatteryInverter.ChannelId.POWER_MODULE_TEMPERATURE_HIGH_FAULT)
								.bit(9, RctCessBatteryInverter.ChannelId.BALANCE_MODULE_TEMPERATURE_HIGH_FAULT)
								.bit(10, RctCessBatteryInverter.ChannelId.VOLTAGE_15_FAULT)
								.bit(11, RctCessBatteryInverter.ChannelId.FIRE_SYSTEM_ALARM)
								.bit(12, RctCessBatteryInverter.ChannelId.BATTERY_DRY_FAULT)
								.bit(13, RctCessBatteryInverter.ChannelId.OVERLOAD_FAULT)),
						m(new BitsWordElement(0x0013, this)
								.bit(0, RctCessBatteryInverter.ChannelId.VOLTAGE_HIGH_L1)
								.bit(1, RctCessBatteryInverter.ChannelId.VOLTAGE_HIGH_L2)
								.bit(2, RctCessBatteryInverter.ChannelId.VOLTAGE_HIGH_L3)
								.bit(3, RctCessBatteryInverter.ChannelId.VOLTAGE_LOW_L1)
								.bit(4, RctCessBatteryInverter.ChannelId.VOLTAGE_LOW_L2)
								.bit(5, RctCessBatteryInverter.ChannelId.VOLTAGE_LOW_L3)
								.bit(6, RctCessBatteryInverter.ChannelId.GRID_FREQUENCY_HIGH)
								.bit(7, RctCessBatteryInverter.ChannelId.GRID_FREQUENCY_LOW)
								.bit(8, RctCessBatteryInverter.ChannelId.GRID_PHASE_SEQUENCE_FAULT)
								.bit(9, RctCessBatteryInverter.ChannelId.SOFT_WORK_CURRENT_HIGH_L1)
								.bit(10, RctCessBatteryInverter.ChannelId.SOFT_WORK_CURRENT_HIGH_L2)
								.bit(11, RctCessBatteryInverter.ChannelId.SOFT_WORK_CURRENT_HIGH_L3)
								.bit(12, RctCessBatteryInverter.ChannelId.GRID_VOLTAGE_UNBALANCE)
								.bit(13, RctCessBatteryInverter.ChannelId.GRID_CURRENT_UNBALANCE)
								.bit(14, RctCessBatteryInverter.ChannelId.GRID_LOSS_PHASE)
								.bit(15, RctCessBatteryInverter.ChannelId.N_CURRENT_HIGH)),
						m(new BitsWordElement(0x0014, this)
								.bit(0, RctCessBatteryInverter.ChannelId.PRE_CHARGE_BUS_VOLTAGE_HIGH)
								.bit(1, RctCessBatteryInverter.ChannelId.PRE_CHARGE_BUS_VOLTAGE_LOW)
								.bit(2, RctCessBatteryInverter.ChannelId.UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_HIGH)
								.bit(3, RctCessBatteryInverter.ChannelId.UNCONTROLLED_RECTIFIER_BUS_VOLTAGE_LOW)
								.bit(4, RctCessBatteryInverter.ChannelId.RUN_BUS_VOLTAGE_HIGH)
								.bit(5, RctCessBatteryInverter.ChannelId.RUN_BUS_VOLTAGE_LOW)
								.bit(6, RctCessBatteryInverter.ChannelId.POSITIVE_NEGATIVE_BUS_UNBALANCE)
								.bit(7, RctCessBatteryInverter.ChannelId.CELL_VOLTAGE_LOW)
								.bit(8, RctCessBatteryInverter.ChannelId.CURRENT_MODE_BUS_VOLTAGE_LOW)
								.bit(9, RctCessBatteryInverter.ChannelId.CELL_VOLTAGE_HIGH)
								.bit(10, RctCessBatteryInverter.ChannelId.AC_PRE_CHARGE_CURRENT_HIGH)
								.bit(11, RctCessBatteryInverter.ChannelId.AC_CURRENT_HIGH)
								.bit(12, RctCessBatteryInverter.ChannelId.BALANCE_MODULE_SOFTWARE_CURRENT_HIGH)
								.bit(15, RctCessBatteryInverter.ChannelId.BATTERY_REVERSE)),
						m(new BitsWordElement(0x0015, this)
								.bit(0, RctCessBatteryInverter.ChannelId.PRE_CHARGE_TIMEOUT)
								.bit(1, RctCessBatteryInverter.ChannelId.PRE_CHARGE_CURRENT_HIGH_L1)
								.bit(2, RctCessBatteryInverter.ChannelId.PRE_CHARGE_CURRENT_HIGH_L2)
								.bit(3, RctCessBatteryInverter.ChannelId.PRE_CHARGE_CURRENT_HIGH_L3)),
						m(new BitsWordElement(0x0016, this)
								.bit(2, RctCessBatteryInverter.ChannelId.AD_NULL_SHIFT_FAULT)
								.bit(11, RctCessBatteryInverter.ChannelId.BMS_CELL_FAULT)
								.bit(12, RctCessBatteryInverter.ChannelId.STS_COMMUNICATION_FAULT)
								.bit(13, RctCessBatteryInverter.ChannelId.BMS_CONNECTION_FAIL)
								.bit(14, RctCessBatteryInverter.ChannelId.CAN_CONNECTION_FAULT)
								.bit(15, RctCessBatteryInverter.ChannelId.EMS_CONNECTION_FAULT)),
						m(new BitsWordElement(0x0017, this)
								.bit(0, RctCessBatteryInverter.ChannelId.PRE_CHARGE_RELAY_OPEN_FAULT)
								.bit(1, RctCessBatteryInverter.ChannelId.PRE_CHARGE_RELAY_CLOSE_FAULT)
								.bit(2, RctCessBatteryInverter.ChannelId.PRE_CHARGE_RELAY_OPEN_STATUS_FAULT)
								.bit(3, RctCessBatteryInverter.ChannelId.PRE_CHARGE_RELAY_CLOSE_STATUS_FAULT)
								.bit(4, RctCessBatteryInverter.ChannelId.MAIN_RELAY_OPEN_FAULT)
								.bit(5, RctCessBatteryInverter.ChannelId.MAIN_RELAY_CLOSE_FAULT)
								.bit(6, RctCessBatteryInverter.ChannelId.MAIN_RELAY_OPEN_STATUS_FAULT)
								.bit(7, RctCessBatteryInverter.ChannelId.MAIN_RELAY_CLOSE_STATUS_FAULT)
								.bit(8, RctCessBatteryInverter.ChannelId.AC_MAIN_RELAY_ADHESIVE_FAULT)
								.bit(9, RctCessBatteryInverter.ChannelId.DC_RELAY_OPEN_FAULT)),
						m(new BitsWordElement(0x0018, this)
								.bit(0, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_HIGH_L1_FAULT)
								.bit(1, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_HIGH_L2_FAULT)
								.bit(2, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_HIGH_L3_FAULT)
								.bit(3, RctCessBatteryInverter.ChannelId.ISLAND_ENABLE_FAULT)
								.bit(5, RctCessBatteryInverter.ChannelId.SYSTEM_RESONANCE_FAULT)
								.bit(6, RctCessBatteryInverter.ChannelId.SOFT_WORK_VOLTAGE_HIGH_CURRENT_HIGH_FAULT)
								.bit(8, RctCessBatteryInverter.ChannelId.MODULE_DIAL_UP_ADDRESS_FAULT)
								.bit(9, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_LOW_L1_FAULT)
								.bit(10, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_LOW_L2_FAULT)
								.bit(11, RctCessBatteryInverter.ChannelId.INVERTER_VOLTAGE_LOW_L3_FAULT)
								.bit(12, RctCessBatteryInverter.ChannelId.OFFGRID_NO_SYNCHRONIZATION_SIGNAL_FAULT)
								.bit(14, RctCessBatteryInverter.ChannelId.OFFGRID_SHORT_CIRCUIT_FAULT)
								.bit(15, RctCessBatteryInverter.ChannelId.VOLTAGE_LOW_CROSS_OVER_TIME_FAULT))
			)
		);
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
		return new StringBuilder()
				.append(this.stateMachine.debugLog())
				.toString();
	}

	@Override
	public String toString() {
		return toStringHelper(this)
				.addValue(this.id())
				.toString();
	}

}
