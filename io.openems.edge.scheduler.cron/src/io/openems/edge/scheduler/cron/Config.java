package io.openems.edge.scheduler.cron;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition( //
		name = "Scheduler Cron", //
		description = "This Scheduler executes specific Controllers based on cron expressions.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "scheduler0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Always Run Before", description = "IDs of Controllers that should be executed _before_ other Controllers in the order of the IDs.")
	String[] alwaysRunBeforeController_ids() default {};

	@AttributeDefinition( //
			name = "Cron Entries", //
			description = "List of cron entries in the format '* * * * *: controller0, controller1'. "
					+ "Use https://crontab.guru/ to create cron expressions. "
					+ "If multiple entries are active at the same time, controllers from all active entries "
					+ "are combined in list order and duplicates are ignored (first occurrence wins).")
	String[] cronEntries() default {};

	@AttributeDefinition(name = "Always Run After", description = "IDs of Controllers that should be executed _after_ other Controllers in the order of the IDs.")
	String[] alwaysRunAfterController_ids() default { "ctrlDebugLog0" };

	String webconsole_configurationFactory_nameHint() default "Scheduler Cron [{id}]";
}
