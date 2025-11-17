package io.openems.edge.edge2edge.websocket.pvinverter;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.channel.AccessMode;

@ObjectClassDefinition(//
		name = "Edge-2-Edge PV-Inverter Websocket", //
		description = "Connects a PV inverter from a slave OpenEMS Edge via Websocket")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "pvInverter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Remote Access-Mode", description = "Access-Mode to the Component at the slave OpenEMS Edge?")
	AccessMode remoteAccessMode() default AccessMode.READ_ONLY;

	@AttributeDefinition(name = "Remote Component-ID", description = "Component-ID of ESS at the slave OpenEMS Edge.")
	String remoteComponentId() default "pvInverter0";

	@AttributeDefinition(name = "Bridge-ID")
	String bridge_id() default "bridge0";

	@AttributeDefinition(name = "Bridge-Target")
	String Bridge_target();

	String webconsole_configurationFactory_nameHint() default "Edge-2-Edge PV-Inverter Websocket [{id}]";

}