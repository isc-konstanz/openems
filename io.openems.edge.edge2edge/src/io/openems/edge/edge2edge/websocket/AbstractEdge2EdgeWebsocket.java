package io.openems.edge.edge2edge.websocket;

import static java.util.stream.Collectors.toSet;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CaseFormat;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.ChannelCategory;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.function.Disposable;
import io.openems.common.jsonrpc.request.GetChannelsOfComponent.ChannelRecord;
import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.common.channel.ChannelId.ChannelIdImpl;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.edge2edge.websocket.bridge.BridgeComponentStateHandler;
import io.openems.edge.edge2edge.websocket.bridge.ChannelSubscriber;
import io.openems.edge.edge2edge.websocket.bridge.Edge2EdgeWebsocketBridge;

/**
 * Shared base for websocket based Edge-2-Edge Components that map a single
 * remote Component 1:1 onto a local, typed Nature.
 */
public abstract class AbstractEdge2EdgeWebsocket extends AbstractOpenemsComponent implements Edge2EdgeWebsocket {

	private final Logger log = LoggerFactory.getLogger(AbstractEdge2EdgeWebsocket.class);

	protected final ChannelSubscriber channelSubscriber = new ChannelSubscriber();
	protected final BridgeComponentStateHandler bridgeStateHandler = new BridgeComponentStateHandler();

	private volatile String remoteComponentId;
	private Disposable channelSubscriberDisposable;

	protected AbstractEdge2EdgeWebsocket(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);

		this.bridgeStateHandler.addOnStateChangeListener((prev, current) -> {
			switch (current) {
			case NOT_CONNECTED -> {
				this._setRemoteNoComponentFault(false);
				this._setRemoteNoConnection(true);
			}
			case CONNECTED -> {
				this._setRemoteNoComponentFault(false);
				this._setRemoteNoConnection(false);
			}
			case COMPONENT_NOT_AVAILABLE -> {
				this._setRemoteNoComponentFault(true);
				this._setRemoteNoConnection(false);
			}
			}
		});

		this.bridgeStateHandler.addOnSubscribeListener((bridge, channels) -> {
			for (var channel : channels) {
				if (this.hasChannelToBeCreated(channel)) {
					this.createChannel(channel);
				}
			}

			final var c = channels.stream() //
					.filter(t -> t.accessMode() != AccessMode.WRITE_ONLY) //
					.filter(t -> this.hasChannel(t.id()) || this.isChannelInCategory(t.category())) //
					.map(t -> new ChannelAddress(this.remoteComponentId, t.id())) //
					.collect(toSet());

			this.channelSubscriber.setSubscribeChannels(c);
		});

		this.bridgeStateHandler.addOnUnsubscribeListener(bridge -> {
			final var unsubscribedChannels = this.channelSubscriber.unsubscribeAll();

			// reset channel values
			for (var channelAddress : unsubscribedChannels) {
				try {
					final var channel = this.channel(channelAddress.getChannelId());
					channel.setNextValue(null);
				} catch (IllegalArgumentException e) {
					// channel does not exist
				}
			}
		});
	}

	/**
	 * Binds the {@link Edge2EdgeWebsocketBridge}.
	 *
	 * <p>
	 * Subclasses must expose this via their own {@code @Reference}-annotated
	 * bind method that calls {@code super.bindBridge(bridge)}. OSGi Declarative
	 * Services in this codebase always re-declares {@code @Reference} on the
	 * concrete class, rather than relying on inheritance of the annotation.
	 *
	 * @param bridge the bridge to bind
	 */
	protected void bindBridge(Edge2EdgeWebsocketBridge bridge) {
		this.bindStateHandler(bridge);
		this.bindChannels(bridge);
	}

	/**
	 * Unbinds the {@link Edge2EdgeWebsocketBridge}. See {@link #bindBridge}.
	 *
	 * @param bridge the bridge to unbind
	 */
	protected void unbindBridge(Edge2EdgeWebsocketBridge bridge) {
		this.bridgeStateHandler.unbindBridge(bridge);
		this.channelSubscriberDisposable.dispose();
	}

	/**
	 * Registers this Component's {@link BridgeComponentStateHandler} on the
	 * bridge, so it starts tracking connection-/component-state for
	 * {@link #remoteComponentId}.
	 *
	 * @param bridge the bridge to bind
	 */
	private void bindStateHandler(Edge2EdgeWebsocketBridge bridge) {
		this.bridgeStateHandler.bindBridge(bridge);
	}

	/**
	 * Registers this Component's {@link ChannelSubscriber} on the bridge and
	 * forwards every incoming value update onto the matching local Channel.
	 *
	 * @param bridge the bridge to bind
	 */
	private void bindChannels(Edge2EdgeWebsocketBridge bridge) {
		this.channelSubscriberDisposable = bridge.addChannelSubscriber(this.channelSubscriber, t -> {
			for (var entry : t.entrySet()) {
				try {
					final var channel = this.channel(entry.getKey().getChannelId());
					if (channel.getType() == OpenemsType.BOOLEAN) {
						final var value = JsonUtils.getAsType(OpenemsType.INTEGER, entry.getValue());
						channel.setNextValue(value == null ? null : value.equals(1));
						continue;
					}
					channel.setNextValue(JsonUtils.getAsType(channel.getType(), entry.getValue()));
				} catch (IllegalArgumentException e) {
					this.channelSubscriber.unsubscribeChannel(entry.getKey());
				} catch (OpenemsNamedException e) {
					this.log.info("Unable to parse remote channel value.", e);
				}
			}
		});
	}

	/**
	 * Common part of {@code activate()}. Subclasses keep their own
	 * {@code @Activate} method (their generated {@code Config} type differs per
	 * Nature) and delegate to this helper.
	 *
	 * @param context           the {@link ComponentContext}
	 * @param id                the Component-ID
	 * @param alias             the Alias
	 * @param enabled           is the Component enabled?
	 * @param cm                the {@link ConfigurationAdmin}
	 * @param bridgeId          the configured Bridge-ID
	 * @param remoteComponentId the configured remote Component-ID
	 */
	protected final void activate(ComponentContext context, String id, String alias, boolean enabled,
			ConfigurationAdmin cm, String bridgeId, String remoteComponentId) {
		super.activate(context, id, alias, enabled);
		this.remoteComponentId = remoteComponentId;

		OpenemsComponent.updateReferenceFilter(cm, this.servicePid(), "Bridge", bridgeId);

		this.bridgeStateHandler.updateComponentId(enabled ? remoteComponentId : null);
	}

	@Override
	protected void deactivate() {
		super.deactivate();
		this.bridgeStateHandler.deactivate();
	}

	@SuppressWarnings("deprecation")
	protected final boolean hasChannel(String channelId) {
		return this._channel(channelId) != null;
	}

	/**
	 * Decides whether a local Channel should be dynamically created for the
	 * given remote Channel.
	 *
	 * @param channel the remote {@link ChannelRecord}
	 * @return true if a local Channel still needs to be created for it
	 */
	protected boolean hasChannelToBeCreated(ChannelRecord channel) {
		return !this.hasChannel(channel.id()) && this.isChannelInCategory(channel.category());
	}

	/**
	 * Decides whether remote Channels of the given {@link ChannelCategory} are
	 * relevant for this Component.
	 *
	 * <p>
	 * Default: only {@link ChannelCategory#STATE} (i.e. Fault Channels that
	 * aren't already part of a typed Nature - everything else the Nature
	 * already declares is picked up via {@link #hasChannel}).
	 *
	 * @param channelCategory the remote Channel's {@link ChannelCategory}
	 * @return true if Channels of this Category should be created/subscribed
	 */
	protected boolean isChannelInCategory(ChannelCategory channelCategory) {
		return channelCategory == ChannelCategory.STATE;
	}

	private void createChannel(ChannelRecord channel) {
		final var doc = switch (channel.category()) {
		case ENUM -> Doc.of(channel.type());
		case OPENEMS_TYPE -> Doc.of(channel.type()) //
				.unit(channel.unit());
		case STATE -> Doc.of(channel.level());
		};
		final var channelName = CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, channel.id());
		this.addChannel(new ChannelIdImpl(channelName, doc.persistencePriority(channel.persistencePriority()) //
				.accessMode(AccessMode.READ_ONLY) //
				.text(channel.text())));
	}

}
