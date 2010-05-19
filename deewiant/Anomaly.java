// File created: prior to 2005-10-02

package deewiant;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import robocode.AdvancedRobot;
import robocode.BulletHitEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import robocode.util.Utils;

import deewiant.ammunition.Ammunition;
import deewiant.propulsion.Propulsion;
import deewiant.perception.Perception;
import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public final class Anomaly extends AdvancedRobot {
	private static final Color
		body   = new Color(010, 045, 103),
		turret = new Color(0x30, 0, 0200),
		radar  = null;

	private Ammunition ammunition;
	private Propulsion propulsion;
	private Perception perception;

	private boolean won = false;

	public void run() {
		super.setColors(body, turret, radar);
		super.setAdjustGunForRobotTurn(true);
		super.setAdjustRadarForGunTurn(true);
		super.setAdjustRadarForRobotTurn(true);

		Global.bot       = this;
		Global.out       = this.out;
		Global.mapWidth  = super.getBattleFieldWidth ();
		Global.mapHeight = super.getBattleFieldHeight();
		Global.dudes.ensureCapacity(super.getOthers());

		ammunition = new Ammunition();
		propulsion = new Propulsion();
		perception = new Perception();

		while (!won) {
			super.setScanColor(getColour(super.getEnergy()));

			updateDudes();
			Global.me.setLocation(super.getX(), super.getY());

			perception.perceive();
			ammunition.munition();
			propulsion.propulse();
			execute();
		}
	}

	public void onDeath(final DeathEvent e) {
		gameOver();
	}
	public void onWin(final WinEvent e) {
		won = true;
		gameOver();
		propulsion.victoryDance();
		perception.victoryDance();
		ammunition.victoryDance();
	}
	private void gameOver() {
		newTarget(null);
		ammunition.spewTheStats();
		if (wallHits > 0)
			Global.out.println(wallHits + " wall hits");
		if (enemyHits > 0)
			Global.out.println(enemyHits + " rams");
		super.clearAllEvents(); // to avoid printing twice if we win and then die
	}

	public void onScannedRobot(final ScannedRobotEvent e) {
		final Enemy dude = getDude(e.getName());

		dude.dead = false;

		final double heading = e.getHeadingRadians();
		final long now = super.getTime();

		dude.deltaHeading =
			Utils.normalRelativeAngle(heading - dude.heading) /
			(now - dude.scanTime);

		dude.heading  = heading;
		dude.bearing  = e.getBearingRadians();
		dude.distance = e.getDistance();
		dude.scanTime = now;
		dude.velocity = e.getVelocity();
		dude.energy   = e.getEnergy();

		dude.absBearing =
			Utils.normalAbsoluteAngle(super.getHeadingRadians() + dude.bearing);

		dude.x        = super.getX() + dude.distance * Math.sin(dude.absBearing);
		dude.y        = super.getY() + dude.distance * Math.cos(dude.absBearing);

		double wallDamage = 0;
		final double prevSpeed = Math.abs(dude.prevVelocity);
		if (dude.velocity == 0 && prevSpeed > 2.0)
			wallDamage = Math.max(0, prevSpeed / 2 - 1);

		double deltaEnergy = dude.prevEnergy - dude.energy - wallDamage;
		if (dude.justHit)
			deltaEnergy -= dude.myLastHit;

		dude.firePower =
			deltaEnergy >= 0.1 && deltaEnergy <= 3.0
				? deltaEnergy
				: -1;

		if (dude.firePower != -1) {
			dude.lastShootTime = now;
			++dude.shotCount;
		}

		if (Global.target == null || preferableTarget(dude))
			newTarget(dude);

		Tools.setBotBox(dude.boundingBox, dude);

		dude.vicinity.setRect(
			dude.x - Tools.BOT_WIDTH *1.25,
			dude.y - Tools.BOT_HEIGHT*1.25,
			Tools.BOT_WIDTH  * 2.5,
			Tools.BOT_HEIGHT * 2.5);

		dude.old = dude.positionUnknown = false;
		dude.justSeen = true;

		propulsion.onScannedRobot(dude);
	}

	private void updateDudes() {
		final long now = super.getTime();

		for (final Enemy dude : Global.dudes) if (!dude.dead) {

			final long timediff = now - dude.scanTime;
			if (timediff > 0) {
				dude.justSeen = dude.justHit = false;

				if (timediff > 360 / Rules.RADAR_TURN_RATE)
					dude.old = dude.positionUnknown = true;

				dude.setGuessPosition(now);
			} else {
				dude.guessedPos  = dude;
				dude.guessedBBox.setRect(dude.boundingBox);
			}

			if (PAINT_ENEMIES && !dude.old) {
				final Graphics2D g = super.getGraphics();

				g.setColor(Color.GRAY);
				g.draw(dude.vicinity);

				g.setColor(Color.LIGHT_GRAY);
				g.draw(dude.guessedBBox);

				if (dude.positionUnknown)
					g.setColor(Color.ORANGE);
				else if (dude == Global.target)
					g.setColor(Color.RED);
				else
					g.setColor(Color.WHITE);

				g.draw(dude.boundingBox);
			}
		}
	}

	private boolean preferableTarget(final Enemy dude) {
		return (
			dude.scanTime - Global.target.scanTime > 2*Tools.LOCK_ADVANCE || (
				dude.distance < 0.9*Global.target.distance &&

				// don't switch targets if about to shoot
				!perception.readyToLock()));
	}
	private void newTarget(final Enemy dude) {
		Global.target = dude;
		if (dude != null)
			Global.out.printf("Now targeting: %s\n", dude.name);
		ammunition.newTarget(dude);
	}

	private Enemy getDude(final String name) {
		final Integer id = Global.dudeIds.get(name);

		Enemy dude;
		if (id == null) {
			dude = new Enemy();

			dude.name = name;
			dude.id = Global.id++;
			Global.dudeIds.put(name, dude.id);
			Global.dudes.add(dude);

			ammunition.newEnemy(dude);
		} else {
			dude = Global.dudes.get(id);
			dude.newInfo();
		}

		return dude;
	}

	public void onRobotDeath(final RobotDeathEvent e) {
		final Integer i = Global.dudeIds.get(e.getName());
		if (i != null) {
			final Enemy dude = Global.dudes.get(i);
			if (dude == Global.target)
				newTarget(null);

			dude.dead = dude.positionUnknown = dude.old = true;
			dude.justSeen = dude.justHit = false;
		}
	}

	public void onBulletHit(final BulletHitEvent e) {
		ammunition.onBulletHit(e.getBullet());

		final Enemy dude = getDude(e.getName());

		dude.energy    = e.getEnergy();
		dude.justHit   = true;
		dude.myLastHit = Rules.getBulletDamage(e.getBullet().getPower());
		++dude.hitCount;
	}

	public void onHitByBullet(final HitByBulletEvent e) {
		final Enemy dude = getDude(e.getName());

		++dude.hitMeCount;

		final double damage = Rules.getBulletDamage(e.getPower());

		dude.hurtMe        += damage;
		Global.damageTaken += damage;

		if (dude.old) {
			dude.old = false;
			dude.bearing = e.getBearingRadians();
			perception.hiddenDudeAt(dude.bearing);
		}
	}

	private static int enemyHits = 0;
	public void onHitRobot(final HitRobotEvent e) {
		final Enemy dude = getDude(e.getName());

		if (e.isMyFault())
			++enemyHits;

		dude.bearing = e.getBearingRadians();
		dude.energy  = e.getEnergy();
	}

	private static int wallHits = 0;
	public void onHitWall(final HitWallEvent e) { ++wallHits; }

	private Color getColour(final double energy) {
		if (energy < 200.1)
			return Tools.HSLtoRGB(
				Tools.fromToRange(
					0f, 200f,
					0f, 240f,
					(float)energy),
				1f,
				0.5f);

		else if (energy < 250.1)
			return Tools.HSLtoRGB(
				240f,
				1f,
				Tools.fromToRange(
					200f, 250f,
					0.5f, 1f,
					(float)energy));
		else
			return Color.WHITE;
	}

	public void onPaint(final Graphics2D g) {
		if (perception != null) perception.onPaint(g);
		if (propulsion != null) propulsion.onPaint(g);
	}

	private static final boolean
		PAINT_ENEMIES = true;
}
