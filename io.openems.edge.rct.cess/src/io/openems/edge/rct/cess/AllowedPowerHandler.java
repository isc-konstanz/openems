package io.openems.edge.rct.cess;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.util.TriConsumer;

import io.openems.edge.battery.api.Battery;
import io.openems.edge.batteryinverter.api.SymmetricBatteryInverter;
import io.openems.edge.common.component.ClockProvider;
import io.openems.edge.common.type.TypeUtils;

/**
 * Helper class to handle calculation of Allowed-Charge-Power and
 * Allowed-Discharge-Power. This class is used by {@link ChannelManager} as a
 * callback to updates of Battery Channels.
 */
public class AllowedPowerHandler implements TriConsumer<ClockProvider, Battery, SymmetricBatteryInverter> {

	public static final int VOLTAGE_CONTROL_FILTER_TIME_CONSTANT = 10; // [seconds]

	private final RctCess parent;


	public AllowedPowerHandler(RctCess parent) {
		this.parent = parent;
	}

	protected float lastAllowedBatteryChargePower;
	protected float lastAllowedBatteryDischargePower;

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
		var chargeMaxCurrent = battery.getChargeMaxCurrentChannel().getNextValue().get();
		var dischargeMaxCurrent = battery.getDischargeMaxCurrentChannel().getNextValue().get();


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
	 * @param dischargeMaxCurrent the {@link Battery.ChannelId#DISCHARGE_MAX_CURRENT}
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

}