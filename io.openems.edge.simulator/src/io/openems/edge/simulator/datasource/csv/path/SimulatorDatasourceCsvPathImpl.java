package io.openems.edge.simulator.datasource.csv.path;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.simulator.CsvIndexDataContainer;
import io.openems.edge.simulator.datasource.api.AbstractDatasource;
import io.openems.edge.simulator.datasource.api.SimulatorDatasource;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Simulator.Datasource.CSV.Path", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_WRITE //
})
public class SimulatorDatasourceCsvPathImpl extends AbstractDatasource
		implements SimulatorDatasourceCsvPath, SimulatorDatasource, OpenemsComponent, EventHandler {

	private static final int TIME_DELTA_DEFAULT = Integer.MAX_VALUE;

	private final Logger log = LoggerFactory.getLogger(SimulatorDatasourceCsvPathImpl.class);

	@Reference
	private ComponentManager componentManager;

	private Config config;

	public SimulatorDatasourceCsvPathImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				SimulatorDatasourceCsvPath.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) throws NumberFormatException, IOException {
		var now = ZonedDateTime.now(this.getComponentManager().getClock());
		this.config = config;
		super.activate(context, config.id(), config.alias(), config.enabled(), TIME_DELTA_DEFAULT);
		this.getData().initialize(now);
		this.handleNextTimeDelta(now);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	protected void handleNextRecord(ZonedDateTime now) {
		super.handleNextRecord(now);
		this.handleNextTimeDelta(now);
	}

	protected void handleNextTimeDelta(ZonedDateTime now) {
		var nextTime = this.getData().getCurrentIndex();
		this._setTimeDelta((int) Duration.between(now, nextTime).getSeconds() + 1);
		this.logDebug(log, "Set time delta for next index '" + nextTime + "': " + this.getTimeDelta());
	}

	@Override
	protected ComponentManager getComponentManager() {
		return this.componentManager;
	}

	@Override
	protected CsvIndexDataContainer getData() {
		return (CsvIndexDataContainer) super.getData();
	}

	@Override
	protected CsvIndexDataContainer readData() throws NumberFormatException, IOException {
		return CsvIndexDataContainer.readFile(new File(this.config.source()), 
				this.config.index(), this.config.format(), this.config.factor());
	}
}
