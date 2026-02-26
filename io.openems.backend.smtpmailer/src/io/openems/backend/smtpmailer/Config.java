package io.openems.backend.smtpmailer;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.types.DebugMode;

@ObjectClassDefinition(
		name = "Mailer.Smtp",
		description = "Configures the SMTP Mailer")
public @interface Config {

	@AttributeDefinition( name = "SMTP Host", description = "Hostname of the SMTP server")
	String smtpHost() default "localhost";

	@AttributeDefinition( name = "SMTP Port", description = "Port of the SMTP server (25, 465, 587)")
	int smtpPort() default 587;

	@AttributeDefinition( name = "Use STARTTLS", description = "Enable STARTTLS (recommended for port 587)")
	boolean useStartTls() default true;

	@AttributeDefinition( name = "Use SSL (SMTPS)", description = "Enable implicit SSL (usually port 465)")
	boolean useSsl() default false;

	@AttributeDefinition( name = "SMTP Authentication", description = "Enable SMTP authentication")
	boolean useAuthentication() default true;

	@AttributeDefinition( name = "SMTP Username", description = "Username for SMTP authentication")
	String username();

	@AttributeDefinition( name = "SMTP Password", description = "Password for SMTP authentication")
	String password();

	@AttributeDefinition( name = "From Address", description = "Default sender email address")
	String fromAddress();

	@AttributeDefinition( name = "Connection Timeout (ms)", description = "SMTP connection timeout in milliseconds")
	int connectionTimeout() default 10000;

	@AttributeDefinition( name = "Read Timeout (ms)", description = "SMTP read timeout in milliseconds")
	int readTimeout() default 10000;

	@AttributeDefinition( name = "Write Timeout (ms)", description = "SMTP write timeout in milliseconds")
	int writeTimeout() default 10000;

	@AttributeDefinition( name = "Mail Thread Pool Size", description = "Number of threads dedicated to sending emails")
	int mailPoolSize() default 2;

	@AttributeDefinition( name = "Max Retry Attempts", description = "Number of retry attempts if sending fails")
	int maxRetries() default 3;

	@AttributeDefinition( name = "Retry Delay (ms)", description = "Delay between retry attempts in milliseconds")
	int retryDelay() default 3000;

	@AttributeDefinition( name = "Debug Mode", description = "Activates the debug mode")
	DebugMode debugMode() default DebugMode.OFF;

	String webconsole_configurationFactory_nameHint() default "Mailer.Smtp";
}