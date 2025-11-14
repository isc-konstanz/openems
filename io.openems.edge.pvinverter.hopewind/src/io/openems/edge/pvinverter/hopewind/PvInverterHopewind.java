package io.openems.edge.pvinverter.hopewind;

import org.osgi.service.event.EventHandler;

import io.openems.common.channel.AccessMode;
import io.openems.common.channel.Level;
import io.openems.common.channel.PersistencePriority;
import io.openems.common.channel.Unit;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.OpenemsType;
import io.openems.edge.bridge.modbus.api.ModbusComponent;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerDoc;
import io.openems.edge.common.channel.IntegerWriteChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.startstop.StartStop;
import io.openems.edge.common.startstop.StartStoppable;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.pvinverter.api.ManagedSymmetricPvInverter;
import io.openems.edge.pvinverter.hopewind.statemachine.StateMachine.State;

public interface PvInverterHopewind extends ManagedSymmetricPvInverter, ElectricityMeter,
	ModbusSlave, ModbusComponent, OpenemsComponent, EventHandler, StartStoppable {

	public static final int HEART_BEAT_DEFAULT = 0;

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		// === 1080 - 1082 ===
		REG_1080(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1080")),
		REG_1081(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1081")),
		REG_1082(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1082")),
		
		// === 1083 - 1085 ===
		REG_1083(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1083")),
		REG_1084(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1084")),
		REG_1085(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 1085")),

		// === 30000 - 30003 ===
		REG_30000(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30000")),
		REG_30001(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30001")),
		TIME(Doc.of(OpenemsType.DOUBLE)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.NONE)),

		// === 30021 - 30023 ===
		REG_30021(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30021")),
		REG_30022(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30022")),
		REG_30023(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30023")),

		// === 30026 - 30036 ===
		REG_30026(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30026")),
		REG_30027(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30027")),
		REG_30028(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30028")),
		REG_30029(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30029")),
		REG_30030(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30030")),
		REG_30031(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30031")),
		REG_30032(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30032")),
		REG_30033(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30033")),
		REG_30034(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30034")),
		REG_30035(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30035")),
		REG_30036(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30036")),

		// === 30056 - 30073 ===
		REG_30056(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30056")),
		REG_30057(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30057")),
		REG_30058(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30058")),
		REG_30059(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30059")),
		REG_30060(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30060")),
		REG_30061(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30061")),
		REG_30062(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30062")),
		REG_30063(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30063")),
		REG_30064(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30064")),
		REG_30065(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30065")),
		REG_30066(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30066")),
		REG_30067(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30067")),
		REG_30068(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30068")),
		REG_30069(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30069")),
		REG_30070(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30070")),
		REG_30071(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30071")),
		REG_30072(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30072")),
		REG_30073(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30073")),

		// === 30079 - 30115 ===
		REG_30079(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30079")),
		REG_30080(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30080")),
		REG_30081(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30081")),
		REG_30082(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30082")),
		REG_30083(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30083")),
		REG_30084(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30084")),
		REG_30085(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30085")),
		REG_30086(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30086")),
		REG_30087(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30087")),
		REG_30088(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30088")),
		REG_30089(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30089")),
		REG_30090(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30090")),
		REG_30091(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30091")),
		REG_30092(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30092")),
		REG_30093(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30093")),
		REG_30094(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30094")),
		REG_30095(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30095")),
		REG_30096(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30096")),
		REG_30097(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30097")),
		REG_30098(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30098")),
		REG_30099(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30099")),
		REG_30100(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30100")),
		REG_30101(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30101")),
		REG_30102(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30102")),
		REG_30103(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30103")),
		REG_30104(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30104")),
		REG_30105(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30105")),
		REG_30106(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30106")),
		REG_30107(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30107")),
		REG_30108(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30108")),
		REG_30109(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30109")),
		REG_30110(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30110")),
		REG_30111(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30111")),
		REG_30112(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30112")),
		REG_30113(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30113")),
		REG_30114(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30114")),
		REG_30115(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30115")),

		// === 30117 - 30123 ===
		DAILY_GENERATION(Doc.of(OpenemsType.LONG)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.KILOWATT_HOURS)),
		CUM_GENERATION(Doc.of(OpenemsType.LONG)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.KILOWATT_HOURS)),
		REG_30121(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30121")),
		REG_30122(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30122")),
		REG_30123(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 30123")),

		// === 31112 - 31115 ===
		REG_31112(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 31112")),
		REG_31113(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 31113")),
		REG_31114(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 31114")),
		REG_31115(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 31115")),

// Not available (Modbus Exception)
//		// === 31119 - 31120 ===
//		NIGHT_SLEEP(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("Night Sleep Status")),
//		RSD_ENABLED(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.text("RSP Enable Status")),

		// === 32014 ===
		HEART_BEAT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.WRITE_ONLY)
				.unit(Unit.NONE)),

		// === 34000 - 34003 ===
		REG_34000(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 34000")),
		REG_34001(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 34001")),
		REG_34002(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 34002")),
		REG_34003(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 34003")),

		// === 34005 ===
		REG_34005(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 34005")),

		// == 34074 - 34075 ==
		DRM_ENABLED(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("DRM Enable Status")),
		RIPPLE_CONTROL_ENABLED(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Ripple Control Enable Status")),

		// === 34294 - 39295 ===
		NS_PROTECTION_ENABLED(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Night Sleep Protection Status")),
		NS_PROTECTION_SWITCH(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Night Sleep Protection Switch")),

		// === 40000 - 40005 ===
		REG_40000(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40000")),
		REG_40001(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40001")),
		REACTIVE_REGULATION_MODE(Doc.of(ReactivePowerLimitState.values())
				.accessMode(AccessMode.READ_WRITE)
				.text("Reactive Power Regulation Mode")),
		REACTIVE_POWER_FACTOR_REGULATION(Doc.of(OpenemsType.FLOAT)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.PERCENT)),
		REACTIVE_POWER_REGULATION(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.VOLT_AMPERE_REACTIVE)),
		REACTIVE_PERCENT_REGULATION(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.PERCENT)),

		// === 40011- 40013 ===
		ACTIVE_REGULATION_MODE(Doc.of(ActivePowerLimitState.values())
				.accessMode(AccessMode.READ_WRITE)
				.text("Active Power Regulation Mode")),
//		ACTIVE_POWER_REGULATION(Doc.of(OpenemsType.INTEGER)
//				.accessMode(AccessMode.READ_WRITE)
//				.unit(Unit.WATT)),
		ACTIVE_PERCENT_REGULATION(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.unit(Unit.PERCENT)),

		// === 40200 - 40202 ===
		STARTUP(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Start the Inverter")),
		SHUTDOWN(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Stop the Inverter")),
		RESET(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_WRITE)
				.text("Reset the Inverter")),

		// === 40500 - 40594 ===
		MPPT_1_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),
		MPPT_2_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),
		MPPT_3_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),
		MPPT_4_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),
		REG_40504(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40504")),
		REG_40505(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40505")),
		REG_40506(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40506")),
		REG_40507(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40507")),
		STRING_1_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_2_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_3_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_4_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_5_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_6_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_7_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_8_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_9_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)), 
		STRING_10_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_11_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_12_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_13_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_14_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_15_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		STRING_16_CURRENT(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.MILLIAMPERE)),
		MPPT_1_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		MPPT_2_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		MPPT_3_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		MPPT_4_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		REG_40528(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40528")),
		REG_40529(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40529")),
		REG_40530(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40530")),
		REG_40531(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40531")),
		// GRID VOLTAGE AB
		// GRID VOLTAGE BC
		// GRID VOLTAGE CA
		// GRID CURRENT A
		// GRID CURRENT B
		// GRID CURRENT C
		// FREQUENCY
		// ACTIVE POWER
		// REACTIVE POWER
		APPERENT_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT_AMPERE)),
		POWER_FACTOR(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.PERCENT)),
		REG_40543(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40543")),
		TEMPERATURE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.DEZIDEGREE_CELSIUS)),
		INSULATION_RESISTANCE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.OHM)),
		RUNNING_STATE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Inverter Running State")),
		INVERTER_STATE(Doc.of(InverterState.values())
				.accessMode(AccessMode.READ_ONLY)
				.text("Inverter State")),
		TODAY_YIELD(Doc.of(OpenemsType.LONG)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.KILOWATT_HOURS)),
		REG_40550(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40550")),
		REG_40551(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40551")),
		CO2_REDUCTION(Doc.of(OpenemsType.LONG)
				.accessMode(AccessMode.READ_ONLY)
				.text("CO2 Reduction in kg")),
		DAILY_RUNTIME(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.SECONDS)),
		TOTAL_RUNTIME(Doc.of(OpenemsType.LONG)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.SECONDS)),
		REG_40557(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40557")),
		REG_40558(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40558")),
		REG_40559(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40559")),
		REG_40560(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40560")),
		// NOT SURE ABOUT THESE FOLLOWING FAULT/ALARM/ARCING WORDS
		FAULT_WORD_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 1")),
		FAULT_WORD_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 2")),
		FAULT_WORD_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 3")),
		FAULT_WORD_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 4")),
		FAULT_WORD_5(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 5")),
		FAULT_WORD_6(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 6")),
		FAULT_WORD_7(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 7")),
		FAULT_WORD_8(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 8")),
		ALARM_WORD_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 1")),
		ALARM_WORD_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 2")),
		ALARM_WORD_3(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 3")),
		ALARM_WORD_4(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 4")),
		ALARM_WORD_5(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 5")),
		ALARM_WORD_6(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 6")),
		ALARM_WORD_7(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 7")),
		ALARM_WORD_8(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Word 8")),
		FAULT_WORD_9(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Word 9")),
		FAULT_CODE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Fault Code")),
		ALARM_CODE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Alarm Code")),
		ARCING_WORD_1(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Arcing Word 1")),
		ARCING_WORD_2(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Arcing Word 2")),
		REG_40582(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40582")),
		REG_40583(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40583")),
		REG_40584(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40584")),
		REG_40585(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40585")),
		REG_40586(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40586")),
		REG_40587(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40587")),
		REG_40588(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40588")),
		REG_40589(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40589")),
		REG_40590(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40590")),
		REG_40591(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40591")),
		REG_40592(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40592")),
		REG_40593(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40593")),
		DC_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.KILOWATT)),

		// === 40601 - 40644 ===
		UNIT_ID(Doc.of(OpenemsType.STRING)
				.accessMode(AccessMode.READ_ONLY)
				.text("The Modbus Unit ID of the Inverter")),
		REG_40627(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40647")),
		REG_40628(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40648")),
		REG_40629(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40649")),
		REG_40630(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40650")),
		REG_40631(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40651")),
		REG_40632(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40652")),
		REG_40633(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40653")),
		REG_40634(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40654")),
		REG_40635(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40655")),
		REG_40636(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40656")),
		REG_40637(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40657")),
		REG_40638(Doc.of(OpenemsType.INTEGER)	
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40658")),
		REG_40639(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40659")),
		REG_40640(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40660")),
		REG_40641(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40661")),
		REG_40642(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40662")),
		REG_40643(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40663")),
		REG_40644(Doc.of(OpenemsType.INTEGER)	
				.accessMode(AccessMode.READ_ONLY)
				.text("Register 40664")),

		// === 40646 - 40647 ===
		RATED_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		RATED_VOLTAGE(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.VOLT)),

		// === 41000 - 41019 ===
		STRING_1_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_2_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_3_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_4_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_5_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_6_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_7_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_8_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_9_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_10_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_11_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_12_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_13_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_14_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_15_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),
		STRING_16_POWER(Doc.of(OpenemsType.INTEGER)
				.accessMode(AccessMode.READ_ONLY)
				.unit(Unit.WATT)),

		STATE_MACHINE(Doc.of(State.values())
				.text("Current State of State-Machine")),
		RUN_FAILED(Doc.of(Level.WARNING)
				.text("Running the Logic failed")),
		MAX_START_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of start attempts failed")),
		MAX_STOP_ATTEMPTS(Doc.of(Level.WARNING)
				.text("The maximum number of stop attempts failed"));


		

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_START_ATTEMPTS}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getMaxStartAttemptsChannel() {
		return this.channel(ChannelId.MAX_START_ATTEMPTS);
	}

	/**
	 * Gets the {@link StateChannel} for {@link ChannelId#MAX_START_ATTEMPTS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getMaxStartAttempts() {
		return this.getMaxStartAttemptsChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#MAX_START_ATTEMPTS} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxStartAttempts(Boolean value) {
		this.getMaxStartAttemptsChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MAX_STOP_ATTEMPTS}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getMaxStopAttemptsChannel() {
		return this.channel(ChannelId.MAX_STOP_ATTEMPTS);
	}

	/**
	 * Gets the {@link StateChannel} for {@link ChannelId#MAX_STOP_ATTEMPTS}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getMaxStopAttempts() {
		return this.getMaxStopAttemptsChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MAX_STOP_ATTEMPTS}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMaxStopAttempts(Boolean value) {
		this.getMaxStopAttemptsChannel().setNextValue(value);
	}

	/**
	 * Gets the target Start/Stop mode from config or StartStop-Channel.
	 *
	 * @return {@link StartStop}
	 */
	public StartStop getStartStopTarget();

	/**
	 * Gets the Channel for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel
	 */
	public default Channel<State> getStateMachineChannel() {
		return this.channel(ChannelId.STATE_MACHINE);
	}

	/**
	 * Gets the StateMachine channel value for {@link ChannelId#STATE_MACHINE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<State> getStateMachine() {
		return this.getStateMachineChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#STATE_MACHINE}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setStateMachine(State value) {
		this.getStateMachineChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RUN_FAILED}.
	 *
	 * @return the Channel
	 */
	public default Channel<Boolean> getRunFailedChannel() {
		return this.channel(ChannelId.RUN_FAILED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RUN_FAILED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRunFailed(boolean value) {
		this.getRunFailedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#INVERTER_STATE}.
	 *
	 * @return the Channel {@link Channel}
	 */
	public default Channel<InverterState> getInverterStateChannel() {
		return this.channel(ChannelId.INVERTER_STATE);
	}

	/**
	 * Gets the {@link InverterState}, see {@link ChannelId#INVERTER_STATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<InverterState> getInverterState() {
		return this.getInverterStateChannel().value();
	}

	/**
	 * Sets the Startup value. See {@link ChannelId#STARTUP}.
	 */
	public default void startup() throws OpenemsNamedException {
		IntegerWriteChannel startupChannel = this.channel(ChannelId.STARTUP);
		startupChannel.setNextWriteValue(1);
	}

	/**
	 * Sets the Shutdown value. See {@link ChannelId#SHUTDOWN}.
	 */
	public default void shutdown() throws OpenemsNamedException {
		IntegerWriteChannel shutdownChannel = this.channel(ChannelId.SHUTDOWN);
		shutdownChannel.setNextWriteValue(1);
	}

	/**
	 * Sets the Reset value. See {@link ChannelId#RESET}.
	 */
	public default void reset() throws OpenemsNamedException {
		IntegerWriteChannel resetChannel = this.channel(ChannelId.RESET);
		resetChannel.setNextWriteValue(1);
	}


	/**
	 * Sets the HeartBeat value. See {@link ChannelId#HEART_BEAT}.
	 *
	 * @param value the Integer value
	 * @throws OpenemsNamedException on error
	 */
	public default void setHeartBeat(Integer value) throws OpenemsNamedException {
		IntegerWriteChannel heartBeatChannel = this.channel(ChannelId.HEART_BEAT);
		heartBeatChannel.setNextWriteValue(value);
	}

	/**
	 * Sets the HeartBeat value. See {@link ChannelId#HEART_BEAT}.
	 *
	 * @throws OpenemsNamedException on error
	 */
	public default void setHeartBeat() throws OpenemsNamedException {
		this.setHeartBeat(HEART_BEAT_DEFAULT);
	}
}
