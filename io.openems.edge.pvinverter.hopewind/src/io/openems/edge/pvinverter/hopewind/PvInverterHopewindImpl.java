package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_1;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_2;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_MINUS_3;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.chain;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.MANDATORY;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicy.STATIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.time.Duration;
import java.time.Instant;
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
import io.openems.edge.bridge.modbus.api.element.DummyRegisterElement;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.element.UnsignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC3ReadRegistersTask;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.bridge.modbus.api.task.Task;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.taskmanager.Priority;
import io.openems.edge.common.type.TypeUtils;
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
public class PvInverterHopewindImpl extends AbstractOpenemsModbusComponent implements PvInverterHopewind, ManagedSymmetricPvInverter,
		ElectricityMeter, OpenemsComponent, ModbusComponent, ModbusSlave, TimedataProvider, EventHandler, StartStoppable {

	private final Logger log = LoggerFactory.getLogger(PvInverterHopewindImpl.class);

	private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);

	private final StateMachine stateMachine = new StateMachine(State.UNDEFINED);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata = null;

	/**
	 * Timestamp of the last externally received ACTIVE_POWER_LIMIT setpoint.
	 * Initialized at activation, so the watchdog is armed even if no setpoint ever
	 * arrives.
	 */
	private volatile Instant lastSetpoint = Instant.MIN;

	/** Failsafe stage 1: force the power limit to 0, re-asserted every cycle. */
	private volatile boolean failsafeActive = false;

	/** Failsafe stage 2: command the inverter to shut down entirely. */
	private volatile boolean failsafeShutdownActive = false;

	/** Guards the failsafe's own writes from re-arming the watchdog. */
	private volatile boolean selfWrite = false;

	/** Consecutive heartbeat write failures; accessed from the Modbus thread. */
	private int heartbeatFailures = 0;

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
		this._setMaxApparentPower(APPARENT_POWER_MAX);
		this._setMaxActivePower(ACTIVE_POWER_MAX);
		this._setMaxReactivePower(REACTIVE_POWER_MAX);

		this.config = config;
		if (!config.enabled()) {
			return;
		}
		ElectricityMeter.calculateAverageVoltageFromPhases(this);
		ElectricityMeter.calculateSumCurrentFromPhases(this);
		PvInverterHopewind.calculatePhasePowersFromVoltageAndCurrent(this);

		// Arm the watchdog at activation, so that "main edge died before the first
		// setpoint" is covered as well. Every external ACTIVE_POWER_LIMIT write
		// re-arms it and releases an active failsafe.
		this.lastSetpoint = Instant.now(this.componentManager.getClock());
		this.getActivePowerLimitChannel().onSetNextWrite(this::onActivePowerLimitWrite);
	}

	/**
	 * Called on every write to the ACTIVE_POWER_LIMIT channel: external setpoints
	 * re-arm the watchdog and release an active failsafe; the failsafe's own writes
	 * are ignored via {@link #selfWrite}. In PROPORTIONAL mode the setpoint [W] is
	 * additionally forwarded as percent of the maximum active power, because the
	 * device then only evaluates the percent register.
	 *
	 * @param value the written value in [W]
	 * @throws OpenemsNamedException on error
	 */
	private void onActivePowerLimitWrite(Integer value) throws OpenemsNamedException {
		if (this.selfWrite || value == null) {
			return;
		}
		this.lastSetpoint = Instant.now(this.componentManager.getClock());
		if (this.failsafeActive) {
			this.logInfo(this.log, "Fresh power limit setpoint [" + value + " W] received. Releasing failsafe.");
		}
		this.failsafeActive = false;
		this.failsafeShutdownActive = false;

		if (this.config.activePowerLimitMode() == ActivePowerLimitMode.PROPORTIONAL) {
			this.setActivePowerLimitPercent(convertWattToPercent(value));
		}
	}

	protected static int convertWattToPercent(int watt) {
		return Math.max(0, Math.min(100, (int) Math.round(watt * 100.0 / ACTIVE_POWER_MAX)));
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
			this.sendHeartbeat();
			this.handleStateMachine();
			this.handleWatchdog();
			this.handleInverterMode();

			this.calculateProductionEnergy.update(this.getActivePower().get());
			break;
		}
	}

	/**
	 * Writes the heartbeat register every cycle, unconditionally.
	 *
	 * <p>
	 * Per field observation the register only drives the communication LED on the
	 * inverter - the device takes no protective action when writes stop. It is
	 * therefore kept as a pure keepalive/diagnostic and is NOT part of the
	 * failsafe concept; see {@link #handleWatchdog()} for the actual protection.
	 */
	private void sendHeartbeat() {
		try {
			this.setHeartBeat();
		} catch (IllegalArgumentException | OpenemsNamedException e) {
			this.logError(this.log, "Setting HeartBeat failed: " + e.getMessage());
		}
	}

	/**
	 * Tracks the result of every heartbeat Modbus write; raises
	 * {@link PvInverterHopewind.ChannelId#HEART_BEAT_FAILED} after
	 * {@link PvInverterHopewind#HEART_BEAT_FAILURE_THRESHOLD} consecutive
	 * failures. Called on the Modbus worker thread.
	 *
	 * @param state the {@link Task.ExecuteState} of the write
	 */
	private void onHeartbeatExecuted(Task.ExecuteState state) {
		if (state instanceof Task.ExecuteState.Error) {
			if (++this.heartbeatFailures >= HEART_BEAT_FAILURE_THRESHOLD) {
				this._setHeartBeatFailed(true);
			}
		} else if (state instanceof Task.ExecuteState.Ok) {
			this.heartbeatFailures = 0;
			this._setHeartBeatFailed(false);
		}
	}

	private void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

		// Prepare the Context
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

	/**
	 * Watchdog failsafe: the inverter has no communication supervision of its own,
	 * so protection must be an active command from this driver.
	 *
	 * <p>
	 * Stage 1: after {@link Config#watchdogTimer()} seconds without an external
	 * ACTIVE_POWER_LIMIT setpoint, the power limit is forced to 0 - re-asserted on
	 * every cycle until a fresh setpoint arrives (latched failsafe).
	 *
	 * <p>
	 * Stage 2: after {@link Config#watchdogShutdownTimer()} additional seconds the
	 * inverter is commanded to shut down entirely via the state machine (see
	 * {@link #getStartStopTarget()}).
	 *
	 * <p>
	 * Note: values re-applied by an Api-Controller ({@code ApiWorker}) are
	 * indistinguishable from fresh setpoints, so the effective reaction time after
	 * a dead control path is {@code apiTimeout + watchdogTimer}. Configure the
	 * Api-Controller's {@code apiTimeout} non-zero and small.
	 */
	private void handleWatchdog() {
		if (!this.config.watchdog()) {
			this._setWatchdogTripped(false);
			this._setWatchdogShutdown(false);
			return;
		}

		final var now = Instant.now(this.componentManager.getClock());

		// An intentional STOP (config or channel) means no setpoints are expected:
		// keep the watchdog idle. Not applied while the failsafe itself forces STOP.
		if (!this.failsafeShutdownActive && this.getStartStopTarget() == StartStop.STOP) {
			this.lastSetpoint = now;
		}

		final var sinceSetpoint = Duration.between(this.lastSetpoint, now).getSeconds();

		if (!this.failsafeActive && sinceSetpoint > this.config.watchdogTimer()) {
			this.failsafeActive = true;
			this.logWarn(this.log, "Watchdog tripped after " + sinceSetpoint
					+ " s without a power limit setpoint. Forcing power limit to 0.");
		}

		if (this.failsafeActive) {
			var shutdownTimer = this.config.watchdogShutdownTimer();
			if (!this.failsafeShutdownActive && shutdownTimer > 0
					&& sinceSetpoint > this.config.watchdogTimer() + shutdownTimer) {
				this.failsafeShutdownActive = true;
				this.logWarn(this.log, "Watchdog escalation after " + sinceSetpoint
						+ " s without a power limit setpoint. Commanding inverter shutdown.");
			}
			this.applyZeroPowerLimit();
		}

		this._setWatchdogTripped(this.failsafeActive);
		this._setWatchdogShutdown(this.failsafeShutdownActive);
	}

	/**
	 * Forces the power limit to 0. Writes both the actual [W] and the percent [%]
	 * register, so the limit is effective regardless of the mode the device is
	 * actually in.
	 */
	private void applyZeroPowerLimit() {
		this.selfWrite = true;
		try {
			this.setActivePowerLimit(0);
			this.setActivePowerLimitPercent(0);
		} catch (Exception e) {
			this.logError(this.log, "Applying failsafe zero power limit failed: " + e.getMessage());
		} finally {
			this.selfWrite = false;
		}
	}

	private void handleInverterMode() {
		if (!this.getActivePowerLimitMode().isDefined()) {
			return;
		}
		ActivePowerLimitMode activePowerLimitModeConfig = this.config.activePowerLimitMode();
		ActivePowerLimitMode activePowerLimitMode = this.getActivePowerLimitMode().asEnum();
		if (activePowerLimitMode != activePowerLimitModeConfig) {
			try {
				this.setActivePowerLimitMode(activePowerLimitModeConfig);

			} catch (OpenemsNamedException e) {
			this.logError(this.log, 
				"Setting ACTIVE_POWER_LIMIT_MODE failed: " + e.getMessage());
			}
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
		if (this.failsafeShutdownActive) {
			// Watchdog stage 2: force STOP until fresh setpoints arrive
			return StartStop.STOP;
		}
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
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				ElectricityMeter.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricPvInverter.getModbusSlaveNatureTable(accessMode));
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
//				new FC3ReadRegistersTask(1080, Priority.LOW,
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

//				new FC3ReadRegistersTask(30117, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.DAILY_GENERATION,
//								new UnsignedDoublewordElement(30117).wordOrder(LSWMSW), SCALE_FACTOR_MINUS_2),
//						m(PvInverterHopewind.ChannelId.CUMULATIVE_GENERATION,
//								new UnsignedDoublewordElement(30119).wordOrder(LSWMSW), SCALE_FACTOR_MINUS_2)),
//						m(PvInverterHopewind.ChannelId.REG_30121,
//								new UnsignedWordElement(30121)),
//						m(PvInverterHopewind.ChannelId.REG_30122,
//								new UnsignedWordElement(30122)),
//						m(PvInverterHopewind.ChannelId.REG_30123,
//								new UnsignedWordElement(30123))),

//				new FC3ReadRegistersTask(31112, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.REG_31112,
//								new UnsignedWordElement(31112)),
//						m(PvInverterHopewind.ChannelId.REG_31113,
//								new UnsignedWordElement(31113)),
//						m(PvInverterHopewind.ChannelId.REG_31114,
//								new UnsignedWordElement(31114)),
//						m(PvInverterHopewind.ChannelId.REG_31115,
//								new UnsignedWordElement(31115))),

//				//Not available (Modbus Exception)
//				new FC3ReadRegistersTask(31119, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.NIGHT_SLEEP,
//								new UnsignedWordElement(31119)),
//						m(PvInverterHopewind.ChannelId.RSD_ENABLED,
//								new UnsignedWordElement(31120))),

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

//				new FC3ReadRegistersTask(34074, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.DRM_ENABLED,
//								new UnsignedWordElement(34074)),
//						m(PvInverterHopewind.ChannelId.RIPPLE_CONTROL_ENABLED,
//								new UnsignedWordElement(34075))),

//				new FC3ReadRegistersTask(34294, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.NS_PROTECTION_ENABLED,
//								new UnsignedWordElement(34294)),
//						m(PvInverterHopewind.ChannelId.NS_PROTECTION_SWITCH,
//								new UnsignedWordElement(34295))),

//				new FC3ReadRegistersTask(40003, Priority.HIGH,
//						m(PvInverterHopewind.ChannelId.REG_40000,
//								new UnsignedWordElement(40000)),
//						m(PvInverterHopewind.ChannelId.REG_40001,
//								new UnsignedWordElement(40001)),
//						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION_MODE,
//								new UnsignedWordElement(40002)),
//						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_FACTOR_REGULATION,
//								new UnsignedWordElement(40003), chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_4)),
//						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION_VALUE,
//								new UnsignedWordElement(40004), SCALE_FACTOR_1),
//						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION_PERCENT,
//								new UnsignedWordElement(40005), SCALE_FACTOR_2),
//						new DummyRegisterElement(40006, 40010),
//						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_MODE,
//								new UnsignedWordElement(40011)),
//						m(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT,
//								new UnsignedWordElement(40012), SCALE_FACTOR_1),
//						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERCENT,
//								new UnsignedWordElement(40013), SCALE_FACTOR_MINUS_2)),

//				new FC3ReadRegistersTask(40200, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.STARTUP,
//								new UnsignedWordElement(40200)),
//						m(PvInverterHopewind.ChannelId.SHUTDOWN,
//								new UnsignedWordElement(40201)),
//						m(PvInverterHopewind.ChannelId.RESET,
//								new UnsignedWordElement(40202))),

				new FC3ReadRegistersTask(40500, Priority.LOW,
						m(PvInverterHopewind.ChannelId.DC_VOLTAGE_MPPT_1,
								new UnsignedWordElement(40500), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.DC_VOLTAGE_MPPT_2,
								new UnsignedWordElement(40501), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.DC_VOLTAGE_MPPT_3,
								new UnsignedWordElement(40502), SCALE_FACTOR_MINUS_1),
						m(PvInverterHopewind.ChannelId.DC_VOLTAGE_MPPT_4,
								new UnsignedWordElement(40503), SCALE_FACTOR_MINUS_1),
						new DummyRegisterElement(40504, 40507),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_1,
								new SignedWordElement(40508), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_2,
								new SignedWordElement(40509), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_3,
								new SignedWordElement(40510), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_4,
								new SignedWordElement(40511), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_5,
								new SignedWordElement(40512), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_6,
								new SignedWordElement(40513), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_7,
								new SignedWordElement(40514), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_8,
								new SignedWordElement(40515), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_9,
								new SignedWordElement(40516), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_10,
								new SignedWordElement(40517), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_11,
								new SignedWordElement(40518), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_12,
								new SignedWordElement(40519), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_13,
								new SignedWordElement(40520), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_14,
								new SignedWordElement(40521), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_15,
								new SignedWordElement(40522), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_CURRENT_STRING_16,
								new SignedWordElement(40523), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_MPPT_1,
								new SignedWordElement(40524), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_MPPT_2,
								new SignedWordElement(40525), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_MPPT_3,
								new SignedWordElement(40526), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_MPPT_4,
								new SignedWordElement(40527), SCALE_FACTOR_1)),

				new FC3ReadRegistersTask(40532, Priority.HIGH,
						m(ElectricityMeter.ChannelId.VOLTAGE_L1,
								new SignedWordElement(40532), CONVERT_VOLTAGE),
						m(ElectricityMeter.ChannelId.VOLTAGE_L2,
								new SignedWordElement(40533), CONVERT_VOLTAGE),
						m(ElectricityMeter.ChannelId.VOLTAGE_L3,
								new SignedWordElement(40534), CONVERT_VOLTAGE),
						m(ElectricityMeter.ChannelId.CURRENT_L1,
								new SignedWordElement(40535), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.CURRENT_L2,
								new SignedWordElement(40536), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.CURRENT_L3,
								new SignedWordElement(40537), SCALE_FACTOR_2),
						m(ElectricityMeter.ChannelId.FREQUENCY,
								new UnsignedWordElement(40538), SCALE_FACTOR_1),
						m(ElectricityMeter.ChannelId.ACTIVE_POWER,
								new SignedWordElement(40539), SCALE_FACTOR_1),
						m(ElectricityMeter.ChannelId.REACTIVE_POWER,
								new SignedWordElement(40540), SCALE_FACTOR_MINUS_3),
						m(PvInverterHopewind.ChannelId.APPERENT_POWER,
								new SignedWordElement(40541), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.POWER_FACTOR,
								new SignedWordElement(40542), chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_4))),

				new FC3ReadRegistersTask(40544, Priority.LOW,
						m(PvInverterHopewind.ChannelId.TEMPERATURE,
								new SignedWordElement(40544), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.INSULATION_RESISTANCE,
								new UnsignedWordElement(40545), SCALE_FACTOR_2),
						m(PvInverterHopewind.ChannelId.RUNNING_STATE,
								new UnsignedWordElement(40546)),
						m(PvInverterHopewind.ChannelId.INVERTER_STATE,
								new UnsignedWordElement(40547)),
						new DummyRegisterElement(40548, 40560),
//						m(PvInverterHopewind.ChannelId.TODAY_YIELD,
//								new UnsignedDoublewordElement(40548).wordOrder(LSWMSW), SCALE_FACTOR_1),
//						m(PvInverterHopewind.ChannelId.CO2_REDUCTION,
//								new UnsignedDoublewordElement(40552).wordOrder(LSWMSW), SCALE_FACTOR_1),
//						m(PvInverterHopewind.ChannelId.DAILY_RUNTIME,
//								new UnsignedWordElement(40554), SCALE_FACTOR_1),
//						m(PvInverterHopewind.ChannelId.TOTAL_RUNTIME,
//								new UnsignedDoublewordElement(40555).wordOrder(LSWMSW), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.FAULT_1,
								new UnsignedWordElement(40561)),
						m(PvInverterHopewind.ChannelId.FAULT_2,
								new UnsignedWordElement(40562)),
						m(PvInverterHopewind.ChannelId.FAULT_3,
								new UnsignedWordElement(40563)),
						m(PvInverterHopewind.ChannelId.FAULT_4,
								new UnsignedWordElement(40564)),
						m(PvInverterHopewind.ChannelId.FAULT_5,
								new UnsignedWordElement(40565)),
						m(PvInverterHopewind.ChannelId.FAULT_6,
								new UnsignedWordElement(40566)),
						m(PvInverterHopewind.ChannelId.FAULT_7,
								new UnsignedWordElement(40567)),
						m(PvInverterHopewind.ChannelId.FAULT_8,
								new UnsignedWordElement(40568)),
						m(PvInverterHopewind.ChannelId.ALARM_1,
								new UnsignedWordElement(40569)),
						m(PvInverterHopewind.ChannelId.ALARM_2,
								new UnsignedWordElement(40570)),
						m(PvInverterHopewind.ChannelId.ALARM_3,
								new UnsignedWordElement(40571)),
						m(PvInverterHopewind.ChannelId.ALARM_4,
								new UnsignedWordElement(40572)),
						m(PvInverterHopewind.ChannelId.ALARM_5,
								new UnsignedWordElement(40573)),
						m(PvInverterHopewind.ChannelId.ALARM_6,
								new UnsignedWordElement(40574)),
						m(PvInverterHopewind.ChannelId.ALARM_7,
								new UnsignedWordElement(40575)),
						m(PvInverterHopewind.ChannelId.ALARM_8,
								new UnsignedWordElement(40576)),
						m(PvInverterHopewind.ChannelId.FAULT_9,
								new UnsignedWordElement(40577)),
						m(PvInverterHopewind.ChannelId.FAULT_CODE,
								new UnsignedWordElement(40578)),
						m(PvInverterHopewind.ChannelId.ALARM_CODE,
								new UnsignedWordElement(40579)),
						m(PvInverterHopewind.ChannelId.ARCING_1,
								new UnsignedWordElement(40580)),
						m(PvInverterHopewind.ChannelId.ARCING_2,
								new UnsignedWordElement(40581)),
						new DummyRegisterElement(40582, 40593),
						m(PvInverterHopewind.ChannelId.DC_POWER,
								new UnsignedWordElement(40594), SCALE_FACTOR_1)),

//				new FC3ReadRegistersTask(40601, Priority.LOW,
//						m(PvInverterHopewind.ChannelId.UNIT_ID,
//							new StringWordElement(40601, 27), STRING_CONVERTER),
//						m(PvInverterHopewind.ChannelId.REG_40628,
//							new UnsignedWordElement(40628)),
//						m(PvInverterHopewind.ChannelId.REG_40629,
//							new UnsignedWordElement(40629)),
//						m(PvInverterHopewind.ChannelId.REG_40630,
//							new UnsignedWordElement(40630)),
//						m(PvInverterHopewind.ChannelId.REG_40631,
//							new UnsignedWordElement(40631)),
//						m(PvInverterHopewind.ChannelId.REG_40632,
//							new UnsignedWordElement(40632)),
//						m(PvInverterHopewind.ChannelId.REG_40633,
//							new UnsignedWordElement(40633)),
//						m(PvInverterHopewind.ChannelId.REG_40634,
//							new UnsignedWordElement(40634)),
//						m(PvInverterHopewind.ChannelId.REG_40635,
//							new UnsignedWordElement(40635)),
//						m(PvInverterHopewind.ChannelId.REG_40636,
//							new UnsignedWordElement(40636)),
//						m(PvInverterHopewind.ChannelId.REG_40637,
//							new UnsignedWordElement(40637)),
//						m(PvInverterHopewind.ChannelId.REG_40638,       
//							new UnsignedWordElement(40638)),
//						m(PvInverterHopewind.ChannelId.REG_40639,
//							new UnsignedWordElement(40639)),
//						m(PvInverterHopewind.ChannelId.REG_40640,
//							new UnsignedWordElement(40640)),
//						m(PvInverterHopewind.ChannelId.REG_40641,
//							new UnsignedWordElement(40641)),
//						m(PvInverterHopewind.ChannelId.REG_40642,
//							new UnsignedWordElement(40642)),
//						m(PvInverterHopewind.ChannelId.REG_40643,
//							new UnsignedWordElement(40643)),
//						m(PvInverterHopewind.ChannelId.REG_40644,
//							new UnsignedWordElement(40644))),

				new FC3ReadRegistersTask(40646, Priority.LOW,
						m(PvInverterHopewind.ChannelId.RATED_POWER,
							new UnsignedWordElement(40646), SCALE_FACTOR_3),
						m(PvInverterHopewind.ChannelId.RATED_VOLTAGE,
							new UnsignedWordElement(40647))),

				new FC3ReadRegistersTask(41000, Priority.LOW,
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_1,
								new SignedWordElement(41000), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_2,
								new SignedWordElement(41001), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_3,
								new SignedWordElement(41002), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_4,
								new SignedWordElement(41003), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_5,
								new SignedWordElement(41004), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_6,
								new SignedWordElement(41005), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_7,
								new SignedWordElement(41006), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_8,
								new SignedWordElement(41007), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_9,
								new SignedWordElement(41008), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_10,
								new SignedWordElement(41009), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_11,
								new SignedWordElement(41010), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_12,
								new SignedWordElement(41011), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_13,
								new SignedWordElement(41012), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_14,
								new SignedWordElement(41013), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_15,
								new SignedWordElement(41014), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.DC_POWER_STRING_16,
								new SignedWordElement(41015), SCALE_FACTOR_1),
						new DummyRegisterElement(41016, 41019)),

//				new FC16WriteRegistersTask(1083,
//						m(PvInverterHopewind.ChannelId.REG_1083,
//								new UnsignedWordElement(1083)),
//						m(PvInverterHopewind.ChannelId.REG_1084,
//								new UnsignedWordElement(1084)),
//						m(PvInverterHopewind.ChannelId.REG_1085,
//								new UnsignedWordElement(1085))),

//				// Not available (Modbus Exception)
//				new FC16WriteRegistersTask(31119,
//						m(PvInverterHopewind.ChannelId.NIGHT_SLEEP,
//								new UnsignedWordElement(31119))),
//						m(PvInverterHopewind.ChannelId.RSD_ENABLED,
//								new UnsignedWordElement(31120))),

				new FC6WriteRegisterTask(this::onHeartbeatExecuted, 32014,
						m(PvInverterHopewind.ChannelId.HEART_BEAT,
								new UnsignedWordElement(32014))),

				new FC3ReadRegistersTask(40011, Priority.HIGH,
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_MODE,
								new UnsignedWordElement(40011)),
						m(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT,
								new UnsignedWordElement(40012), SCALE_FACTOR_1),
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERCENT,
								new UnsignedWordElement(40013), SCALE_FACTOR_MINUS_2)),

//				new FC16WriteRegistersTask(34074,
//						m(PvInverterHopewind.ChannelId.DRM_ENABLED,
//								new UnsignedWordElement(34074)),
//						m(PvInverterHopewind.ChannelId.RIPPLE_CONTROL_ENABLED,
//								new UnsignedWordElement(34075))),
//
//				new FC16WriteRegistersTask(34294,
//						m(PvInverterHopewind.ChannelId.NS_PROTECTION_ENABLED,
//								new UnsignedWordElement(34294)),
//						m(PvInverterHopewind.ChannelId.NS_PROTECTION_SWITCH,
//								new UnsignedWordElement(34295))),

				new FC6WriteRegisterTask(40002,
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION_MODE,
								new UnsignedWordElement(40002))),
				new FC6WriteRegisterTask(40003,
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_FACTOR_REGULATION,
								new SignedWordElement(40003), chain(CONVERT_FLOAT, SCALE_FACTOR_MINUS_4))),
				new FC6WriteRegisterTask(40004,
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION,
								new SignedWordElement(40004), SCALE_FACTOR_1)),
				new FC6WriteRegisterTask(40005,
						m(PvInverterHopewind.ChannelId.REACTIVE_POWER_REGULATION_PERCENT,
								new UnsignedWordElement(40005), SCALE_FACTOR_MINUS_2)),

				new FC6WriteRegisterTask(40011,
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_MODE,
								new UnsignedWordElement(40011))),
				new FC6WriteRegisterTask(40012,
						m(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT,
								new UnsignedWordElement(40012), SCALE_FACTOR_1)),
				new FC6WriteRegisterTask(40013,
						m(PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERCENT,
								new UnsignedWordElement(40013), SCALE_FACTOR_MINUS_2)),

				new FC6WriteRegisterTask(40200,
						m(PvInverterHopewind.ChannelId.STARTUP,
								new UnsignedWordElement(40200))),
				new FC6WriteRegisterTask(40201,
						m(PvInverterHopewind.ChannelId.SHUTDOWN,
								new UnsignedWordElement(40201))),
				new FC6WriteRegisterTask(40202,
						m(PvInverterHopewind.ChannelId.RESET,
								new UnsignedWordElement(40202)))
		);
	}

	public static final ElementToChannelConverter SCALE_FACTOR_MINUS_4 = new ElementToChannelScaleFactorConverter(-4);

	protected static final ElementToChannelConverter CONVERT_STRING = new ElementToChannelConverter(v -> {
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

	protected static final ElementToChannelConverter CONVERT_VOLTAGE = new ElementToChannelConverter(v -> {
		if (v == null) {
				return null;
		}
		int value = TypeUtils.getAsType(OpenemsType.INTEGER, v);
		return (int) Math.round(value * 100 / 1.732);
	});

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
				.append(stateMachine.debugLog())
				.toString();
	}

}
