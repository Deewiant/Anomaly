package deewiant;
import robocode.*;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

final public class Anomaly extends AdvancedRobot {
	private static final Color   RED = new Color(48, 0, 128);
	private static final Color  BLUE = new Color(48, 0, 128);
	private static final Color GREEN = null;

	private              HashMap    <String, Enemy> dudes;
	private              Bombardier                 bombardier;
	private              Propulsion                 propulsion;
	private              Perception                 perception;

	private              String target;

	private              boolean won;

	public void run() {
		super.setColors(RED, BLUE, GREEN);
		super.setAdjustGunForRobotTurn(true);
		super.setAdjustRadarForGunTurn(true);
		super.setAdjustRadarForRobotTurn(true);
		won = false;

		dudes      = new HashMap<String, Enemy>(    );
		bombardier = new Bombardier            (this);
		propulsion = new Propulsion            (this);
		perception = new Perception            (this);

		while (!won) {
			perception.perceive(dudes.get(target));
			bombardier.bombard (dudes.get(target));
			propulsion.propel  (dudes.get(target));
			execute();
		}
	}

	public void onDeath(DeathEvent e) {
		gameOver();
	}
	public void onWin(WinEvent e) {
		won = true;
		gameOver();
		propulsion.victoryDance();
		perception.victoryDance();
		bombardier.victoryDance();
	}
	private void gameOver() {
		bombardier.spewTheStats();
		clearAllEvents(); // to avoid printing twice if we win and then die
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		Enemy dude;
		Enemy oldDude = dudes.get(e.getName());
		if (oldDude != null)
		 	dude = oldDude;
		 else
		 	dude = new Enemy();

		double absoluteBearing = (this.getHeadingRadians() + dude.bearingRad) % (2 * Math.PI);

		double heading = e.getHeadingRadians();
		long now = this.getTime();

		dude.deltaHeading = Utils.normaliseBearing(heading - dude.headingRad) / (now - dude.scanTime);

		dude.headingRad = e.getHeadingRadians();
		dude.bearingRad = e.getBearingRadians();
		dude.distance   = e.getDistance();
		dude.scanTime   = now;
		dude.energy     = e.getEnergy();
		dude.speed      = e.getVelocity();
		dude.name       = e.getName();
		dude.x          = this.getX() + dude.distance * Math.sin(absoluteBearing);
		dude.y          = this.getY() + dude.distance * Math.cos(absoluteBearing);

		if (target == null || dudes.get(target).distance * 0.9 > dude.distance)
			target = dude.name;

		dudes.put(dude.name, dude);

		updateBots();
	}

	public void onRobotDeath(RobotDeathEvent e) {
		String name = e.getName();
		if (dudes.containsKey(name))
			dudes.remove(name);
		if (name == target)
			target = null;

		updateBots();
	}

	private void updateBots() {
		propulsion.updateEnemies(dudes.values());
	}

	public void onBulletHit(BulletHitEvent e) { bombardier.contemplateHit(e); }
}
