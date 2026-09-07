package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_MODE;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.HEART_BEAT;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.INVERTER_STATE;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.SHUTDOWN;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.STARTUP;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.STATE_MACHINE;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.WATCHDOG_SHUTDOWN;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.WATCHDOG_TRIPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.startstop.StartStopConfig;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public class PvInverterHopewindImplTest {

	private static ComponentTest prepare(TimeLeapClock clock, MyConfig config) throws Exception {
		return new ComponentTest(new PvInverterHopewindImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addReference("setModbus", new DummyModbusBridge("modbus0")) //
				.activate(config);
	}

	private static MyConfig.Builder myConfig() {
		return MyConfig.create() //
				.setId("pvInverter0") //
				.setModbusId("modbus0") //
				.setModbusUnitId(1);
	}

	/**
	 * Register 40011 mapping as confirmed on the device: 0 = disabled, 1 = actual
	 * power [kW], 2 = proportional power [%]. Writing 0 disables the limit entirely
	 * (field incident 2026-09) - this test guards against inverting the mapping.
	 */
	@Test
	public void testActivePowerLimitModeMatchesRegisterMap() {
		assertEquals(0, ActivePowerLimitMode.DISABLED.getValue());
		assertEquals(1, ActivePowerLimitMode.ACTUAL.getValue());
		assertEquals(2, ActivePowerLimitMode.PROPORTIONAL.getValue());
	}

	/**
	 * The heartbeat is a pure keepalive and must be written every cycle - also
	 * under a forced STOP.
	 */
	@Test
	public void testHeartbeatIsAlwaysSent() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig() //
				.setStartStop(StartStopConfig.STOP) //
				.build()) //
				.next(new TestCase() //
						.output(HEART_BEAT, PvInverterHopewind.HEART_BEAT_DEFAULT)) //
				.next(new TestCase() //
						.output(HEART_BEAT, PvInverterHopewind.HEART_BEAT_DEFAULT)) //
		;
	}

	/**
	 * F8: the watchdog is armed at activation - it trips even if no setpoint was
	 * ever received. F4: the zero-limit is re-asserted every cycle (latched) and
	 * covers both limit registers.
	 */
	@Test
	public void testWatchdogTripsWithoutAnySetpoint() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig().build()) //
				.next(new TestCase() //
						.output(WATCHDOG_TRIPPED, false)) //
				.next(new TestCase() //
						.timeleap(clock, 61, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
				// latched: re-asserted on the next cycle as well
				.next(new TestCase() //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
		;
	}

	/**
	 * Every external setpoint re-arms the watchdog (this is also why ApiWorker
	 * re-writes stack apiTimeout on top of watchdogTimer); a fresh setpoint
	 * releases an active failsafe.
	 */
	@Test
	public void testWatchdogReArmAndRelease() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig().build()) //
				// each cycle writes a setpoint after 59 s -> never trips
				.next(new TestCase() //
						.timeleap(clock, 59, ChronoUnit.SECONDS) //
						.input(ACTIVE_POWER_LIMIT, 30_000) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(ACTIVE_POWER_LIMIT, 30_000)) //
				.next(new TestCase() //
						.timeleap(clock, 59, ChronoUnit.SECONDS) //
						.input(ACTIVE_POWER_LIMIT, 30_000) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(ACTIVE_POWER_LIMIT, 30_000)) //
				// silence -> trip
				.next(new TestCase() //
						.timeleap(clock, 61, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
				// fresh setpoint -> release; setpoint passes through untouched
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT, 20_000) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(ACTIVE_POWER_LIMIT, 20_000)) //
		;
	}

	/**
	 * F6 + stage 2: after watchdogTimer + watchdogShutdownTimer the state machine
	 * commands a real shutdown (register 40201); fresh setpoints recover via
	 * startup (register 40200).
	 */
	@Test
	public void testWatchdogShutdownEscalationAndRecovery() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig() //
				.setWatchdogTimer(60) //
				.setWatchdogShutdownTimer(300) //
				.build()) //
				.next(new TestCase() //
						.input(INVERTER_STATE, InverterState.ON_GRID)) //
				.next(new TestCase() //
						.output(STATE_MACHINE, State.RUNNING)) //
				// Stage 1
				.next(new TestCase() //
						.timeleap(clock, 61, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(WATCHDOG_SHUTDOWN, false) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
				// Stage 2
				.next(new TestCase() //
						.timeleap(clock, 301, ChronoUnit.SECONDS) //
						.output(WATCHDOG_SHUTDOWN, true)) //
				// StartStop target is now STOP -> Running -> GoStopped
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(STATE_MACHINE, State.GO_STOPPED) //
						.output(SHUTDOWN, 1)) //
				.next(new TestCase() //
						.input(INVERTER_STATE, InverterState.SHUTDOWN)) //
				.next(new TestCase() //
						.output(STATE_MACHINE, State.STOPPED)) //
				// Recovery: fresh setpoint releases both stages
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT, 10_000) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(WATCHDOG_SHUTDOWN, false) //
						.output(ACTIVE_POWER_LIMIT, 10_000)) //
				// Stopped -> GoRunning -> startup command
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(STATE_MACHINE, State.GO_RUNNING) //
						.output(STARTUP, 1)) //
		;
	}

	/**
	 * An intentional STOP keeps the watchdog idle (no setpoints are expected) and
	 * stops the inverter via the state machine.
	 */
	@Test
	public void testForcedStopIdlesWatchdog() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig() //
				.setStartStop(StartStopConfig.STOP) //
				.build()) //
				.next(new TestCase() //
						.input(INVERTER_STATE, InverterState.ON_GRID)) //
				.next(new TestCase() //
						.timeleap(clock, 300, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(STATE_MACHINE, State.GO_STOPPED) //
						.output(SHUTDOWN, 1) //
						.output(HEART_BEAT, PvInverterHopewind.HEART_BEAT_DEFAULT)) //
		;
	}

	/**
	 * Watchdog disabled by config: no trip, ever - documents that F2 (default off)
	 * would have left the incident unprotected.
	 */
	/**
	 * Regression for the 2026-09-07 field test: the PV-Inverter Cluster writes
	 * {@code null} to every inverter at AFTER_CONTROLLERS when it has no setpoint.
	 * That clears the pending Modbus value, so a failsafe 0 asserted earlier in the
	 * cycle never reached the device. The failsafe must be the last write of the
	 * cycle.
	 */
	@Test
	public void testFailsafeSurvivesClusterNullWrite() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		var test = prepare(clock, myConfig().build());
		var sut = (PvInverterHopewindImpl) test.getSut();
		test //
				.next(new TestCase() //
						.timeleap(clock, 61, ChronoUnit.SECONDS) //
						.onAfterControllersCallbacks(() -> sut.setActivePowerLimit(null)) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
				.next(new TestCase() //
						.onAfterControllersCallbacks(() -> sut.setActivePowerLimit(null)) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0)) //
				// a fresh setpoint from the cluster in the same cycle wins and releases
				.next(new TestCase() //
						.onAfterControllersCallbacks(() -> sut.setActivePowerLimit(20_000)) //
						.output(WATCHDOG_TRIPPED, false) //
						.output(ACTIVE_POWER_LIMIT, 20_000)) //
		;
	}

	/**
	 * Regression for the 2026-09 export incident: if the inverter reports any mode
	 * other than ACTUAL (here DISABLED = 0, which ignores the kW limit), the driver
	 * forces register 40011 back to ACTUAL (1). In ACTUAL mode nothing is written.
	 */
	@Test
	public void testModeIsForcedToActual() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig().build()) //
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.DISABLED) //
						.output(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.ACTUAL)) //
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.PROPORTIONAL) //
						.output(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.ACTUAL)) //
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.ACTUAL) //
						.output(ACTIVE_POWER_LIMIT_MODE, ActivePowerLimitMode.ACTUAL)) //
		;
	}

	@Test
	public void testWatchdogDisabled() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig() //
				.setWatchdog(false) //
				.build()) //
				.next(new TestCase() //
						.timeleap(clock, 3600, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, false)) //
		;
	}
}
