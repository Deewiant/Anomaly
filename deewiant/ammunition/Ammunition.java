// File created: prior to 2005-12-01

package deewiant.ammunition;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import robocode.Bullet;

import deewiant.ammunition.guns.*;
import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;
import static deewiant.common.Enemy.VirtualBullet;
import static deewiant.common.Enemy.VirtualGun;

public final class Ammunition {

	private static final Gun[] guns = {
		new Circular(),
		new Linear(),
		new HeadOn()
	};
	private Gun gun;

	private final Map<Bullet, Gun>   shooter = new HashMap<Bullet, Gun>();
	private final Map<Bullet, Enemy> intendedTarget =
		new HashMap<Bullet,Enemy>();


	public void newEnemy(final Enemy dude) {
		dude.virtualGuns = new VirtualGun[guns.length];

		for (int i = 0; i < guns.length; ++i) {
			guns[i].newEnemy(dude);
			dude.virtualGuns[i] = new VirtualGun();
			dude.virtualGuns[i].gun     = guns[i];
			dude.virtualGuns[i].bullets =
				new ArrayDeque<VirtualBullet>(guns.length * 8);
		}
	}

	public void munition() {
		updateVirtuals();

		if (
			Global.target == null ||
			Global.bot.getEnergy() < 0.2 ||
			Global.bot.getTime() - Global.target.scanTime >= 3*Tools.LOCK_ADVANCE
		) {
			// spin in the same direction as radar to speed it up
			Global.bot.setTurnGunRightRadians(
				Global.bot.getRadarTurnRemainingRadians() *
				Double.POSITIVE_INFINITY);
			return;
		}

		if (gun == null)
			selectGun(Global.target);

		gun.prime();
		gun.aim  ();

		/* Virtual guns:
		 *    fire a virtual bullet from each gun every time we fire
		 *    fire an actual bullet from the currently chosen gun
		 *    pick the gun with the highest hit chance for next time
		 */
		if (gun.allSet()) {
			final Bullet b = gun.fire();
			shooter.put(b, gun);
			intendedTarget.put(b, Global.target);
			++Global.target.shotAtCount;

			for (final Enemy dude : Global.dudes)
			if (!dude.positionUnknown)
			for (final VirtualGun vg : dude.virtualGuns)
			if (vg.gun != gun)
				vg.bullets.add(vg.gun.fireVirtual(dude));

			selectGun(Global.target);
		}
	}
	private void selectGun(final Enemy target) {
		final Gun best = bestGun(target);
		if (best != this.gun) {
			Global.out.printf("Gun set to: %s\n", best.getName());
			this.gun = best;
		}
	}
	private Gun bestGun(final Enemy dude) {
		Gun gun = this.gun == null ? guns[0] : this.gun;
		double bestAccuracy = gun.totalAccuracy(dude);

   	for (int i = 0; i < guns.length; ++i) {
   		final Gun g = guns[i];
   		if (g != gun) {
   			final double acc = g.totalAccuracy(dude);
   			if (acc > bestAccuracy) {
   				gun = g;
   				bestAccuracy = acc;
   			}
   		}
   	}
		return gun;
	}

	public void newTarget(final Enemy target) {
		if (target != null)
			selectGun(target);
	}

	public void onBulletHit(final Bullet b) {
		final String victim  = b.getVictim();
		final Enemy intended = intendedTarget.get(b);

		if (victim == intended.name)
			shooter.get(b).hit(b, intended);
	}

	public void victoryDance() {
		Global.bot.setTurnGunRightRadians(
			-Global.bot.getRadarTurnRemainingRadians());
	}

	public void spewTheStats() {
		int shots = 0, hits = 0, shotsB = 0, hitsB = 0;
		for (Gun gun : guns) {
			shots  += gun.getShots();
			hits   += gun.getHits();
			shotsB += gun.getBattleShots();
			hitsB  += gun.getBattleHits();
			gun.spewStatsAndReset();
		}

		final StringBuilder s = new StringBuilder();
		final Formatter f = new Formatter(s);

		f.format(
			"Total hit %%:\n" +
			"  Round:  %d/%d = %f\n" +
			"  Battle: %d/%d = %f\n",
			hits,  shots,  (double)hits  / shots,
			hitsB, shotsB, (double)hitsB / shotsB);

		f.format("Best guns:\n");
		for (final Enemy dude : Global.dudes) {
			final Gun best = bestGun(dude);

			f.format(
				"  %s: %s with virtual accuracy %f\n",
				dude.name, best.getName(), best.totalAccuracy(dude));
		}
		Global.out.print(s);
	}

	private void updateVirtuals() {
		for (Enemy dude : Global.dudes)
		if (!dude.dead)
		for (VirtualGun vgun : dude.virtualGuns) {

			if (vgun.bullets.size() == 0)
				continue;

			int removableBottom = -1, removableTop = -1;
			boolean irremovableBottom = false, irremovableTop = true;

			int i = 0;
			for (VirtualBullet bul : vgun.bullets) {

				boolean removable = false;
				if (
					bul.alive  &&
					bul.x >= 0 && bul.x <= Global.mapWidth &&
					bul.y >= 0 && bul.y <= Global.mapHeight
				) {
					bul.move();
					if (dude.boundingBox.intersectsLine(bul.line)) {
						vgun.gun.virtualHit(dude);
						removable = true;
					} else {
						irremovableBottom = true;
						irremovableTop    = true;
					}
				} else
					removable = true;

				if (removable) {
					bul.alive = false;
					if (!irremovableBottom)
						removableBottom = i;

					if (irremovableTop) {
						removableTop = i;
						irremovableTop = false;
					}
				}
				++i;
			}

			// remove as many as possible from bottom and top of deque

			if (removableBottom == vgun.bullets.size()-1) {
				vgun.bullets.clear();
				continue;
			}
			while (removableBottom-- >= 0)
				vgun.bullets.removeFirst();

			if (!irremovableTop) {
				removableTop = vgun.bullets.size() - removableTop;
				while (removableTop-- >= 0)
					vgun.bullets.removeLast();
			}

		}
	}
}
