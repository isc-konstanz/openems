package io.openems.edge.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CsvDataContainer extends DataContainer {

	/**
	 * Reads a CSV file from a JAR file.
	 *
	 * @param clazz     a class in the same java package as the file
	 * @param filename  the name of the file in the java package
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvDataContainer readResource(Class<?> clazz, String filename,
			CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		try (var br = new BufferedReader(new InputStreamReader(clazz.getResourceAsStream(filename)))) {
			return readLines(br.lines().toArray(s -> new String[s]), new CsvDataContainer(csvFormat, factor));
		}
	}

	/**
	 * Reads a CSV file.
	 *
	 * @param path      the path + filename of the CSV file
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvDataContainer readFile(File path,
			CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		try (var br = new BufferedReader(new FileReader(path))) {
			return readLines(br.lines().toArray(s -> new String[s]), new CsvDataContainer(csvFormat, factor));
		}
	}

	/**
	 * Reads a CSV file.
	 *
	 * @param csv       the CSV content
	 * @param csvFormat the CSV-Format
	 * @param factor    a multiplication factor to apply on the read number
	 * @return a {@link DataContainer}
	 * @throws IOException           on error
	 * @throws NumberFormatException on error
	 */
	public static CsvDataContainer read(String csv,
			CsvFormat csvFormat, float factor) throws NumberFormatException, IOException {
		return readLines(csv.split("\\r?\\n"), new CsvDataContainer(csvFormat, factor));
	}

	protected static  <C extends CsvDataContainer> C readLines(String[] lines, C data)
			throws NumberFormatException, IOException {
		var isTitleLine = true;
		for (String line : lines) {
			if (isTitleLine) {
				isTitleLine = false;
				if (!isNumeric(line)) {
					data.addHeader(line);
					continue;
				}
			}
			data.addLine(line);
		}
		return data;
	}

	protected CsvFormat format;
	protected float factor;

	public CsvDataContainer(CsvFormat format, float factor) {
		this.format = format;
		this.factor = factor;
	}

	public void addHeader(String line) {
		this.setKeys(line.split(this.format.lineSeparator));
	}

	public void addLine(String line) {
		var values = line.split(this.format.lineSeparator);
		this.addRecord(parseFloat(values));
	}

	protected Float[] parseFloat(String[] values) {
		var floatValues = new Float[values.length];
		for (var i = 0; i < values.length; i++) {
			var value = values[i];
			if (value == null || value.isEmpty()) {
				value = null;
			} else {
				if (this.format.decimalSeparator != ".") {
					value = value.replace(this.format.decimalSeparator, ".");
				}
				floatValues[i] = Float.parseFloat(value) * this.factor;
			}
		}
		return floatValues;
	}

	/**
	 * Returns true if the given value is a number.
	 *
	 * @param strNum a value to be evaluated
	 * @return true for numbers
	 */
	protected static boolean isNumeric(String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			Float.parseFloat(strNum);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

}
