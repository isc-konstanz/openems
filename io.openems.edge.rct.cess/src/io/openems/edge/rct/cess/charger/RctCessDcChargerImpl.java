package io.openems.edge.rct.cess.charger;

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
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.modbusslave.ModbusSlaveNatureTable;
import io.openems.edge.common.modbusslave.ModbusSlaveTable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.rct.cess.RctCess;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(
		name = "RCT.CESS.200.PV",
		immediate = true,
		configurationPolicy = ConfigurationPolicy.REQUIRE
)
@EventTopics({
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE
})
public class RctCessDcChargerImpl extends AbstractOpenemsComponent implements RctCessDcCharger,
		EssDcCharger, OpenemsComponent, ModbusSlave, EventHandler, TimedataProvider {

	private final CalculateEnergyFromPower calculateActualEnergy = new CalculateEnergyFromPower(this,
			EssDcCharger.ChannelId.ACTUAL_ENERGY);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata = null;

	private volatile RctCess ess = null;

	@Override
	public void bindEss(RctCess ess) {
		this.ess = ess;
	}

	@Override
	public void unbindEss() {
		this.ess = null;
	}

	public RctCessDcChargerImpl() {
		super(
				OpenemsComponent.ChannelId.values(),
				ModbusComponent.ChannelId.values(),
				EssDcCharger.ChannelId.values(),
				RctCessDcCharger.ChannelId.values()
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws OpenemsException {
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void handleEvent(Event event) {
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE:
			this.calculatePower();
			this.calculateEnergy();
			break;
		}
	}

	private void calculatePower() {
		if (this.ess == null) {
			return;
		}
		var inverter = this.ess.getBatteryInverter();
		if (inverter == null || !inverter.getDcPower().isDefined()) {
			return;
		}
		var battery = this.ess.getBattery();
		if (battery == null || !battery.getPower().isDefined() || !battery.getCurrent().isDefined()) {
			return;
		}
		var batteryPower = TypeUtils.multiply(this.getVoltage().get(), this.getCurrent().get());

		var dcPower = inverter.getDcPower().get();
		var pvPower = Math.max(TypeUtils.subtract(dcPower, batteryPower), 0);
		var pvRatio = pvPower / dcPower;

		this._setActualPower(pvPower);
		this._setCurrent(TypeUtils.multiply(inverter.getDcCurrent().get(), 1000, pvRatio));
		this._setVoltage(TypeUtils.multiply(inverter.getDcVoltage().get(), 1000));
	}

	/**
	 * Calculate the Energy values from ActualPower.
	 */
	private void calculateEnergy() {
		var actualPower = this.getActualPower().get();
		if (actualPower == null) {
			// Not available
			this.calculateActualEnergy.update(null);
		} else if (actualPower > 0) {
			this.calculateActualEnergy.update(actualPower);
		} else {
			this.calculateActualEnergy.update(0);
		}
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public ModbusSlaveTable getModbusSlaveTable(AccessMode accessMode) {
		return new ModbusSlaveTable(
				OpenemsComponent.getModbusSlaveNatureTable(accessMode),
				EssDcCharger.getModbusSlaveNatureTable(accessMode),
				ModbusSlaveNatureTable.of(RctCessDcCharger.class, accessMode, 100)
						.build());
	}

	@Override
	public String debugLog() {
		return "P:" + this.getActualPower().asString();
	}

}
