// File created: prior to 2005-10-02

package deewiant;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

		Global.dudes = new HashMap<String, Enemy>(super.getOthers(), 1);
		Global.bot   = this;
		Global.out   = this.out;
		Global.mapWidth  = super.getBattleFieldWidth ();
		Global.mapHeight = super.getBattleFieldHeight();

		ammunition = new Ammunition();
		propulsion = new Propulsion();
		perception = new Perception();

		while (!won) {
			super.setScanColor(getColour(super.getEnergy()));

			updateDudes();

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
		dude.name     = e.getName();

		dude.absBearing =
			Utils.normalAbsoluteAngle(super.getHeadingRadians() + dude.bearing);

		dude.x        = super.getX() + dude.distance * Math.sin(dude.absBearing);
		dude.y        = super.getY() + dude.distance * Math.cos(dude.absBearing);

		double wallDamage = 0;
		final double prevSpeed = Math.abs(dude.prevVelocity);
		if (dude.velocity == 0 && prevSpeed > 2.0)
			wallDamage = Math.max(0, prevSpeed / 2 - 1);

		final double deltaEnergy = dude.prevEnergy - dude.energy - wallDamage;
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

		dude.boundingBox.setRect(
			dude.x - Tools.BOT_WIDTH /2,
			dude.y - Tools.BOT_HEIGHT/2,
			Tools.BOT_WIDTH,
			Tools.BOT_HEIGHT);

		dude.boundingBox =
			AffineTransform
			.getRotateInstance(dude.heading, dude.getX(), dude.getY())
			.createTransformedShape(dude.boundingBox)
			.getBounds2D();

		dude.vicinity.setRect(
			dude.x - Tools.BOT_WIDTH *1.25,
			dude.y - Tools.BOT_HEIGHT*1.25,
			Tools.BOT_WIDTH  * 2.5,
			Tools.BOT_HEIGHT * 2.5);

		dude.old = dude.positionUnknown = false;
		dude.justSeen = true;

		Global.dudes.put(dude.name, dude);

		propulsion.onScannedRobot(dude);
	}

	private void updateDudes() {
		for (final Enemy dude : Global.dudes.values()) {
			final double timediff = super.getTime() - dude.scanTime;
			if (timediff > 360 / Rules.RADAR_TURN_RATE)
				dude.old = true;
			else if (timediff > 1)
				dude.justSeen = false;
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
	}

	private Enemy getDude(final String name) {
		final Enemy oldDude = Global.dudes.get(name);

		Enemy dude;
		if (oldDude == null)
			dude = new Enemy();
		else {
			dude = oldDude;
			dude.newInfo();
		}

		return dude;
	}

	public void onRobotDeath(final RobotDeathEvent e) {
		if (Global.dudes.remove(e.getName()) == Global.target)
			newTarget(null);
	}

	public void onBulletHit(final BulletHitEvent e) {
		ammunition.onBulletHit(e.getBullet());

		final Enemy dude = getDude(e.getName());

		dude.energy = e.getEnergy();
		++dude.hitCount;
	}

	public void onHitByBullet(final HitByBulletEvent e) {
		final Enemy dude = getDude(e.getName());

		++dude.hitMeCount;

		final double power = e.getPower();
		double damage = 4 * power;
		if (power > 1)
			damage += 2 * (power-1);

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
		// new = (old - oldMin) * (newMax - newMin) / (oldMax - oldMin) + newMin
		// hue ranges from 0-360; saturation, luminance are 0-1

		if (energy < 200.1)
			// energy 0-200, hue 0-240, sat 1, lum 0.5
			return Tools.HSLtoRGB(6f*(float)energy/5f, 1f, 0.5f);
		else if (energy < 250.1)
			// energy 200-250, hue 240, sat 1, lum 0.5-1
			return Tools.HSLtoRGB(240f, 1f, ((float)energy - 150f)/100f);
		else
			return Color.WHITE;
	}

	public void onPaint(final Graphics2D g) {
		if (perception != null) perception.onPaint(g);
		if (propulsion != null) propulsion.onPaint(g);

		if (!PAINT_ENEMIES)
			return;

		for (final Enemy dude : Global.dudes.values()) {

			if (dude.old)
				g.setColor(Color.GRAY);
			else if (dude.positionUnknown)
				g.setColor(Color.ORANGE);
			else if (dude == Global.target)
				g.setColor(Color.RED);
			else
				g.setColor(Color.WHITE);

			g.draw(dude.boundingBox);

			g.setColor(Color.GRAY);
			g.draw(dude.vicinity);
		}
	}

	private static final boolean
		PAINT_ENEMIES = true;
}
