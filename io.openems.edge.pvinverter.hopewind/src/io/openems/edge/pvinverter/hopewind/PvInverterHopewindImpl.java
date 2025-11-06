package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
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
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
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
			this.calculateProductionEnergy.update(this.getActivePower().get());
			break;
		}
	}

	private void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		if (this.startStopTarget.get() != StartStop.STOP && 
				this.config.startStop() != StartStopConfig.STOP) {
			try {
				this.setHeartBeat();
				
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
				new FC3ReadRegistersTask(40013, Priority.HIGH,
						m(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT,
								new UnsignedWordElement(40013), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERC,
								new UnsignedWordElement(40014), SCALE_FACTOR_MINUS_2)),
				new FC3ReadRegistersTask(40536, Priority.HIGH,
						m(ElectricityMeter.ChannelId.CURRENT_L1,
								new UnsignedWordElement(40536), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.CURRENT_L2,
								new UnsignedWordElement(40537), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.CURRENT_L3,
								new UnsignedWordElement(40538), SCALE_FACTOR_MINUS_1),
						m(ElectricityMeter.ChannelId.FREQUENCY,
								new UnsignedWordElement(40539), SCALE_FACTOR_MINUS_2),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER,
								new UnsignedWordElement(40540), SCALE_FACTOR_MINUS_2),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER,
								new UnsignedWordElement(40541), SCALE_FACTOR_MINUS_2)),
				new FC6WriteRegisterTask(40013,
						m(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT,
								new UnsignedWordElement(40013), SCALE_FACTOR_1)),
				new FC6WriteRegisterTask(40014,
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERC,
								new UnsignedWordElement(40014), SCALE_FACTOR_MINUS_2)),
				new FC6WriteRegisterTask(32015,
						m(PvInverterHopewind.ChannelId.HEART_BEAT,
								new UnsignedWordElement(32015))));
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
//				PvInverterHopewind.getModbusSlaveNatureTable(accessMode));
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
