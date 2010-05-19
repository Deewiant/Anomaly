// File created: prior to 2005-12-01

package deewiant.propulsion;

import java.awt.Graphics2D;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;
import deewiant.propulsion.engines.model.Engine;
import deewiant.propulsion.engines.*;

public final class Propulsion {
	private static Engine[] engines = {
		new RiskMinimizer(),
//		new WaveSurfer(),
		new Rammer(),
		new ShrapnelDodger(),
	};
	private static final int
		MELEE      = 0,
		ONE_ON_ONE = MELEE,
		RAMMER     = 1,
		DODGER     = 2;

	private Engine engine;

	public void propulse() {
		if (engine != null)
			engine.move();
	}

	public void victoryDance() {
		Global.bot.setMaxVelocity(0);
		Global.bot.setTurnLeftRadians(Double.POSITIVE_INFINITY);
	}

	public void onScannedRobot(final Enemy dude) {
		if (rammerTime())
			setEngine(engines[RAMMER]);
		else if (dodgerFeelingLucky())
			setEngine(engines[DODGER]);
		else if (Global.bot.getOthers() > 1)
			setEngine(engines[MELEE]);
		else
			setEngine(engines[ONE_ON_ONE]);

		engine.onScannedRobot(dude);
	}

	public void onFired(final Enemy dude) {
		if (engine != null) engine.onFired(dude);
	}

	private void setEngine(final Engine e) {
		if (e != engine) {
			Global.out.printf("Engine set to: %s\n", e.name);
			engine = e;
		}
	}

	// Is it Rammer time?
	private boolean rammerTime() {
		final double nrg = Global.bot.getEnergy();

		// Don't kill yourself...
		if (nrg <= Rules.ROBOT_HIT_DAMAGE)
			return false;

		final long now = Global.bot.getTime();

		for (final Enemy dude : Global.dudes) if (!dude.dead)
		if (!(
			(dude.energy < 0.2 || (dude.energy <= 4 && nrg >= 40)) &&
			now - dude.lastShootTime >
				dude.distance(Global.me) / Rules.getBulletSpeed(dude.firePower)
		))
			return false;
		return true;
	}

	private boolean dodgerFeelingLucky() {
		// FIXME
		return true;
	}
}
