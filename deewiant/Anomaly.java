//C:\Program Files\Java\jdk1.5.0_05\bin
//heading - absolute angle in degrees with 0 facing up the screen, positive clockwise. 0 <= heading < 360.
//bearing - relative angle to some object from your robot's heading, positive clockwise. -180 < bearing <= 180

package deewiant;
import robocode.*;
import java.awt.Color;
import java.util.Vector;

/*
CURRENT

How does it move?
	It doesn't, except when dodging.

How does it fire?

How does it dodge bullets?
	It detects when someone fires, and randomises a direction where to move.
	It tries to dodge walls, but fails, and gets shot easily.

How does the melee strategy differ from one-on-one strategy?

How does it select a target to attack/avoid in melee?
*/

final public class Anomaly extends AdvancedRobot {
	private class Enemy {
		double        energy,
		          prevEnergy,
		            distance,
		             bearing; // radians
		String          name;

		public String toString() {
			return name;
		}
	}

	private double bullet;
	private Vector<Enemy> enemies = new Vector<Enemy>();
	private int target = -1; // just an array index in enemies
	private int enemiesLeft;

	// used for radar scanning in 1vs1
	private int detectionTime = 999;
	private double enemyAbsoluteBearing;

	public void run() {
		setColors(new Color(72, 0, 96), new Color(255, 0, 0), null);
		enemiesLeft = getOthers();

		addCustomEvent(
			new Condition("bulletFired") {
				public boolean test() {
					// for each robot, guess whether it fired this turn by monitoring its energy change

					for (Enemy e: enemies) {
						double delta = e.prevEnergy - e.energy;
						e.prevEnergy = e.energy;

						// damn floating point inaccuracies!!
						// if the energy change matches that of a shot, or the dude gained energy
						if ((delta >= 0.09 && delta <= 3.01) || delta < 0)
							return true;
					}

					return false;
				};
			}
		);

		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);

		for (;;) {
			move();
			radar();
			gun();
			execute();
		}
	}

	public void onBulletHit(BulletHitEvent e) {
	}

	public void onBulletHitBullet(BulletHitBulletEvent e) {
	}

	public void onBulletMissed(BulletMissedEvent e) {
	}

	public void onCustomEvent(CustomEvent e) {
		if (e.getCondition().getName().equals("bulletFired")) {
			// dodge!
			if (Math.random() >= 0.5)
				setTurnRightRadians((Math.random() * Math.PI * 2) % (Math.PI * 2));
			else
				setTurnLeftRadians((Math.random() * Math.PI * 2) % (Math.PI * 2));

			if (Math.random() <= 0.5)
				setAhead(80 + Math.random() % 80);
			else
				setBack (80 + Math.random() % 80);
		}
	}

	public void onHitByBullet(HitByBulletEvent e) {
	}

	public void onHitRobot(HitRobotEvent e) {
	}

	public void onHitWall(HitWallEvent e) {
		double relativeAngle = e.getBearing();
		if (-90 < relativeAngle && relativeAngle <= 90)
			setBack(80);
		else
			setAhead(80);
		out.println("Hit wall in direction " + relativeAngle);
		execute();
	}

	public void onRobotDeath(RobotDeathEvent e) {
		int dude = seenEnemy(e.getName());
		if (dude >= 0) {
			if (dude == target)
				target = -1;

			enemies.remove(dude);
		}
		--enemiesLeft;
	}

	public void onScannedRobot(ScannedRobotEvent e) {
		Enemy dude;

		// update the enemies array
		int index = seenEnemy(e.getName());
		if (index >= 0)
			dude = enemies.get(index);
		else {
			dude      = new Enemy();
			dude.name = e.getName();
			out.println("Found new 'bot by the name of " + dude.name);
		}

		dude.   prevEnergy = (index >= 0 ? dude.energy : e.getEnergy());
		dude.       energy = e.getEnergy();
		dude.     distance = e.getDistance();
		dude.      bearing = e.getBearingRadians();

		// used for radar scanning in 1vs1
		detectionTime = 0;
		enemyAbsoluteBearing = getHeadingRadians() + e.getBearingRadians();

		if (index < 0) {
			enemies.add(dude);
			index = enemies.size() - 1;
		}

		boolean dudeCloserThanTarget;
		if (target >= 0 && target < enemies.size())
			dudeCloserThanTarget = (dude.distance <= 0.8*enemies.get(target).distance ? true : false);
		else
			// no previous target...
			dudeCloserThanTarget = true;

		if (dudeCloserThanTarget) {
			out.println("Changing target from " + ((target >= 0 && target < enemies.size()) ? enemies.get(target).name : "[null]") + " to " + ((index >= 0 && index < enemies.size()) ? enemies.get(index).name : "[null]"));
			target = index;
		} else if (enemiesLeft == 1)
			// can aim gun already here, no chance of getting confused due to multiple dudes
			setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(getHeadingRadians() + dude.bearing - getGunHeadingRadians()));
	}

	public void onWin(WinEvent e) {
		setTurnRadarRight(0);
		setTurnGunLeft   (Double.POSITIVE_INFINITY);
		setTurnRight     (Double.POSITIVE_INFINITY);

		for (;;) {
			if (getEnergy() > 0.1)
				setFire(0.1);
			execute();
		}
	}

	private void move() {
	}

	private void radar() {
		++detectionTime;

		if (enemiesLeft > 1)
			setTurnRadarRight(360);
		else {
			// only one dude left
			// lock radar on him, but if haven't seen him for some time rotate
			if (detectionTime < 5) {

				// thanks to http://robowiki.net/cgi-bin/robowiki?Radar
				double radarTurn = robocode.util.Utils.normalRelativeAngle(getRadarHeadingRadians() - enemyAbsoluteBearing);
				radarTurn += (radarTurn > 0 ? 1 : -1) * 0.04;
				setTurnRadarLeftRadians(radarTurn);

			} else
				setTurnRadarLeft(360);
		}
	}

	private void gun() {
		if (target >= 0 && target < enemies.size() && detectionTime < 10) {
			Enemy dude = enemies.get(target);

			// aim at enemy
			setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(getHeadingRadians() + dude.bearing - getGunHeadingRadians()));

			// at distance 1200 bullet == 0.1, at 200 it == 3
			bullet = -(29.0/10000)*dude.distance + 179.0/50;

			if (getEnergy() <= 0.1)
				bullet = 0;
			else if (getEnergy() < 10)
				bullet = 0.1;
		} else
			bullet = 0;

		if (target >= 0 && bullet > 0 && getGunHeat() < 0.1/* && getGunTurnRemaining() == 0*/)
// vs. 4 * myFirstRobot, 30 rounds each, 800*600:
// getGunTurn && setFire:  4851	1600	  0	2912	170	168	0	0	4	 4		4th
//               setFire:  5876	2100	 80	3295	200	200	0	2	1	12		2nd
// getGunTurn &&    fire:  5724	1850	 40	3366	227	241	0	1	6	 3		3rd
//                  fire: 10188	3150	120	6064	776	 77	0	3	8	10		1st

// vs. CassiusClay, PulsarMax, Shadow, same settings:
// getGunTurn && setFire: 1488	1150	 30	 291	 12	  4	0	1	2	16		4th
//               setFire: 1794	 900	  0	 880	  9	  3	0	0	2	14		2nd
// getGunTurn &&    fire: 1265	 950	  0	 302	  0	 13	0	0	4	11		3rd
//                  fire: 2127	 950	  0	1079	 67	 31	0	0	2	15		1st

			fire(bullet);
	}

	// ------------------------------------------------------ Utilities -------------------------------------------------------- \\

	// return index in enemies of enemy with name theName, or -1 if not found
	private int seenEnemy(String theName) {
		for (int i = 0; i < enemies.size(); ++i) {
			if (enemies.get(i).toString().equals(theName))
				return i;
		}

		return -1;
	}
}
