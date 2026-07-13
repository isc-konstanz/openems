package io.openems.edge.edge2edge.websocket.pvinverter;

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

import com.google.gson.JsonPrimitive;

import io.openems.common.channel.AccessMode;
import io.openems.common.types.MeterType;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.edge2edge.websocket.Edge2EdgeWebsocket;
import io.openems.edge.edge2edge.websocket.bridge.Edge2EdgeWebsocketBridge;
import io.openems.edge.edge2edge.websocket.AbstractEdge2EdgeWebsocket;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Edge2Edge.Websocket.PV-Inverter", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class Edge2EdgeWebsocketPvInverterImpl extends AbstractEdge2EdgeWebsocket implements
		ManagedSymmetricPvInverter, ElectricityMeter, Edge2EdgeWebsocketPvInverter, Edge2EdgeWebsocket, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	private Config config;

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

	public Edge2EdgeWebsocketPvInverterImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(),
				ManagedSymmetricPvInverter.ChannelId.values(),
				StartStoppable.ChannelId.values(), //
				Edge2EdgeWebsocket.ChannelId.values(), //
				Edge2EdgeWebsocketPvInverter.ChannelId.values() //
		);
		this._setMaxApparentPower(Integer.MAX_VALUE); // has no effect, as long as AllowedCharge/DischargePower are null

		this.getActivePowerLimitChannel().onSetNextWrite(t -> {
			if (this.config.remoteAccessMode() == AccessMode.READ_ONLY) {
				return;
			}

			this.bridgeStateHandler.setChannelValue(ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT.id(),
					new JsonPrimitive(t));
		});
	}

	@Activate
	protected void activate(ComponentContext context, Config config) {
		this.activate(context, config.id(), config.alias(), config.enabled(), this.cm, config.bridge_id(),
				config.remoteComponentId());
		this.config = config;
	}

	@Deactivate
	@Override
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public MeterType getMeterType() {
		return MeterType.PRODUCTION;
	}

	@Override
	public String debugLog() {
		return "L:" + this.getActivePower().asString();
	}

}
