package io.openems.edge.simulator.datasource.csv.path;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.edge.simulator.CsvFormat;
import io.openems.edge.simulator.CsvIndex;

@ObjectClassDefinition(//
		name = "Simulator DataSource: CSV Path", //
		description = "This service provides CSV-Input data.")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "datasource0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Factor", description = "Each value in the csv-file is multiplied by this factor.")
	float factor() default 10_000;

	@AttributeDefinition(name = "Source", description = "A CSV-Input file path to a file containing a title line and series of indecies and values.")
	String source() default "";

	@AttributeDefinition(name = "Index", description = "The index mode of the CSV file. Index column must be named 'Timestamp'.")
	CsvIndex index() default CsvIndex.YYYYMMDD_HHMMSS;

	@AttributeDefinition(name = "CSV Format", description = "The format of the CSV file")
	CsvFormat format() default CsvFormat.GERMAN_EXCEL;

	String webconsole_configurationFactory_nameHint() default "Simulator DataSource: CSV Path [{id}]";
}
