package io.openems.edge.edge2edge.websocket.ess;

import static io.openems.common.utils.IntUtils.maxInteger;
import static io.openems.common.utils.IntUtils.minInteger;

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
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsRuntimeException;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.edge2edge.websocket.AbstractEdge2EdgeWebsocket;
import io.openems.edge.edge2edge.websocket.Edge2EdgeWebsocket;
import io.openems.edge.edge2edge.websocket.bridge.Edge2EdgeWebsocketBridge;
import io.openems.edge.ess.api.AsymmetricEss;
import io.openems.edge.ess.api.ManagedSymmetricEss;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.power.api.Power;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Edge2Edge.Websocket.Ess", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class Edge2EdgeWebsocketEssImpl extends AbstractEdge2EdgeWebsocket implements ManagedSymmetricEss,
		AsymmetricEss, SymmetricEss, Edge2EdgeWebsocketEss, Edge2EdgeWebsocket, OpenemsComponent {

	@Reference
	private ConfigurationAdmin cm;

	@Reference(cardinality = ReferenceCardinality.OPTIONAL, policyOption = ReferencePolicyOption.GREEDY, policy = ReferencePolicy.DYNAMIC)
	private volatile Power power;

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

	public Edge2EdgeWebsocketEssImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SymmetricEss.ChannelId.values(), //
				AsymmetricEss.ChannelId.values(), //
				ManagedSymmetricEss.ChannelId.values(), //
				StartStoppable.ChannelId.values(), //
				Edge2EdgeWebsocket.ChannelId.values(), //
				Edge2EdgeWebsocketEss.ChannelId.values() //
		);
		this._setMaxApparentPower(Integer.MAX_VALUE); // has no effect, as long as AllowedCharge/DischargePower are null
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
	public String debugLog() {
		return "SoC:" + this.getSoc().asString() //
				+ "|L:" + this.getActivePower().asString() //
				+ "|Allowed:" //
				+ maxInteger(//
						this.getAllowedChargePower().get(), //
						TypeUtils.multiply(this.getMaxApparentPower().get(), -1))
				+ ";" //
				+ minInteger(//
						this.getAllowedDischargePower().get(), //
						this.getMaxApparentPower().get()) //
				+ "|" + this.getGridModeChannel().value().asOptionString();
	}

	@Override
	public Power getPower() {
		final var power = this.power;
		if (power == null) {
			throw new OpenemsRuntimeException("Ess.Power is not yet available");
		}
		return power;
	}

	@Override
	public void applyPower(int activePower, int reactivePower) throws OpenemsNamedException {
		if (this.config.remoteAccessMode() == AccessMode.READ_ONLY) {
			return;
		}

		this.bridgeStateHandler.setChannelValue(ManagedSymmetricEss.ChannelId.SET_ACTIVE_POWER_EQUALS.id(),
				new JsonPrimitive(activePower));
		this.bridgeStateHandler.setChannelValue(ManagedSymmetricEss.ChannelId.SET_REACTIVE_POWER_EQUALS.id(),
				new JsonPrimitive(reactivePower));
	}

	@Override
	public int getPowerPrecision() {
		return 1;
	}

}
