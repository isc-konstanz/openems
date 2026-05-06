package io.openems.edge.rct.cess.charger;

import static io.openems.edge.common.type.TypeUtils.subtract;
import static io.openems.common.utils.IntUtils.maxInteger;

import java.util.function.Consumer;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.ess.dccharger.api.EssDcCharger;
import io.openems.edge.rct.cess.RctCess;
import io.openems.edge.timedata.api.TimedataProvider;

public interface RctCessDcCharger extends 
		EssDcCharger, OpenemsComponent, ModbusSlave, EventHandler, TimedataProvider {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	public void bindEss(RctCess ess);

	public void unbindEss();

	public static void calculateActualPowerFromBindings(RctCessDcCharger charger, RctCess ess) {
		var battery = ess.getBattery();
		if (battery == null) {
			return;
		}
		var batteryInverter = ess.getBatteryInverter();
		if (batteryInverter == null) {
			return;
		}

		final Consumer<Value<Integer>> calculatePower = ignore -> {
			var dcPower = batteryInverter.getDcPower().get();
			var batteryPower = battery.getPower().get();
			if (batteryPower == null || dcPower == null) {
				return;
			}
			charger._setActualPower(maxInteger(subtract(dcPower, batteryPower), 0));
		};
		battery.getPowerChannel().onSetNextValue(calculatePower);
		batteryInverter.getDcPowerChannel().onSetNextValue(calculatePower);

		final Consumer<Value<Integer>> calculateVoltageAndCurrent = ignore -> {
			var voltage = batteryInverter.getDcVoltage().get();
			var power = charger.getActualPower().get();
			if (power == null || power == null) {
				return;
			}
			charger._setVoltage(voltage);
			charger._setCurrent((power * 1000) / (voltage / 1000));
		};
		charger.getActualPowerChannel().onSetNextValue(calculateVoltageAndCurrent);
		batteryInverter.getDcVoltageChannel().onSetNextValue(calculateVoltageAndCurrent);
	}

}
