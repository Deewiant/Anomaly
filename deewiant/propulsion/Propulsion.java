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
	};
	private static final int
		MELEE      = 0,
		ONE_ON_ONE = MELEE,
		RAMMER     = 1;

	private Engine engine;

	public void propulse() {
		if (engine != null)
			engine.move();
	}

	public void victoryDance() {
		setEngine(null);
		Global.bot.setMaxVelocity(0);
		Global.bot.setTurnLeftRadians(Double.POSITIVE_INFINITY);
	}

	public void onScannedRobot(final Enemy dude) {
		if (Global.bot.getOthers() > 1)
			setEngine(engines[MELEE]);
		else {
			if (Global.target != null) {
				if (rammerTime(Global.target))
					setEngine(engines[RAMMER]);
				else
					setEngine(engines[ONE_ON_ONE]);
			} else
				setEngine(engines[MELEE]);
		}

		engine.onScannedRobot(dude);
	}

	public void onPaint(final Graphics2D g) {
		if (engine != null) engine.onPaint(g);
	}

	private void setEngine(final Engine e) {
		if (e != engine && e != null)
			Global.out.printf("Engine set to: %s\n", e.name);
		engine = e;
	}

	// is it Rammer time?
	// assume that the target is the only dude left
	private boolean rammerTime(final Enemy dude) {
		return
			dude.energy < 0.2
			|| (
				Global.bot.getEnergy() / dude.energy >= 10 &&
				Global.bot.getTime() - dude.lastShootTime >
					(4*360.0)/Rules.RADAR_TURN_RATE);
	}
}
