package io.openems.edge.scheduler.cron;

import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;

import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.scheduler.api.Scheduler;
import io.openems.edge.scheduler.cron.Utils.CronEntry;

/**
 * This Scheduler returns Controllers from all matching cron entries,
 * together with configured always-before and always-after Controllers.
 *
 * <p>
 * Each entry maps a 5-field cron expression to a list of controller IDs:
 *
 * <pre>
 * * * * * *: controller0, controller1
 * </pre>
 *
 * If more than one entry is active at the same time, controller IDs from all
 * active entries are combined in configuration order. Duplicates are ignored,
 * i.e. the first occurrence wins.
 */
@Designate(ocd = Config.class, factory = true)
@Component( //
		name = "Scheduler.Cron", //
		immediate = true, //
		configurationPolicy = REQUIRE)
public class SchedulerCronImpl extends AbstractOpenemsComponent
		implements SchedulerCron, Scheduler, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(SchedulerCronImpl.class);

	private Config config = null;
	private List<CronEntry> cronEntries = List.of();

	@Reference
	private ComponentManager componentManager;

	public SchedulerCronImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Scheduler.ChannelId.values(), //
				SchedulerCron.ChannelId.values());
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		this.applyConfig(config);
		super.activate(context, config.id(), config.alias(), config.enabled());
	}

	@Modified
	private void modified(ComponentContext context, Config config) throws OpenemsNamedException {
		this.applyConfig(config);
		super.modified(context, config.id(), config.alias(), config.enabled());
	}

	private void applyConfig(Config config) {
		this.config = config;
		this.cronEntries = config.enabled() //
				? Utils.parseCronEntries(config.cronEntries()) //
				: List.of();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public synchronized LinkedHashSet<String> getControllers() {
		var result = new LinkedHashSet<String>();

		// Add "Always Run Before" Controllers
		this.addControllersById(result, this.config.alwaysRunBeforeController_ids());

		// Evaluate which cron entries are currently active
		var now = ZonedDateTime.now(this.componentManager.getClock());

		var activeEntries = this.cronEntries.stream() //
				.filter(e -> e.isActive(now)) //
				.toList();

		if (activeEntries.size() > 1 && this.log.isDebugEnabled()) {
			this.log.debug("[{}] Multiple cron entries are active simultaneously. Active expressions: {}", //
					this.id(), //
					activeEntries.stream().map(CronEntry::cronExpression).toList());
		}

		for (var activeEntry : activeEntries) {
			for (var controllerId : activeEntry.controllerIds()) {
				this.addControllerById(result, controllerId);
			}
		}

		// Add "Always Run After" Controllers
		this.addControllersById(result, this.config.alwaysRunAfterController_ids());

		return result;
	}

	private void addControllersById(LinkedHashSet<String> result, String[] controllerIds) {
		for (var controllerId : controllerIds) {
			this.addControllerById(result, controllerId);
		}
	}

	private void addControllerById(LinkedHashSet<String> result, String controllerId) {
		if (!controllerId.isEmpty()) {
			result.add(controllerId);
		}
	}
}
