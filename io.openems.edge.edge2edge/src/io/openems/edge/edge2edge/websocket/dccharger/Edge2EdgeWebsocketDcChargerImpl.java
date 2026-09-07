package io.openems.edge.edge2edge.websocket.dccharger;

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
import org.osgi.service.metatype.annotations.Designate;

import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.edge2edge.websocket.Edge2EdgeWebsocket;
import io.openems.edge.edge2edge.websocket.bridge.Edge2EdgeWebsocketBridge;
import io.openems.edge.edge2edge.websocket.AbstractEdge2EdgeWebsocket;
import io.openems.edge.ess.dccharger.api.EssDcCharger;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Edge2Edge.Websocket.DcCharger", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class Edge2EdgeWebsocketDcChargerImpl extends AbstractEdge2EdgeWebsocket implements
		EssDcCharger, Edge2EdgeWebsocketDcCharger, Edge2EdgeWebsocket, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	/**
	 * Binds the {@link Edge2EdgeWebsocketBridge}.
	 *
	 * @param bridge the bridge to bind
	 */
	@Reference(policy = ReferencePolicy.DYNAMIC, //
			policyOption = ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.OPTIONAL)
	@Override
	public void bindBridge(Edge2EdgeWebsocketBridge bridge) {
		super.bindBridge(bridge);
	}

	/**
	 * Unbinds the {@link Edge2EdgeWebsocketBridge}.
	 *
	 * @param bridge the bridge to unbind
	 */
	@Override
	public void unbindBridge(Edge2EdgeWebsocketBridge bridge) {
		super.unbindBridge(bridge);
	}

	public Edge2EdgeWebsocketDcChargerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				EssDcCharger.ChannelId.values(), //
				Edge2EdgeWebsocketDcCharger.ChannelId.values(), //
				Edge2EdgeWebsocket.ChannelId.values() //
		);
	}

	@Activate
	protected void activate(ComponentContext context, Config config) {
		this.activate(context, config.id(), config.alias(), config.enabled(), this.cm, config.bridge_id(),
				config.remoteComponentId());
	}

	@Deactivate
	@Override
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public String debugLog() {
		return "P:" + this.getActualPower().asString();
	}

}
