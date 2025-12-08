package io.openems.edge.rct.cess;

import static io.openems.edge.common.type.TypeUtils.multiply;
import static io.openems.edge.common.type.TypeUtils.subtract;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BiFunction;

import org.apache.logging.log4j.util.TriConsumer;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.filter.Pt1filter;
import io.openems.edge.common.type.TypeUtils;

/**
 * Helper class to handle calculation of Allowed-Charge-Power and
 * Allowed-Discharge-Power. This class is used by {@link ChannelManager} as a
 * callback to updates of Battery Channels.
 */
public class AllowedPowerHandler implements TriConsumer<ClockProvider, Battery, SymmetricBatteryInverter> {

	public static final int VOLTAGE_CONTROL_FILTER_TIME_CONSTANT = 10; // [seconds]

//	private static final int ESS_PROTECTION_EXTREME_LIMIT_TIMEOUT = 240; // [seconds]

	private final RctCess parent;

	private final Pt1filter pt1FilterChargeMaxCurrentVoltageLimit;
	private final Pt1filter pt1FilterDischargeMaxCurrentVoltageLimit;

	public AllowedPowerHandler(RctCess parent) {
		this.parent = parent;
		this.pt1FilterChargeMaxCurrentVoltageLimit = new Pt1filter(VOLTAGE_CONTROL_FILTER_TIME_CONSTANT,
				this.parent.getCycleTime());
		this.pt1FilterDischargeMaxCurrentVoltageLimit = new Pt1filter(VOLTAGE_CONTROL_FILTER_TIME_CONSTANT,
				this.parent.getCycleTime());
	}

	protected float lastAllowedBatteryChargePower;
	protected float lastAllowedBatteryDischargePower;

//	private Instant lastEssProtectionEntry = null;
	private Instant lastCalculate = null;

	@Override
	public void accept(ClockProvider clockProvider, Battery battery, SymmetricBatteryInverter inverter) {
		this.calculateAllowedChargeDischargePower(clockProvider, battery, inverter);

		// Battery limits
		var batteryAllowedChargePower = Math.round(this.lastAllowedBatteryChargePower);
		var batteryAllowedDischargePower = Math.round(this.lastAllowedBatteryDischargePower);

		// PV-Production (for HybridEss)
		var pvPower = Math.max(
				TypeUtils.orElse(
						TypeUtils.subtract(this.parent.getActivePower().get(), this.parent.getDcDischargePower().get()),
						0),
				0);

		// Apply AllowedChargePower and AllowedDischargePower
		this.parent._setAllowedChargePower(batteryAllowedChargePower * -1 /* invert charge power */);
		this.parent._setAllowedDischargePower(batteryAllowedDischargePower + pvPower);
	}

	/**
	 * Calculates Allowed-Charge-Power and Allowed-Discharge Power from the given
	 * parameters. Result is stored in 'lastBatteryAllowedChargePower' and
	 * 'lastBatteryAllowedDischargePower' variables - both as positive values!
	 *
	 * @param clockProvider the {@link ClockProvider}
	 * @param battery       the {@link Battery}
	 * @param inverter      the {@link SymmetricBatteryInverter}
	 */
	protected void calculateAllowedChargeDischargePower(
			ClockProvider clockProvider, Battery battery, SymmetricBatteryInverter inverter) {
		final var cycleTime = this.parent.getCycleTime();
		var chargeMaxCurrent = battery.getChargeMaxCurrentChannel().getNextValue().get();
		var dischargeMaxCurrent = battery.getDischargeMaxCurrentChannel().getNextValue().get();

		final var voltRegulationChargeMaxCurrent = calculateMaxCurrent(battery, inverter, cycleTime,
				this.pt1FilterChargeMaxCurrentVoltageLimit, TypeUtils::min, TypeUtils::subtract, true);
		final var voltRegulationDischargeMaxCurrent = calculateMaxCurrent(battery, inverter, cycleTime,
				this.pt1FilterDischargeMaxCurrentVoltageLimit, TypeUtils::max, TypeUtils::sum, false);

//		parent._setEvpChargeMaxCurrent(voltRegulationChargeMaxCurrent);
//		parent._setEvpDischargeMaxCurrent(voltRegulationDischargeMaxCurrent);

		chargeMaxCurrent = TypeUtils.min(chargeMaxCurrent, voltRegulationChargeMaxCurrent);
		dischargeMaxCurrent = TypeUtils.min(dischargeMaxCurrent, voltRegulationDischargeMaxCurrent);

//		final var current = battery.getCurrentChannel().value();
//		this.checkEssVoltageProtectionExtremes(clockProvider, chargeMaxCurrent, dischargeMaxCurrent, current);

		final var voltage = battery.getVoltageChannel().getNextValue().get();
		this.calculateAllowedChargeDischargePower(clockProvider, this.parent.isStarted(),
				chargeMaxCurrent, dischargeMaxCurrent, voltage);
	}

	/**
	 * Calculates Allowed-Charge-Power and Allowed-Discharge Power from the given
	 * parameters. Result is stored in 'allowedChargePower' and
	 * 'allowedDischargePower' variables - both as positive values!
	 *
	 * @param clockProvider       the {@link ClockProvider}
	 * @param isStarted           is the ESS started?
	 * @param chargeMaxCurrent    the {@link Battery.ChannelId#CHARGE_MAX_CURRENT}
	 * @param dischargeMaxCurrent the
	 *                            {@link Battery.ChannelId#DISCHARGE_MAX_CURRENT}
	 * @param voltage             the {@link Battery.ChannelId#VOLTAGE}
	 */
	protected void calculateAllowedChargeDischargePower(ClockProvider clockProvider, boolean isStarted,
			Integer chargeMaxCurrent, Integer dischargeMaxCurrent, Integer voltage) {
		final var now = Instant.now(clockProvider.getClock());
		float charge;
		float discharge;

		/*
		 * Calculate initial AllowedChargePower and AllowedDischargePower
		 */
		if (!isStarted || chargeMaxCurrent == null || dischargeMaxCurrent == null || voltage == null) {
			// Block ACTIVE and REACTIVE Power if
			// - GenericEss is not in State "STARTED"
			// - any of CHARGE_MAX_CURRENT, DISHARGE_MAX_CURRENT or VOLTAGE are missing
			charge = 0;
			discharge = 0;

		} else {
			// Calculate AllowedChargePower and AllowedDischargePower from battery current
			// limits and voltage.
			// Efficiency factor is not considered in chargeMaxCurrent (DC Power > AC Power)
			charge = chargeMaxCurrent * voltage;
			discharge = round(dischargeMaxCurrent * voltage * RctCess.EFFICIENCY_FACTOR);
		}

		/*
		 * Handle Force Charge and Discharge
		 */
		if (charge < 0 && discharge < 0) {
			// Both Force Charge and Discharge are active -> cannot do anything
			charge = 0;
			discharge = 0;

		} else if (discharge < 0) {
			// Force Charge is active
			// Make sure AllowedChargePower is greater-or-equals absolute
			// AllowedDischargePower
			charge = max(charge, abs(discharge));

		} else if (charge < 0) {
			// Force Discharge is active
			// Make sure AllowedDischargePower is greater-or-equals absolute
			// AllowedChargePower
			discharge = max(abs(charge), discharge);
		}

		/*
		 * In Non-Force Mode: apply the max increase ramp.
		 */
		if (charge > 0) {
			charge = applyMaxIncrease(this.lastAllowedBatteryChargePower, charge, this.lastCalculate, now);
		}
		if (discharge > 0) {
			discharge = applyMaxIncrease(this.lastAllowedBatteryDischargePower, discharge, this.lastCalculate, now);
		}

		/*
		 * Apply result
		 */
		this.lastCalculate = now;
		this.lastAllowedBatteryChargePower = charge;
		this.lastAllowedBatteryDischargePower = discharge;
	}

//	private void checkEssVoltageProtectionExtremes(ClockProvider clockProvider, Integer chargeMaxCurrent,
//			Integer dischargeMaxCurrent, Value<Integer> current) {
//		if (!(this.parent instanceof EssVoltageProtection ess)) {
//			return;
//		}
//		if (dischargeMaxCurrent == null || chargeMaxCurrent == null || !current.isDefined()) {
//			return;
//		}
//		if (dischargeMaxCurrent >= 0 || chargeMaxCurrent >= 0) {
//			this.lastEssProtectionEntry = null;
//			ess._setEvpDeepDischargeProtection(false);
//			ess._setEvpOverChargeProtection(false);
//			return;
//		}
//
//		if (this.lastEssProtectionEntry == null) {
//			this.lastEssProtectionEntry = Instant.now(clockProvider.getClock());
//		}
//
//		if (dischargeMaxCurrent < 0
//				&& current.get() >= 0
//				&& this.isExtremeTimeoutPassed()) {
//			ess._setEvpDeepDischargeProtection(true);
//		}
//
//		if (chargeMaxCurrent < 0
//				&& current.get() <= 0
//				&& this.isExtremeTimeoutPassed()) {
//			ess._setEvpOverChargeProtection(true);
//		}
//	}
//
//	private boolean isExtremeTimeoutPassed() {
//		return Duration.between(this.lastEssProtectionEntry, Instant.now())
//				.getSeconds() > ESS_PROTECTION_EXTREME_LIMIT_TIMEOUT;
//	}

	/**
	 * Applies the max increase ramp, built from MAX_INCREASE_PERCENTAGE.
	 *
	 * @param lastValue   the result value in [W] of previous run
	 * @param thisValue   the current value [W]
	 * @param lastInstant the timestamp of the previous run
	 * @param thisInstant the current timestamp
	 * @return the new value
	 */
	private static float applyMaxIncrease(float lastValue, float thisValue, Instant lastInstant, Instant thisInstant) {
		final long millis;
		if (lastValue < 0 || lastInstant == null) {
			// Was in Force-Mode before
			lastValue = 0;
			millis = 1000;
		} else {
			millis = Duration.between(lastInstant, thisInstant).toMillis();
		}
		return min(thisValue, lastValue + thisValue * millis * RctCess.MAX_POWER_INCREASE_PERCENTAGE / 1000.F /* convert [mW] to [W] */);
	}

	private record RegulationValues(
			boolean isBatteryStarted,
			int voltage,
			int current,
			int chargeMaxVoltage,
			int dischargeMinVoltage,
			Integer innerResistance,
//			Integer bvpChargeBms,
//			Integer bvpDischargeBms,
			int inverterDcMinVoltage,
			int inverterDcMaxVoltage) {
		private static RegulationValues from(Battery battery, SymmetricBatteryInverter inverter) {
			var isBatteryStarted = battery.isStarted();
			var voltage = battery.getVoltage().get();
			var current = battery.getCurrent().get();
			var chargeMaxVoltage = battery.getChargeMaxVoltage().get();
			var dischargeMinVoltage = battery.getDischargeMinVoltage().get();
			var innerResistance = battery.getInnerResistance().get();
//			var bvpChargeBms = battery.getBvpChargeBms().get();
//			var bvpDischargeBms = battery.getBvpDischargeBms().get();
			var inverterDcMinVoltage = inverter.getDcMinVoltage().get();
			var inverterDcMaxVoltage = inverter.getDcMaxVoltage().get();
			if (!isBatteryStarted
					|| voltage == null
					|| current == null
					|| chargeMaxVoltage == null
					|| dischargeMinVoltage == null
					|| innerResistance == null
					|| inverterDcMinVoltage == null
					|| inverterDcMaxVoltage == null
			) {
				return null;
			}
			return new RegulationValues(isBatteryStarted, voltage, current, chargeMaxVoltage, dischargeMinVoltage,
					innerResistance, inverterDcMinVoltage, inverterDcMaxVoltage);
		}
	}

	private static Integer calculateMaxCurrent(Battery battery, SymmetricBatteryInverter inverter, int cycleTime,
			Pt1filter pt1Filter, BiFunction<Integer, Integer, Integer> dcLimit,
			BiFunction<Double, Double, Double> typeUtilsMethod, boolean invert) {
		var regulationValues = RegulationValues.from(battery, inverter);
		if (regulationValues == null) {
			return null;
		}

//		final var batteryLimit = invert
//				? TypeUtils.min(regulationValues.chargeMaxVoltage, regulationValues.bvpChargeBms)
//				: TypeUtils.max(regulationValues.dischargeMinVoltage, regulationValues.bvpDischargeBms);
		final var batteryLimit = invert
				? regulationValues.chargeMaxVoltage
				: regulationValues.dischargeMinVoltage;
		final var inverterLimit = invert
				? regulationValues.inverterDcMaxVoltage
				: regulationValues.inverterDcMinVoltage;
		final var limitVoltage = dcLimit.apply(
				batteryLimit,
				inverterLimit);

		var subtractLimit = subtract(regulationValues.voltage, limitVoltage);
		var voltageDifference = invert ? multiply(subtractLimit, -1) : subtractLimit;

		var resistance = regulationValues.innerResistance / 1000.;
		final var deltaChargeCurrent = voltageDifference / resistance;
		final var maxCurrentVoltLimit = typeUtilsMethod.apply(deltaChargeCurrent, (double) regulationValues.current);
		pt1Filter.setCycleTime(cycleTime);

		return pt1Filter.applyPt1Filter(max(maxCurrentVoltLimit, -5.0));
	}
}