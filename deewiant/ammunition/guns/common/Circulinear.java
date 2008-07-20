// File created: 2007-11-22 18:49:39

package deewiant.ammunition.guns.common;

import java.awt.geom.Point2D;

import robocode.util.Utils;

import deewiant.common.Global;
import deewiant.common.Tools;

// http://www-128.ibm.com/developerworks/library/j-circular/
public final class Circulinear {
	private Circulinear() {}

	public static double calculate(
		final double firePower,
		final boolean circular
	) {
		final double bulletSpeed = Tools.bulletSpeed(firePower);
		      Point2D guess = new Point2D.Double(Global.target.x, Global.target.y);
		final Point2D me    = new Point2D.Double(Global.bot.getX(), Global.bot.getY());

		if (!Tools.zero(Global.target.velocity)) {
			for (int i = 10; i-- > 0;) {
				final long time = Global.bot.getTime() + Math.round(me.distance(guess) / bulletSpeed);
				if (circular)
					guess = Global.target.guessCircularPosition(time);
				else
					guess = Global.target.guessLinearPosition  (time);

				// assume that enemy stops at wall
				if (guess.getX() > Global.mapWidth  - 18.0 || guess.getX() < 18.0)
					guess.setLocation(
						Tools.between(guess.getX(), 18.0, Global.mapWidth - 18.0),
						guess.getY());

				if (guess.getY() > Global.mapHeight - 18.0 || guess.getY() < 18.0)
					guess.setLocation(
						guess.getX(),
						Tools.between(guess.getY(), 18.0, Global.mapHeight - 18.0));
			}
		}

		return Utils.normalAbsoluteAngle(Tools.atan2(guess, me));
	}
}
