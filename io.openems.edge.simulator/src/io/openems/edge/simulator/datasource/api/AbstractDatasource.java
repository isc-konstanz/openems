package io.openems.edge.simulator.datasource.api;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import io.openems.common.types.ChannelAddress;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.type.TypeUtils;
import io.openems.edge.simulator.DataContainer;

public abstract class AbstractDatasource extends AbstractOpenemsComponent
		implements SimulatorDatasource, EventHandler {

	private int timeDelta;
	private LocalDateTime lastIteration = LocalDateTime.MIN;
	private DataContainer data;

	protected abstract ComponentManager getComponentManager();

	protected abstract DataContainer readData() throws NumberFormatException, IOException;

	protected AbstractDatasource(io.openems.edge.common.channel.ChannelId[] firstInitialChannelIds,
			io.openems.edge.common.channel.ChannelId[]... furtherInitialChannelIds) {
		super(firstInitialChannelIds, furtherInitialChannelIds);
	}

	protected void activate(ComponentContext context, String id, String alias, boolean enabled, int timeDelta)
			throws NumberFormatException, IOException {
		var now = ZonedDateTime.now(this.getComponentManager().getClock());
		super.activate(context, id, alias, enabled);
		this.lastIteration = now.toLocalDateTime();
		this.timeDelta = timeDelta;
		this.data = this.readData();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		switch (event.getTopic()) {
		case EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE:
			var now = ZonedDateTime.now(this.getComponentManager().getClock());
			if (this.timeDelta > 0 && Duration.between(this.lastIteration, now).getSeconds() < this.timeDelta) {
				// don't change record, if timeDelta is active and has not been passed yet
				return;
			}
			this.lastIteration = now.toLocalDateTime();
			this.handleNextRecord(now);
			break;
		}
	}

	protected void handleNextRecord(ZonedDateTime now) {
		this.getData().nextRecord();
	}

	protected DataContainer getData() {
		return this.data;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<T> getValues(OpenemsType type, ChannelAddress channelAddress) {
		// First: try full ChannelAddress
		var values = this.getData().getValues(channelAddress.toString());
		if (values.isEmpty()) {
			// Not found: try Channel-ID only (without Component-ID)
			values = this.getData().getValues(channelAddress.getChannelId());
		}
		return values.stream() //
				.map(v -> (T) TypeUtils.getAsType(type, v)) //
				.toList();
	}

	@Override
	public <T> T getValue(OpenemsType type, ChannelAddress channelAddress) {
		// First: try full ChannelAddress
		var valueOpt = this.getData().getValue(channelAddress.toString());
		if (!valueOpt.isPresent()) {
			// Not found: try Channel-ID only (without Component-ID)
			valueOpt = this.getData().getValue(channelAddress.getChannelId());
		}
		return TypeUtils.getAsType(type, valueOpt);
	}

	@Override
	public Set<String> getKeys() {
		return this.getData().getKeys();
	}

	@Override
	public int getTimeDelta() {
		return this.timeDelta;
	}

	protected void _setTimeDelta(int timeDelta) {
		this.timeDelta = timeDelta;
	}
}
