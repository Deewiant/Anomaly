// File created: 2007-11-22 18:54:17

package deewiant.propulsion.engines.model;

import java.awt.geom.Point2D;
import java.awt.Graphics2D;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public abstract class Engine {
	public final String name;
	public Engine(final String s) { name = s; }

	public abstract void move();

	public void onScannedRobot(final Enemy dude) {}
	public void onFired       (final Enemy dude) {}

	public void gameOver() {}

	protected final void goTo(final Point2D destination) {

		double distance = Global.me.distance(destination);

		if (turnToward(destination))
			distance *= -1;

		// don't hit the wall while turning

		final Point2D currentPath =
			Tools.projectVector(
				Global.me,
				Global.bot.getHeadingRadians(),
				Tools.between(distance, -20, 20));

		if (
			Math.abs(Global.bot.getTurnRemainingRadians()) > 0 && (
				currentPath.getX() < Tools.BOT_WIDTH *1.5 || currentPath.getX() > Global.mapWidth  - Tools.BOT_WIDTH *1.5 ||
				currentPath.getY() < Tools.BOT_HEIGHT*1.5 || currentPath.getY() > Global.mapHeight - Tools.BOT_HEIGHT*1.5
			)
		)
			distance = 0;

		Global.bot.setAhead(distance);

		// cheers to AaronR
		Global.bot.setMaxVelocity(
			(240/Math.PI) * (
				(Math.PI/18) -
			 	Math.min(
			 		Math.abs(Global.bot.getTurnRemainingRadians()),
			 		Math.PI/18)));
	}

	protected final boolean turnToward(final Point2D pt) {
		return turnToward(Utils.normalAbsoluteAngle(Tools.atan2(pt, Global.me)));
	}

	// true if BackAsFront, i.e. the argument to setAhead should be negated
	protected final boolean turnToward(final double absoluteAngle) {

		boolean res = false;
		double turn = absoluteAngle - Global.bot.getHeadingRadians();

		if (Math.cos(turn) < 0) {
			turn += Math.PI;
			res = true;
		}
		turn = Utils.normalRelativeAngle(turn);
		Global.bot.setTurnRightRadians(turn);

		return res;
	}
}
