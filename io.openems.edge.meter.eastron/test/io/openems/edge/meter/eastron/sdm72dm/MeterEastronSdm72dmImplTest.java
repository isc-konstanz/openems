package io.openems.edge.meter.eastron.sdm72dm;

import static io.openems.common.types.MeterType.PRODUCTION;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_POWER;
import static io.openems.edge.meter.api.ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY;

import org.junit.Test;

import io.openems.edge.bridge.modbus.test.DummyModbusBridge;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;

public class MeterEastronSdm72dmImplTest {

	private static final int OFFSET = 30001;

	private static ComponentTest prepareTest() throws Exception {
		return new ComponentTest(new MeterEastronSdm72dmImpl()) //
				.addReference("setModbus", new DummyModbusBridge("modbus0") //
						.withInputRegisters(30053 - OFFSET, //
								0xC4BB, 0x8000) // Total system power: -1500 W
						.withInputRegisters(30073 - OFFSET, //
								0x4150, 0x0000, // Total Import: 13 kWh
								0x4020, 0x0000)); // Total Export: 2.5 kWh
	}

	@Test
	public void testNonInvert() throws Exception {
		prepareTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(2) //
						.setType(PRODUCTION) //
						.setInvert(false) //
						.build()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, -1500) //
						.output(ACTIVE_PRODUCTION_ENERGY, 13000L) //
						.output(ACTIVE_CONSUMPTION_ENERGY, 2500L)) //
				.deactivate();
	}

	@Test
	public void testInvert() throws Exception {
		prepareTest() //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setModbusId("modbus0") //
						.setModbusUnitId(2) //
						.setType(PRODUCTION) //
						.setInvert(true) //
						.build()) //
				.next(new TestCase()) //
				.next(new TestCase() //
						.output(ACTIVE_POWER, 1500) //
						.output(ACTIVE_PRODUCTION_ENERGY, 2500L) //
						.output(ACTIVE_CONSUMPTION_ENERGY, 13000L)) //
				.deactivate();
	}
}
