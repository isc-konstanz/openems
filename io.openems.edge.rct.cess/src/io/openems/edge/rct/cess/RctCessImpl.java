package io.openems.edge.rct.cess;

import static com.google.common.base.MoreObjects.toStringHelper;
import static io.openems.edge.bridge.modbus.api.ElementToChannelConverter.SCALE_FACTOR_2;
import static io.openems.edge.common.type.Phase.SingleOrAllPhase.ALL;
import static io.openems.edge.ess.power.api.Pwr.ACTIVE;
import static io.openems.edge.ess.power.api.Pwr.REACTIVE;
import static io.openems.edge.ess.power.api.Relationship.EQUALS;
import static io.openems.edge.rct.cess.statemachine.StateMachine.State.UNDEFINED;

import java.util.ArrayList;
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
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.bridge.modbus.api.ModbusProtocol;
import io.openems.edge.bridge.modbus.api.element.SignedWordElement;
import io.openems.edge.bridge.modbus.api.task.FC6WriteRegisterTask;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.api.HybridEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Constraint;
import io.openems.edge.ess.power.api.Power;
import io.openems.edge.rct.cess.batteryinverter.BatteryInverterRctCess;
import io.openems.edge.rct.cess.statemachine.Context;
import io.openems.edge.rct.cess.statemachine.StateMachine;
import io.openems.edge.rct.cess.statemachine.StateMachine.State;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "Ess.RCT.CESS",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE,
})
public class RctCessImpl extends AbstractOpenemsModbusComponent implements RctCess, HybridEss, ManagedSymmetricEss, SymmetricEss, 
		ElectricityNode, OpenemsComponent, ModbusComponent, ModbusSlave, TimedataProvider, EventHandler, StartStoppable {

	private final Logger logger = LoggerFactory.getLogger(RctCessImpl.class);
	private final StateMachine stateMachine = new StateMachine(UNDEFINED);
	private final ChannelManager channelManager = new ChannelManager(this);

	private final AtomicReference<StartStop> startStopTarget = new AtomicReference<>(StartStop.UNDEFINED);

	private final CalculateEnergyFromPower calculateDcChargeEnergy = new CalculateEnergyFromPower(this,
			HybridEss.ChannelId.DC_CHARGE_ENERGY);
	private final CalculateEnergyFromPower calculateDcDischargeEnergy = new CalculateEnergyFromPower(this,
			HybridEss.ChannelId.DC_DISCHARGE_ENERGY);

	private Config config = null;

	@Reference
	private Power power;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private Battery battery;

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	private ManagedSymmetricBatteryInverter batteryInverter;

	@Override
	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setModbus(BridgeModbus modbus) {
		super.setModbus(modbus);
	}

	public RctCessImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				StartStoppable.ChannelId.values(),
				ElectricityNode.ChannelId.values(),
				SymmetricEss.ChannelId.values(),
				ManagedSymmetricEss.ChannelId.values(),
				HybridEss.ChannelId.values(),
				RctCess.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		this.config = config;
		if (super.activate(context, config.id(), config.alias(), config.enabled(), 1, this.cm,
				"Modbus", config.modbus_id())) {
			return;
		}

		// Update filter for 'Battery'
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "battery", config.battery_id())) {
			return;
		}

		// Update filter for 'BatteryInverter'
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "batteryInverter", config.batteryInverter_id())) {
			return;
		}

		this.getChannelManager().activate(this.getBattery(), this.getBatteryInverter());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.getChannelManager().deactivate();
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
			break;
		}
	}

	protected void handleStateMachine() {
		// Store the current State
		this._setStateMachine(this.stateMachine.getCurrentState());

		// Initialize 'Start-Stop' Channel
		this._setStartStop(StartStop.UNDEFINED);

        // Calculate the DC PV- and Discharge Power value from DC and Battery Power
        this.calculateDcPower();

        // Calculate the Energy values from DC Discharge Power.
        this.calculateDcEnergy();

		// Prepare Context
		var context = new Context(this, this.config, this.getBattery(), this.getBatteryInverter(), this.componentManager.getClock());
		
		// Call the StateMachine
		try {
			this.stateMachine.run(context);
			this._setRunFailed(false);

		} catch (OpenemsNamedException e) {
			this._setRunFailed(true);
			this.logError(this.logger, "StateMachine failed: " + e.getMessage());
		}
	}

	/**
	 * Calculate the PV- and Surplus Power value from DC and Battery Power.
	 * 
	 * @param battery the battery to calculate the PV Power from
	 * 
	 */
	private void calculateDcPower() {
		if (!this.getBattery().getVoltage().isDefined() || !this.getBattery().getCurrent().isDefined()) {
			return;
		}
		var batteryVoltage = this.getBattery().getVoltage().get();
		var batteryCurrent = this.getBattery().getCurrent().get();
		var batteryPower = batteryVoltage * batteryCurrent;
		this._setDcDischargePower(-batteryPower);

		if (this.config.hasDcCharger() && this.getBatteryInverter() instanceof BatteryInverterRctCess) {
			var inverter = (BatteryInverterRctCess) this.getBatteryInverter();
			if (!inverter.getDcPower().isDefined()) {
				this._setPvPower(null);
			} else {
				var dcPower = inverter.getDcPower().get();
				var pvPower = Math.max(dcPower - batteryPower, 0);

				this._setPvPower(pvPower);
			}
		} else {
			this._setPvPower(null);
		}
	}

	/**
	 * Calculate the Energy values from DcPower.
	 */
	private void calculateDcEnergy() {
		var dischargePower = this.getDcDischargePowerChannel().getNextValue().get();
		if (dischargePower == null) {
			// Not available
			this.calculateDcChargeEnergy.update(null);
			this.calculateDcDischargeEnergy.update(null);
		} else if (dischargePower >= 0) {
			// Load-From-Grid
			this.calculateDcChargeEnergy.update(0);
			this.calculateDcDischargeEnergy.update(dischargePower);
		} else {
			// Feed-To-Grid
			this.calculateDcChargeEnergy.update(dischargePower * -1);
			this.calculateDcDischargeEnergy.update(0);
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

	/**
	 * Retrieves StaticConstraints from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public Constraint[] getStaticConstraints() throws OpenemsNamedException {
		var result = new ArrayList<Constraint>();

		// Get BatteryInverterConstraints
		var constraints = this.getBatteryInverter().getStaticConstraints();

		for (var c : constraints) {
			result.add(this.getPower().createSimpleConstraint(c.description, this, c.phase, c.pwr, c.relationship, c.value));
		}

		// If the GenericEss is not in State "STARTED" block ACTIVE and REACTIVE Power!
		if (!this.isStarted()) {
			result.add(this.createPowerConstraint("ActivePower Constraint ESS not Started", ALL, ACTIVE, EQUALS, 0));
			result.add(this.createPowerConstraint("ReactivePower Constraint ESS not Started", ALL, REACTIVE, EQUALS, 0));
		}
		return result.toArray(new Constraint[result.size()]);
	}

	protected ChannelManager getChannelManager() {
		return this.channelManager;
	}

	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	protected Battery getBattery() {
		return this.battery;
	}

	protected ManagedSymmetricBatteryInverter getBatteryInverter() {
		return this.batteryInverter;
	}

	@Override
	public boolean isManaged() {
		return this.batteryInverter.isManaged();
	}

	@Override
	public Power getPower() {
		return this.power;
	}

	/**
	 * Forwards the power request to the {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		this.getBatteryInverter().run(this.getBattery(), activePower, reactivePower);

		IntegerWriteChannel setActivePowerChannel = this.channel(RctCess.ChannelId.SET_ACTIVE_POWER);
		setActivePowerChannel.setNextWriteValue(activePower);
		IntegerWriteChannel setReactivePowerChannel = this.channel(RctCess.ChannelId.SET_REACTIVE_POWER);
		setReactivePowerChannel.setNextWriteValue(reactivePower);
	}

	/**
	 * Retrieves PowerPrecision from {@link SymmetricBatteryInverter}.
	 *
	 * {@inheritDoc}
	 */
	@Override
	public int getPowerPrecision() {
		return this.getBatteryInverter().getPowerPrecision();
	}

	@Override
	public final Integer getSurplusPower() {
		if (!this.config.hasDcCharger() || !this.getPvPowerChannel().getNextValue().isDefined() || !this.getBattery().getSoc().isDefined()) {
			return null;
		}
		// Is the Battery full?
		if (this.getBattery().getSoc().get() < 99) {
			return null;
		}
		// Is PV producing?
		int pvPower = this.getPvPowerChannel().getNextValue().get();
		if (pvPower < 100) {
			return null;
		}
		// Active Surplus feed-in
		return pvPower;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	protected ModbusProtocol defineModbusProtocol() {
		return new ModbusProtocol(this,
				new FC6WriteRegisterTask(0x0100, 
						m(RctCess.ChannelId.SET_ACTIVE_POWER,
								new SignedWordElement(0x0100), SCALE_FACTOR_2)),
				new FC6WriteRegisterTask(0x0101, 
						m(RctCess.ChannelId.SET_REACTIVE_POWER,
								new SignedWordElement(0x0101), SCALE_FACTOR_2)));
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				SymmetricEss.getModbusSlaveNatureTable(accessMode),
				ManagedSymmetricEss.getModbusSlaveNatureTable(accessMode),
				ModbusSlaveNatureTable.of(RctCess.class, accessMode, 100)
						.build()
		);
	}

	@Override
	public String debugLog() {
		var builder = new StringBuilder(this.stateMachine.debugLog());

		builder.append("|SoC:").append(this.getSoc().asString()) //
				.append("|L:").append(this.getActivePower().asString());

		// For HybridEss show PV production power and actual Battery charge power
		if (this.config.hasDcCharger() && this.getPvPower().isDefined()) {
			builder.append("|PV:").append(this.getPvPower().asString())
					.append("|Battery:").append(this.getDcDischargePower().asString());
		}

		// Show max AC export/import active power:
		// Minimum of MaxAllowedCharge/DischargePower and MaxApparentPower
		builder.append("|Allowed:") //
				.append(TypeUtils.max(//
						this.getAllowedChargePower().get(), TypeUtils.multiply(this.getMaxApparentPower().get(), -1)))
				.append(";") //
				.append(TypeUtils.min(//
						this.getAllowedDischargePower().get(), this.getMaxApparentPower().get()));

		builder.append("|").append(this.getGridModeChannel().value().asOptionString());
		
		return builder.toString();
	}

	@Override
	public String toString() {
		return toStringHelper(this)
				.addValue(this.id())
				.toString();
	}

}
