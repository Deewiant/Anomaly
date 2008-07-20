package deewiant;
import robocode.*;
import java.awt.geom.Point2D;
import java.io.PrintStream;

final public class Bombardier {
	private AdvancedRobot bot;
	private PrintStream   out;

	private double firePower;
	private long shots, hits;
    private static long totalShots = 0, totalHits = 0;

	public Bombardier(AdvancedRobot r) {
		bot = r;
		out = bot.out;

		shots = 0;
		hits = 0;
	}

	public void bombard(Enemy e) {
		if (e == null) return;

		choosePower(e);
		if (bot.getGunTurnRemaining() < 0.1) {
			takeAim(e);
			fire(e);
		}

		return;
	}

	public void victoryDance() {
		bot.setTurnGunRightRadians(Double.NEGATIVE_INFINITY);
	}

	public void spewTheStats() {
		totalShots += shots;
		totalHits  += hits;

		out.println(  "Bombardier statistics:" +
		            "\n----------------------" +
		            "\nHit %:       " + hits + "/" + shots + " = " + (double)hits / shots +
		            "\nTotal hit %: " + totalHits + "/" + totalShots + " = " + (double)totalHits / totalShots
		);
	}

	public void contemplateHit(BulletHitEvent e) {
		++hits;
	}

	private void choosePower(Enemy target) {
		firePower = Math.min(target.energy / 5, 3);
		if (target.energy < 16) {
			// what it'll take to kill target
			double powerNeeded = Math.min(target.energy / 4, (target.energy + 2) / 6);
			firePower = Math.min(firePower, powerNeeded);
		}
		firePower = Math.min(firePower, 1200 / target.distance);

		if (firePower > bot.getEnergy())
			firePower = 0.1; // the additional check in fire() makes sure we never get disabled
	}

	private void takeAim(Enemy target) {
		// http://www-128.ibm.com/developerworks/library/j-circular/

		long time, bulletTime;
		double bulletSpeed = 20 - 3 * firePower;
		Point2D.Double guess = new Point2D.Double(target.x, target.y);

		double mapHeight = bot.getBattleFieldHeight();
		double mapWidth  = bot.getBattleFieldWidth ();

		for (int i = 0; i < 50; ++i) {
			bulletTime = Math.round(Utils.distance(bot.getX(), bot.getY(), guess.x, guess.y) / bulletSpeed);
			time = bot.getTime() + bulletTime;
			guess = target.guessPosition(time);

			// assume that enemy stops at wall
			// 18 is wall thickness, I take it??
			if (guess.x > mapWidth - 18.0 || guess.x < 18.0 || guess.y > mapHeight - 18.0 || guess.y < 18.0) {
				guess.x = Math.min(Math.max(18.0, guess.x), mapWidth - 18.0);
				guess.y = Math.min(Math.max(18.0, guess.y), mapHeight - 18.0);
				break;
			}
		}

		double gunTurn = Math.PI/2 - Math.atan2(guess.y - bot.getY(), guess.x - bot.getX()) - bot.getGunHeadingRadians();
		bot.setTurnGunRightRadians(Utils.normaliseBearing(gunTurn));
	}

	private void fire(Enemy target) {
		if (bot.getGunHeat() == 0                          &&
		    bot.getEnergy() >= 0.2                         && // don't disable yourself
		    bot.getTime() - target.scanTime < 10           && // don't shoot dudes you haven't seen in a while, might not be connecting at all
		    !(bot.getOthers() == 1 && target.energy < 0.1)    // don't shoot if there's only one disabled dude left - ram instead
		) {
			bot.setFire(firePower);
			++shots;
		}
	}
}
