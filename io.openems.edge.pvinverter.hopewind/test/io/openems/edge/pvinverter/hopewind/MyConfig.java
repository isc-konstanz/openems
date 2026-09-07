package io.openems.edge.pvinverter.hopewind;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.common.startstop.StartStopConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = null;
		private String modbusId = null;
		private int modbusUnitId;
		private boolean watchdog = true;
		private int watchdogTimer = PvInverterHopewind.WATCH_DOG_TIMER_DEFAULT;
		private int watchdogShutdownTimer = PvInverterHopewind.WATCH_DOG_SHUTDOWN_TIMER_DEFAULT;
		private StartStopConfig startStop = StartStopConfig.START;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setModbusId(String modbusId) {
			this.modbusId = modbusId;
			return this;
		}

		public Builder setModbusUnitId(int modbusUnitId) {
			this.modbusUnitId = modbusUnitId;
			return this;
		}

		public Builder setWatchdog(boolean watchdog) {
			this.watchdog = watchdog;
			return this;
		}

		public Builder setWatchdogTimer(int watchdogTimer) {
			this.watchdogTimer = watchdogTimer;
			return this;
		}

		public Builder setWatchdogShutdownTimer(int watchdogShutdownTimer) {
			this.watchdogShutdownTimer = watchdogShutdownTimer;
			return this;
		}

		public Builder setStartStop(StartStopConfig startStop) {
			this.startStop = startStop;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public boolean watchdog() {
		return this.builder.watchdog;
	}

	@Override
	public int watchdogTimer() {
		return this.builder.watchdogTimer;
	}

	@Override
	public int watchdogShutdownTimer() {
		return this.builder.watchdogShutdownTimer;
	}

	@Override
	public StartStopConfig startStop() {
		return this.builder.startStop;
	}

	@Override
	public String modbus_id() {
		return this.builder.modbusId;
	}

	@Override
	public int modbusUnitId() {
		return this.builder.modbusUnitId;
	}

	@Override
	public String Modbus_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.modbus_id());
	}
}
