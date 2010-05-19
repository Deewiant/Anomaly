// File created: 2007-11-22 18:18:36

/*\_______________
|                 |
\-----------------/
/   / (  /
/   /----'
/***/

package deewiant.ammunition.guns.model;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import robocode.Bullet;
import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;
import static deewiant.common.VirtualBullets.VirtualBullet;

public abstract class Gun {
	// assume no more than 2^31-1 shots...
	public static final class Accuracy {
		int shots, hits;
	}

	// these two Maps are static so that they are remembered between rounds
	// their contents are updated in spewStatsAndReset() after every round
	// (otherwise acc and vacc are used, to avoid lookups)
	//
	// the String key is the name of the Gun

	public static final Map<String, Accuracy>
		accuracy = new HashMap<String, Accuracy>();

	// Virtual accuracy is per-enemy, using Enemy.id as the index.
	//
	// Note that it includes real shots and hits as well as virtual ones.
	private static final Map<String, List<Accuracy>>
		virtualAccuracy = new HashMap<String, List<Accuracy>>();

	private       boolean updatedYet = false;

	private final Accuracy       acc  = new Accuracy();
	private final List<Accuracy> vacc;
	private       String   name;
	private       double   firePower;
	private       double   targetAngle;

	public Gun(final String s) {
		name = s;
		if (!accuracy.containsKey(name)) {
			vacc = new ArrayList<Accuracy>();

			       accuracy.put(name, new Accuracy ());
			virtualAccuracy.put(name, vacc);
		} else
			vacc = virtualAccuracy.get(name);
	}
	public final String getName() { return name; }

	public final void aim() {
		this.targetAngle =
			this.setSights(Global.target, Rules.getBulletSpeed(firePower));

		Global.bot.setTurnGunRightRadians(
			Utils.normalRelativeAngle(
				this.targetAngle - Global.bot.getGunHeadingRadians()));
	}

	protected abstract double setSights(final Enemy dude, final double bSpeed);

	public final void spewStatsAndReset() {

		final StringBuilder s = new StringBuilder();
		final Formatter f = new Formatter(s);

		if (acc.shots != 0) {
			final Accuracy memory = updateBattleStats();

			f.format(
				"%s hit %%:\n" +
				"  Round:  %d/%d = %f\n" +
				"  Battle: %d/%d = %f\n",
				name,
			   	acc.hits,    acc.shots, (double)   acc.hits/   acc.shots,
				memory.hits, memory.shots, (double)memory.hits/memory.shots);

			acc.shots = acc.hits = 0;

			updatedYet = false;
		}

		Global.out.print(s);

		final List<Accuracy> virtualMemory = virtualAccuracy.get(name);
		for (int i = 0; i < vacc.size(); ++i) {
			virtualMemory.get(i).shots = vacc.get(i).shots;
			virtualMemory.get(i).hits  = vacc.get(i).hits;
		}
	}

	public final int getShots      () { return acc.shots; }
	public final int getHits       () { return acc.hits;  }
	public final int getBattleShots() { return updateBattleStats().shots; }
	public final int getBattleHits () { return updateBattleStats().hits;  }

	private final Accuracy updateBattleStats() {
		final Accuracy memory = accuracy.get(name);
		if (!updatedYet && acc.shots != 0) {
			memory.shots += this.acc.shots;
			memory.hits  += this.acc.hits;
			updatedYet = true;
		}
		return memory;
	}

	public final void prime() { firePower = chooseFirePower(Global.target); }

	private final double chooseFirePower(final Enemy dude) {
		double fp;

		// so that firePower == 3 at distance 400 and 0.1 at 1100
		fp = (32600.0 - 29.0 * dude.distance)/7000.0;
		fp = Tools.between(fp, 0.1, 3.0);

		// since damage = 4*power + 2*(power - 1)
		// we can simplify and solve to power = (damage + 2) / 6:
		// double neededPower = (Global.target.energy + 2) / 6;
		// but 2*(power - 1) is added only if power > 1, so:
		//if (neededPower < 1)
		//	neededPower = Global.target.energy / 4;
		// or simpler:
		if (dude.energy <= 16)
			fp = Math.min(dude.energy / 4, (dude.energy + 2) / 6);

		if (Global.bot.getEnergy() <= 3.1)
			fp = 0.1;

		return fp;
	}

	public final boolean allSet() {
		return
			Global.bot.getGunHeat() == 0                                       &&

			 // don't shoot dudes we haven't seen in a while, might not be
			 // connecting
			Global.bot.getTime() - Global.target.scanTime < Tools.LOCK_ADVANCE &&

			 // don't shoot if there's only one disabled dude left - ram instead
			!(Global.bot.getOthers() == 1 && Global.target.energy < 0.1)       &&

			Tools.near(targetAngle, Global.bot.getGunHeadingRadians());
	}

	public final void newEnemy(final Enemy dude) {
		assert (vacc.size() == dude.id);
		vacc.add(new Accuracy());
	}

	// This should only be called if the victim was the intended target of the
	// bullet
	public final void hit(final Bullet b, final Enemy victim) {
		++acc.hits;
		++vacc.get(victim.id).hits;
	}

	public final void virtualHit(final Enemy dude) {
		++vacc.get(dude.id).hits;
	}

	public final Bullet fire() {
		++acc.shots;
		++vacc.get(Global.target.id).shots;
		return Global.bot.setFireBullet(firePower);
	}

	public final VirtualBullet fireVirtual(final Enemy dude) {
		++vacc.get(dude.id).shots;

		final double fp = chooseFirePower(dude);

		return new VirtualBullet(
			Global.me, fp,
			setSights(dude, Rules.getBulletSpeed(fp))
		);
	}

	public final double totalAccuracy(final Enemy dude) {
		final Accuracy dudeAcc = vacc.get(dude.id);

		assert (dudeAcc.hits <= dudeAcc.shots);

		// so that when shots is 0, not infinity...
		if (dudeAcc.hits == 0)
			return 0;
		else {
			assert (dudeAcc.shots != 0);
			return (double)dudeAcc.hits / dudeAcc.shots;
		}
	}
}
