package io.openems.edge.scheduler.cron;

import static io.openems.common.test.TestUtils.createDummyClock;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.test.TimeLeapClock;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.DummyController;
import io.openems.edge.scheduler.api.Scheduler;

//CHECKSTYLE:OFF
public class SchedulerCronImplTest {
	// CHECKSTYLE:ON

	/**
	 * Test cron-based scheduling with before/after controllers and day-of-week
	 * matching.
	 *
	 * <p>
	 * The dummy clock starts on Wednesday 2020-01-01 00:00.
	 * <ul>
	 * <li>Entry 1: {@code 30 8 * * *: morningCtrl} – every day at 08:30</li>
	 * <li>Entry 2: {@code 0 12 * * 3: wednesdayLunchCtrl} – Wednesday at 12:00
	 * </li>
	 * </ul>
	 */
	@Test
	public void test() throws Exception {
		final var clock = createDummyClock(); // Starts on a WEDNESDAY

		final SchedulerCronImpl sut = new SchedulerCronImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyController("morningCtrl")) //
				.addComponent(new DummyController("wednesdayLunchCtrl")) //
				.addComponent(new DummyController("alwaysBefore")) //
				.addComponent(new DummyController("alwaysAfter")) //
				.activate(MyConfig.create() //
						.setId("scheduler0") //
						.setAlwaysRunBeforeControllerIds("alwaysBefore") //
						.setCronEntries(//
								"30 8 * * *: morningCtrl", //
								"0 12 * * 3: wednesdayLunchCtrl") //
						.setAlwaysRunAfterControllerIds("alwaysAfter") //
						.build()) //
				.next(new TestCase("00:00 – no match") //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 00:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.next(new TestCase("08:29 – one minute before morning entry") //
						.timeleap(clock, 8 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:29")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.next(new TestCase("08:30 – morning entry matches") //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"morningCtrl", //
								"alwaysAfter"))) //
				.next(new TestCase("08:31 – morning entry no longer matches") //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 08:31")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.next(new TestCase("12:00 Wed – wednesday lunch entry matches") //
						.timeleap(clock, 3 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 12:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"wednesdayLunchCtrl", //
								"alwaysAfter"))) //
				.next(new TestCase("12:01 Wed – wednesday lunch entry no longer matches") //
						.timeleap(clock, 1, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Wed 2020-01-01 12:01")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.next(new TestCase("Thu 08:30 – morning entry matches again, lunch entry does not (wrong day)") //
						.timeleap(clock, 20 * 60 + 29, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Thu 2020-01-02 08:30")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"morningCtrl", //
								"alwaysAfter"))) //
				.next(new TestCase("Thu 12:00 – wednesday-specific entry does not match on Thursday") //
						.timeleap(clock, 3 * 60 + 30, MINUTES) //
						.onBeforeProcessImage(() -> assertTime(clock, "Thu 2020-01-02 12:00")) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"alwaysBefore", //
								"alwaysAfter"))) //
				.deactivate();
	}

	/**
	 * Test merging of multiple active cron entries.
	 *
	 * <p>
	 * The dummy clock starts at an arbitrary point in time.
	 * <ul>
	 * <li>Entry 1: {@code 0 12 * * *: cont1, cont2} – every day at 12:00, first
	 * matches cont1, then cont2</li>
	 * <li>Entry 2: {@code 0 12 * * *: cont3, cont2} – every day at 12:00, first
	 * matches cont3, then cont2 (ignored, as cont2 is already matched)</li>
	 * </ul>
	 */
	@Test
	public void testMergesMultipleActiveEntriesWithFirstOccurrenceWinning() throws Exception {
		final var clock = createDummyClock();

		final SchedulerCronImpl sut = new SchedulerCronImpl();
		new ComponentTest(sut) //
				.addReference("componentManager", new DummyComponentManager(clock)) //
				.addComponent(new DummyController("cont1")) //
				.addComponent(new DummyController("cont2")) //
				.addComponent(new DummyController("cont3")) //
				.activate(MyConfig.create() //
						.setId("scheduler0") //
						.setCronEntries(//
								"0 12 * * *: cont1, cont2", //
								"0 12 * * *: cont3, cont2") //
						.build()) //
				.next(new TestCase("12:00 - both entries active; duplicate is kept from first occurrence") //
						.timeleap(clock, 12 * 60, MINUTES) //
						.onBeforeControllersCallbacks(() -> assertControllerIds(sut, //
								"cont1", //
								"cont2", //
								"cont3"))) //
				.deactivate();
	}

	private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("E yyyy-MM-dd HH:mm")
			.withLocale(Locale.ENGLISH);

	private static void assertTime(TimeLeapClock clock, String dateTime) throws OpenemsNamedException {
		assertEquals(dateTime, clock.now().format(DTF));
	}

	private static void assertControllerIds(Scheduler scheduler, String... controllerIds) throws OpenemsNamedException {
		assertArrayEquals(controllerIds, scheduler.getControllers().stream() //
				.toArray(String[]::new));
	}
}
