package deewiant;
import robocode.*;
import robocode.util.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

final public class Anomaly extends AdvancedRobot {
	private static final Color c = new Color(0x30, 0, 0200);

	private              Map        <String, Enemy> dudes;
	private              Bombardier                 bombardier;
	private              Propulsion                 propulsion;
	private              Perception                 perception;

	private              String target;

	private              boolean won;

	public void run() {
		super.setColors(c, c, null);
		super.setAdjustGunForRobotTurn(true);
		super.setAdjustRadarForGunTurn(true);
		super.setAdjustRadarForRobotTurn(true);
		won = false;

		dudes      = new HashMap<String, Enemy>(    );
		bombardier = new Bombardier            (this);
		propulsion = new Propulsion            (this);
		perception = new Perception            (this);

		while (!won) {
			super.setScanColor(getColour(super.getEnergy()));

			final Enemy target = dudes.get(this.target);
			perception.perceive(target);
			bombardier.bombard (target);
			propulsion.propel  (target);
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
		bombardier.victoryDance(perception.clockwise);
	}
	private void gameOver() {
		bombardier.spewTheStats();
		if (wallHits > 0)
			out.println(wallHits + " wall hits");
		clearAllEvents(); // to avoid printing twice if we win and then die
	}

	public void onScannedRobot(final ScannedRobotEvent e) {
		final Enemy oldDude = dudes.get(e.getName());
		final Enemy    dude = (oldDude == null) ? new Enemy() : oldDude;

		final double heading = e.getHeadingRadians();
		final long now = super.getTime();

		dude.newInfo();

		dude.deltaHeading = Utils.normalRelativeAngle(heading - dude.heading) / (now - dude.scanTime);

		dude.heading  = heading;
		dude.bearing  = e.getBearingRadians();
		dude.distance = e.getDistance();
		dude.scanTime = now;
		dude.velocity = e.getVelocity();
		dude.energy   = e.getEnergy();
		dude.name     = e.getName();

		final double absoluteBearing = Utils.normalAbsoluteAngle(super.getHeadingRadians() + dude.bearing);

		dude.x        = super.getX() + dude.distance * Math.sin(absoluteBearing);
		dude.y        = super.getY() + dude.distance * Math.cos(absoluteBearing);

		double wallDamage = 0;
		final double prevSpeed = Math.abs(dude.prevVelocity);
		if (dude.velocity == 0 && prevSpeed > 2.0)
			wallDamage = Math.max(0, prevSpeed / 2 - 1);

		final double deltaEnergy = dude.prevEnergy - dude.energy - wallDamage;
		dude.firePower = (deltaEnergy >= 0.1 && deltaEnergy <= 3.0 ? deltaEnergy : -1);
		if (dude.firePower != -1)
			dude.lastShootTime = now;

		if (target == null || preferableTarget(dude))
			target = dude.name;

		dudes.put(dude.name, dude);

		updateDudes();

		propulsion.onScannedRobot(dude);
	}

	private void updateDudes() {
		propulsion.updateDudes(dudes.values());
	}

	private boolean preferableTarget(final Enemy dude) {
		final Enemy target = dudes.get(this.target);
		return (
			dude.scanTime - target.scanTime >= 100 || (
				!perception.readyToLock() && // don't switch targets if we're about to lock (i.e. shoot)
				target.distance * 0.9 > dude.distance
			)
		);
	}

	public void onRobotDeath(final RobotDeathEvent e) {
		final String name = e.getName();
		if (dudes.containsKey(name))
			dudes.remove(name);
		if (name.equals(target))
			target = null;

		updateDudes();
	}

	private static long wallHits = 0;
	public void onBulletHit(final BulletHitEvent e) { bombardier.onBulletHit(e); }
	public void onHitWall  (final HitWallEvent e)   { ++wallHits; }

	private Color getColour(double energy) {
		// newValue = (oldValue - oldMinimum) * (newMaximum - newMinimum) / (oldMaximum - oldMinimum) + newMinimum
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

	public void onPaint(Graphics2D g) {
		if (perception != null) perception.onPaint(g);
	}
}
