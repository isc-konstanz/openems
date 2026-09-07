package io.openems.edge.scheduler.cron;

import static com.cronutils.model.CronType.UNIX;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.cronutils.model.time.ExecutionTime;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

public class Utils {

	private Utils() {
	}

	private static final CronParser CRON_PARSER = new CronParser(
			CronDefinitionBuilder.instanceDefinitionFor(UNIX));

	/**
	 * Represents a parsed cron entry with a pre-compiled execution schedule and
	 * associated controller IDs.
	 *
	 * <p>
	 * Entry format: {@code * * * * *: controller0, controller1}
	 * <p>
	 * Cron fields (space-separated): minute hour day-of-month month day-of-week
	 */
	public record CronEntry(String cronExpression, ExecutionTime executionTime, List<String> controllerIds) {

		/**
		 * Parses a cron entry string.
		 *
		 * @param entry the entry string, e.g. {@code "0 8 * * 1-5: ctrl0, ctrl1"}
		 * @return the parsed {@link CronEntry}, or {@code null} if the format is
		 *         invalid
		 */
		public static CronEntry parse(String entry) {
			var colonIdx = entry.indexOf(':');
			if (colonIdx < 0) {
				return null;
			}
			var cronExpr = entry.substring(0, colonIdx).trim();
			var controllersStr = entry.substring(colonIdx + 1).trim();
			var controllerIds = Arrays.stream(controllersStr.split(",")) //
					.map(String::trim) //
					.filter(s -> !s.isEmpty()) //
					.collect(Collectors.toList());
			try {
				var executionTime = ExecutionTime.forCron(CRON_PARSER.parse(cronExpr));
				return new CronEntry(cronExpr, executionTime, controllerIds);
			} catch (Exception e) {
				return null;
			}
		}

		/**
		 * Returns {@code true} if this entry's cron expression matches the given time.
		 *
		 * @param now the current zoned date/time
		 * @return true if active
		 */
		public boolean isActive(ZonedDateTime now) {
			return this.executionTime.isMatch(now);
		}
	}

	/**
	 * Parses all cron entry strings from the config, skipping blank or invalid
	 * entries.
	 *
	 * @param entries raw config strings
	 * @return list of parsed {@link CronEntry} objects in original order
	 */
	public static List<CronEntry> parseCronEntries(String[] entries) {
		var result = new ArrayList<CronEntry>();
		for (var entry : entries) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			var parsed = CronEntry.parse(entry);
			if (parsed != null) {
				result.add(parsed);
			}
		}
		return result;
	}
}
