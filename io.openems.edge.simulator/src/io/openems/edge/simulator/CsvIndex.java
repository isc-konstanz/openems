package io.openems.edge.simulator;

public enum CsvIndex {
	/**
	 * Always use the next line.
	 */
	LINE,

	/**
	 * Find the line closest to the current unix timestamp.
	 */
	UNIXTIMESTAMP,

	/**
	 * Find the line closest to the current month, day, hours, minutes and seconds. 
	 * Values must be in the ISO format "[yyyy-]MM-dd[T| ]HH:mm:ssZ", where the year will be ignored.
	 */
	YYYYMMDD_HHMMSS,

	/**
	 * Find the line closest to the current hours, minutes and seconds.
	 * Values must be in the format: HH:mm:ss.
	 */
	HHMMSS
}
