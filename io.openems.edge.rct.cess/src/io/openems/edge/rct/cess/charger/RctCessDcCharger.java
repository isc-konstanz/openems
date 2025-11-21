package io.openems.edge.rct.cess.charger;

import org.osgi.service.event.EventHandler;

import io.openems.edge.common.channel.Doc;
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

}
