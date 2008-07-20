// File created: prior to 2005-12-01

package deewiant;

import robocode.*;
import robocode.util.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

final public class Bombardier {
	private final AdvancedRobot bot;
	private final PrintStream   out;
	private final Gun           gun;

	// remember gun accuracy across rounds
	protected final class Accuracy {
		long shots, hits;
	}
	private static Map<String, Accuracy> accuracy = new HashMap<String, Accuracy>();

	public Bombardier(AdvancedRobot r) {
		bot = r;
		out = bot.out;

		gun = new CircuLinear();
	}

	public void bombard(final Enemy target) {
		if (target == null)
			return;
		if (bot.getEnergy() < 0.2) {
			// panic!
			bot.setTurnGunRightRadians(Double.POSITIVE_INFINITY);
			return;
		}

		gun.prime(target);
		gun.aim  (target);
		gun.fire (target);
	}

	public void victoryDance(boolean anticlockwise) {
		bot.setTurnGunRightRadians(anticlockwise ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
	}

	public void spewTheStats() {
		gun.spewStats();
	}

	public void onBulletHit(BulletHitEvent e) { gun.onBulletHit(e); }

	private abstract class Gun {
		private long hits = 0, shots = 0;
		protected double firePower;
		public    double targetAngle;
		abstract public String getName();

		Gun(String s) {
			out.println("Gun set to: " + s + " targeting");
			if (!Bombardier.accuracy.containsKey(getName()))
				Bombardier.accuracy.put(getName(), new Accuracy());
		}

		final public void aim(Enemy target) {
			this.setSights(target);
			bot.setTurnGunRightRadians(Utils.normalRelativeAngle(this.targetAngle - bot.getGunHeadingRadians()));
		}

		abstract protected void setSights(Enemy e);

		final public void spewStats() {
			Accuracy memory = accuracy.get(getName());
			memory.shots += this.shots;
			memory.hits  += this.hits;

			out.println(getName() + " targeting:" +
			            "\nHit %:       " + hits + "/" + shots + " = " + (double)hits / shots +
			            "\nTotal hit %: " + memory.hits + "/" + memory.shots + " = " + (double)memory.hits / memory.shots
			);
		}

		public void onBulletHit(final BulletHitEvent e) { ++hits; }

		public void prime(final Enemy target) {
			/*firePower = Math.min(target.energy / 5, 3);
			if (target.energy < 16) {
				// what it'll take to kill target
				double powerNeeded = Math.min(target.energy / 4, (target.energy + 2) / 6);
				firePower = Math.min(firePower, powerNeeded);
			}
			firePower = Math.min(firePower, 1200 / target.distance);

			if (firePower > bot.getEnergy())
				firePower = 0.1; // the additional check in fire() makes sure we never get disabled
			*/

			// so that firePower == 3 at distance 200 and 0.1 at 800
			firePower = (23800.0 - 29.0 * target.distance)/6000.0;
			firePower = Tools.between(firePower, 0.1, 3.0);

			// since damage = 4*power + 2*(power - 1)
			// we can simplify and solve to power = (damage + 2) / 6:
			// double neededPower = (target.energy + 2) / 6;
			// but 2*(power - 1) is added only if power > 1, so:
			//if (neededPower < 1)
			//	neededPower = target.energy / 4;
			// or simpler:
			if (target.energy <= 16)
				firePower = Math.min(target.energy / 4, (target.energy + 2) / 6);

			if (bot.getEnergy() <= 3.1)
				firePower = 0.1;
		}

		public final void fire(final Enemy target) {
			if (bot.getGunHeat() == 0                          &&
			    bot.getEnergy() > 0.1                          && // don't disable yourself
			    bot.getTime() - target.scanTime < 10           && // don't shoot dudes you haven't seen in a while, might not be connecting
			    !(bot.getOthers() == 1 && target.energy < 0.1) &&   // don't shoot if there's only one disabled dude left - ram instead
			    Tools.near(targetAngle, bot.getGunHeadingRadians())
			) {
				bot.setFire(firePower);
				++shots;
			}
		}
	}

	private final class CircuLinear extends Gun {
		// hooray for Java's lack of ability to use a constant here
		public String getName() { return "Circular/Linear"; }
		CircuLinear() { super("Circular/Linear"); }

		public void setSights(final Enemy target) {
			// http://www-128.ibm.com/developerworks/library/j-circular/

			long time, bulletTime;
			final double bulletSpeed = Tools.bulletSpeed(firePower);
			      Point2D.Double guess = new Point2D.Double(target.x, target.y);
			final Point2D.Double me    = new Point2D.Double(bot.getX(), bot.getY());

			final double mapHeight = bot.getBattleFieldHeight();
			final double mapWidth  = bot.getBattleFieldWidth ();

			if (target.velocity != 0) {
				for (int i = 0; i < 10; ++i) {
					bulletTime = Math.round(me.distance(guess) / bulletSpeed);
					time = bot.getTime() + bulletTime;
					guess = target.guessPosition(time);

					// assume that enemy stops at wall
					if (guess.x > mapWidth - 18.0 || guess.x < 18.0 || guess.y > mapHeight - 18.0 || guess.y < 18.0) {
						guess.x = Tools.between(guess.x, 18.0, mapWidth  - 18.0);
						guess.y = Tools.between(guess.y, 18.0, mapHeight - 18.0);
					}
				}
			}

			super.targetAngle = Utils.normalAbsoluteAngle(Tools.atan2(guess, me));
		}
	}
}
