// File created: prior to 2005-12-01

package deewiant.ammunition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Map;

import robocode.Bullet;

import deewiant.ammunition.guns.*;
import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public final class Ammunition {

	private static final Gun[] guns = {
		new Circular(),
		new Linear(),
		new HeadOn()
	};
	private Gun gun;

	private Map<Bullet, Gun> shooter = new HashMap<Bullet, Gun>();

	public void munition() {
		if (
			Global.target == null ||
			Global.bot.getEnergy() < 0.2 ||
			Global.bot.getTime() - Global.target.scanTime >= 3*Tools.LOCK_ADVANCE
		) {
			// spin in the same direction as radar to speed it up
			Global.bot.setTurnGunRightRadians(Global.bot.getRadarTurnRemainingRadians() * Double.POSITIVE_INFINITY);
			return;
		}

		if (gun == null)
			setGun(selectGun());

		gun.prime();
		gun.aim  ();

		/* Virtual guns:
		 *    fire a virtual bullet from each gun every time we fire
		 *    fire an actual bullet from the currently chosen gun
		 *    pick the gun with the highest hit chance for next time
		 */
		if (gun.allSet()) {
			shooter.put(gun.fire(), gun);
			++Global.target.shotAtCount;

			//for (final Gun g : guns)
			//if (g != gun)
			//	gun.fireVirtual();

			final Gun g = selectGun();
			if (g != gun)
				setGun(g);
		}
	}
	private Gun selectGun() {
		Gun gun = guns[0];
  // 	for (int i = 1; i < guns.size(); ++i)
  // 		if (guns.get(i).accuracy > gun.accuracy)
  // 			gun = guns.get(i);
		return gun;
	}
	private void setGun(final Gun g) {
		gun = g;
		Global.out.printf("Gun set to: %s targeting\n", g.getName());
	}

	public void onBulletHit(final Bullet b) {
		shooter.get(b).hit(b);
	}

	public void victoryDance() {
		Global.bot.setTurnGunRightRadians(-Global.bot.getRadarTurnRemainingRadians());
	}

	public void spewTheStats() {
		for (Gun gun : guns)
			gun.spewStatsAndReset();
	}
}
