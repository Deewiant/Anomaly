package deewiant;
import robocode.*;

final public class Perception {
	private AdvancedRobot bot;
	static final int SCAN_RANGE = 1150; // empirical, approximate, currently unused
	int prevDir;

	public Perception(AdvancedRobot r) {
		bot = r;
	}

	public void perceive(Enemy enemy) {
		boolean melee = bot.getOthers() > 1;

		// lock if we have a target whom we've seen recently, and if we're capable of shooting it
		// in melee, lock only if we're about to shoot
		if (enemy != null && bot.getEnergy() > 0.1 && bot.getTime() - enemy.scanTime < 3 && (!melee || (melee && bot.getGunHeat() / bot.getGunCoolingRate() < 6)))
			lock(enemy);
		else
			bot.setTurnRadarLeftRadians(prevDir > 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
		return;
	}

	public void victoryDance() {
		// sometimes spins fast, sometimes slow. don't ask me why...
		bot.setTurnRadarRightRadians(prevDir > 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
	}

	private void lock(Enemy enemy) {
		double radarTurn = Utils.normaliseBearing(bot.getRadarHeadingRadians() - (bot.getHeadingRadians() + enemy.bearingRad));
		prevDir = (radarTurn > 0 ? 1 : -1);
		radarTurn += prevDir;

		bot.setTurnRadarLeftRadians(radarTurn * 0.42);
	}
}
