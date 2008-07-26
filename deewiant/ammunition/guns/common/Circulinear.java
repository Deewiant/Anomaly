// File created: 2007-11-22 18:49:39

package deewiant.ammunition.guns.common;

import java.awt.geom.Point2D;

import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

// http://www-128.ibm.com/developerworks/library/j-circular/
public final class Circulinear {
	private Circulinear() {}

	public static double calculate(
		final Enemy dude,
		final double bulletSpeed,
		final boolean circular
	) {
		Point2D guess = new Point2D.Double(dude.x, dude.y);

		if (!Tools.zero(dude.velocity)) {
			for (int i = 10; i-- > 0;) {
				final long time =
					Global.bot.getTime() +
					Math.round(Global.me.distance(guess) / bulletSpeed);

				if (circular)
					guess = dude.guessCircularPosition(time);
				else
					guess = dude.guessLinearPosition  (time);
			}
		}

		return Utils.normalAbsoluteAngle(Tools.atan2(guess, Global.me));
	}
}
