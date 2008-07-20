// File created: 2007-11-22 18:18:36

/*\_______________
|                 |
\-----------------/
/   / (  /
/   /----'
/***/

package deewiant.ammunition.guns.model;

import java.util.HashMap;
import java.util.Map;

import robocode.Bullet;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public abstract class Gun {
	// remember gun accuracy across rounds
	// assume no more than 2^31-1 shots...
	public static final class Accuracy {
		int shots, hits;
	}
	public static final Map<String, Accuracy>
		accuracy = new HashMap<String, Accuracy>();

	private final Accuracy acc = new Accuracy();
	private       String   name;
	protected     double   firePower;
	public        double   targetAngle;

	public Gun(final String s) {
		name = s;
		if (!accuracy.containsKey(name))
			accuracy.put(name, new Accuracy());
	}
	public final String getName() { return name; }

	public final void aim() {
		this.setSights();
		Global.bot.setTurnGunRightRadians(Utils.normalRelativeAngle(this.targetAngle - Global.bot.getGunHeadingRadians()));
	}

	protected abstract void setSights();

	public final void spewStatsAndReset() {
		if (acc.shots == 0)
			return;

		final Accuracy memory = accuracy.get(name);
		memory.shots += this.acc.shots;
		memory.hits  += this.acc.hits;

		Global.out.printf(
			"%s targeting:\n" +
			"Hit %%:       %d/%d = %f\n" +
			"Total hit %%: %d/%d = %f\n",
			name,
			   acc.hits,    acc.shots, (double)   acc.hits/   acc.shots,
			memory.hits, memory.shots, (double)memory.hits/memory.shots);

		acc.shots = acc.hits = 0;
	}

	public final void prime() {

		// so that firePower == 3 at distance 400 and 0.1 at 1100
		firePower = (32600.0 - 29.0 * Global.target.distance)/7000.0;
		firePower = Tools.between(firePower, 0.1, 3.0);

		// since damage = 4*power + 2*(power - 1)
		// we can simplify and solve to power = (damage + 2) / 6:
		// double neededPower = (Global.target.energy + 2) / 6;
		// but 2*(power - 1) is added only if power > 1, so:
		//if (neededPower < 1)
		//	neededPower = Global.target.energy / 4;
		// or simpler:
		if (Global.target.energy <= 16)
			firePower = Math.min(Global.target.energy / 4, (Global.target.energy + 2) / 6);

		if (Global.bot.getEnergy() <= 3.1)
			firePower = 0.1;
	}

	public final boolean allSet() {
		return
			Global.bot.getGunHeat() == 0                                       &&
			Global.bot.getTime() - Global.target.scanTime < Tools.LOCK_ADVANCE && // don't shoot dudes you haven't seen in a while, might not be connecting
			!(Global.bot.getOthers() == 1 && Global.target.energy < 0.1)       && // don't shoot if there's only one disabled dude left - ram instead
			Tools.near(targetAngle, Global.bot.getGunHeadingRadians());
	}

	public final Bullet fire() {
		++acc.shots;
		return Global.bot.setFireBullet(firePower);
	}

	public void hit(final Bullet b) {
		++acc.hits;
	}
}
