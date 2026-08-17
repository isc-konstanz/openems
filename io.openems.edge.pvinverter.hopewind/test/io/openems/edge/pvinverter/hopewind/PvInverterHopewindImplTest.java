package io.openems.edge.pvinverter.hopewind;

import static io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter.ChannelId.ACTIVE_POWER_LIMIT;
import static io.openems.edge.pvinverter.hopewind.PvInverterHopewind.ChannelId.ACTIVE_POWER_LIMIT_PERCENT;
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
	 * F11: the mode enum must match the reverse-engineered register map of 40011:
	 * [0 = power, 1 = percent].
	 */
	@Test
	public void testActivePowerLimitModeMatchesRegisterMap() {
		assertEquals(0, ActivePowerLimitMode.ACTUAL.getValue());
		assertEquals(1, ActivePowerLimitMode.PROPORTIONAL.getValue());
	}

	@Test
	public void testConvertWattToPercent() {
		assertEquals(0, PvInverterHopewindImpl.convertWattToPercent(0));
		assertEquals(50, PvInverterHopewindImpl.convertWattToPercent(33000));
		assertEquals(100, PvInverterHopewindImpl.convertWattToPercent(66000));
		assertEquals(100, PvInverterHopewindImpl.convertWattToPercent(99000)); // clamped
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
						.output(ACTIVE_POWER_LIMIT, 0) //
						.output(ACTIVE_POWER_LIMIT_PERCENT, 0)) //
				// latched: re-asserted on the next cycle as well
				.next(new TestCase() //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0) //
						.output(ACTIVE_POWER_LIMIT_PERCENT, 0)) //
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
	 * F5: in PROPORTIONAL mode the setpoint is forwarded as percent and the
	 * failsafe writes percent 0 - no exception may escape.
	 */
	@Test
	public void testProportionalMode() throws Exception {
		final var clock = new TimeLeapClock(Instant.parse("2026-01-01T12:00:00.00Z"), ZoneOffset.UTC);
		prepare(clock, myConfig() //
				.setActivePowerLimitMode(ActivePowerLimitMode.PROPORTIONAL) //
				.build()) //
				.next(new TestCase() //
						.input(ACTIVE_POWER_LIMIT, 33_000) //
						.output(ACTIVE_POWER_LIMIT_PERCENT, 50)) //
				.next(new TestCase() //
						.timeleap(clock, 61, ChronoUnit.SECONDS) //
						.output(WATCHDOG_TRIPPED, true) //
						.output(ACTIVE_POWER_LIMIT, 0) //
						.output(ACTIVE_POWER_LIMIT_PERCENT, 0)) //
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
