package io.openems.edge.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CsvIndexDataContainer extends CsvDataContainer {
	public static final String TIMESTAMP = "Timestamp";

	/**
	 * Reads a CSV file from a JAR file.
	 *
	 * @param clazz     a class in the same java package as the file
	 * @param filename  the name of the file in the java package
	 * @param csvIndex  the CSV-Index mode
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvIndexDataContainer readResource(Class<?> clazz, String filename,
			CsvIndex csvIndex, CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		try (var br = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(filename)))) {
			return readLines(br.lines().toArray(s -> new String[s]), new CsvIndexDataContainer(csvIndex, csvFormat, factor));
		}
	}

	/**
	 * Reads a CSV file.
	 *
	 * @param path      the path + filename of the CSV file
	 * @param csvIndex  the CSV-Index mode
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvIndexDataContainer readFile(File path,
			CsvIndex csvIndex, CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		try (var br = new BufferedReader(new FileReader(path))) {
			return readLines(br.lines().toArray(s -> new String[s]), new CsvIndexDataContainer(csvIndex, csvFormat, factor));
		}
	}

	/**
	 * Reads a CSV file.
	 *
	 * @param csv       the CSV content
	 * @param csvIndex  the CSV-Index mode
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvIndexDataContainer read(String csv,
			CsvIndex csvIndex, CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		return readLines(csv.split("\\r?\\n"), new CsvIndexDataContainer(csvIndex, csvFormat, factor));
	}

	private final DateTimeFormatter[] formatters;
	private final List<ZonedDateTime> index = new ArrayList<>();

	private final CsvIndex indexMode;
	private int indexNum = -1;

	public CsvIndexDataContainer(CsvIndex index, CsvFormat format, float factor) {
		super(format, factor);
		this.formatters = new DateTimeFormatter[] {
		        new DateTimeFormatterBuilder()
		                .parseCaseInsensitive()
		        		.appendPattern("yyyy-MM-dd'T'HH:mm:ss")
		                .parseLenient()
		                .appendOffsetId()
		                .parseStrict()
		                .toFormatter(),
		        new DateTimeFormatterBuilder()
		        		.parseCaseInsensitive()
		        		.appendPattern("yyyy-MM-dd HH:mm:ss")
		                .parseLenient()
		                .appendOffsetId()
		                .parseStrict()
		                .toFormatter(),
		        new DateTimeFormatterBuilder()
						.parseCaseInsensitive()
		        		.appendPattern("MM-dd'T'HH:mm:ss")
		        		.parseDefaulting(ChronoField.YEAR, Year.now().getValue())
		                .appendOffsetId()
		                .parseStrict()
		                .toFormatter(),
		        new DateTimeFormatterBuilder()
						.parseCaseInsensitive()
		        		.appendPattern("MM-dd HH:mm:ss")
		        		.parseDefaulting(ChronoField.YEAR, Year.now().getValue())
		                .appendOffsetId()
		                .parseStrict()
		                .toFormatter()
		};
		this.indexMode = index;
	}

	public void addHeader(String line) {
		var keys = Stream.of(line.split(this.format.lineSeparator)).collect(Collectors.toCollection(ArrayList::new));
		this.indexNum = keys.indexOf(TIMESTAMP);
		if (this.indexNum < 0 && this.indexMode != CsvIndex.LINE) {
            throw new IllegalArgumentException("Invalid index mode for missing column 'Timestamp': " + this.indexMode);
		}
		keys.remove(this.indexNum);
		this.setKeys(keys.toArray(s -> new String[s]));
	}

	public void addLine(String line) {
		var values = Stream.of(line.split(this.format.lineSeparator)).collect(Collectors.toCollection(ArrayList::new));
		var index = values.remove(this.indexNum);
		var record = parseFloat(values.toArray(s -> new String[s]));
		this.addIndex(index);
		this.addRecord(record);
	}

	public void addIndex(String index) {
	    index = index == null ? "" : index.trim();
        switch (this.indexMode) {
            case UNIXTIMESTAMP:
                long value;
                try {
                    value = Long.parseLong(index);

                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid UNIX timestamp: " + index, e);
                }
                Instant instant;
                // values >= 10^12 are milliseconds, otherwise seconds
                if (Math.abs(value) > 1_000_000_000_000L) {
                    instant = Instant.ofEpochMilli(value);
                } else {
                    instant = Instant.ofEpochSecond(value);
                }
                this.index.add(ZonedDateTime.ofInstant(instant, ZoneOffset.UTC));
                break;
            case YYYYMMDD_HHMMSS:
                if (index.isEmpty()) {
                    throw new IllegalArgumentException("YYYYMMDD_HHMMSS requires a datetime value");
                }
                OffsetDateTime timestamp = parseOffsetDateTime(index);
                this.index.add(timestamp.toZonedDateTime());
                break;
            case HHMMSS:
                if (index.isEmpty()) {
                    throw new IllegalArgumentException("HHMMSS requires a time value like HH:mm:ss");
                }
                LocalTime time;
                try {
                    time = LocalTime.parse(index, DateTimeFormatter.ISO_TIME);
                    
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid HH:mm:ss time: " + index, e);
                }
                LocalDate today = LocalDate.now();

                this.index.add(ZonedDateTime.of(today, time, ZoneId.systemDefault()));
                break;
            case LINE:
            default:
                break;
        }
	}

	private OffsetDateTime parseOffsetDateTime(String value) {
	    for (DateTimeFormatter formatter : this.formatters) {
	        try {
	            return OffsetDateTime.parse(value, formatter);

	        } catch (DateTimeParseException e) {
	            // Try next
	        }
	    }
	    throw new IllegalArgumentException("Cannot parse datetime (expected [yyyy-]MM-dd[T| ]HH:mm:ssZ): " + value);
	}

	public List<ZonedDateTime> getIndex() {
		return this.index;
	}

	/**
	 * Gets the current index.
	 *
	 * @return the current index
	 */
	public ZonedDateTime getCurrentIndex() {
		if (this.currentIndex == -1) {
			this.currentIndex = 0;
		}
		return this.index.get(this.currentIndex);
	}

	/**
	 * Switch to the next row of values.
	 */
	public void nextRecord(ZonedDateTime now) {
		this.currentIndex++;
		if (this.currentIndex >= this.records.size()) {
			this.rewind(now);
		}
	}

	/**
	 * Rewinds the data to start again at the first record.
	 */
	public void rewind(ZonedDateTime now) {
		this.initialize(now);
	}

	public void initialize(ZonedDateTime target) {
		ZonedDateTime result = this.index.get(0);
		long resultDelta = Long.MAX_VALUE;

		int targetYear = target.getYear();
		boolean targetLeap = Year.isLeap(targetYear);

		for (ZonedDateTime index : this.index) {
			long indexDelta = Math.abs(Duration.between(target, index).getSeconds());
			int month = index.getMonthValue();
			int day = Math.min(index.getDayOfMonth(), Month.of(month).length(targetLeap));

			ZonedDateTime candidate = ZonedDateTime.of(
					targetYear,
					month,
					day,
					index.getHour(),
					index.getMinute(),
					index.getSecond(),
					index.getNano(),
					target.getZone());

			long candidateDelta = Math.abs(Duration.between(target, candidate).getSeconds()) + indexDelta;
			if (candidateDelta < resultDelta) {
				resultDelta = candidateDelta;
				result = index;
			}
		}
		int resultYearDelta = targetYear - result.getYear();
		if (resultYearDelta != 0) {
			result = result.plusYears(resultYearDelta);
			IntStream.range(0, this.index.size()) //
					.forEach(i -> index.set(i, index.get(i).plusYears(resultYearDelta)));
		}
		this.currentIndex = this.index.indexOf(result);

		while (Duration.between(target, this.getCurrentIndex()).getSeconds() < 0) {
			this.currentIndex++;
		}
	}

}
