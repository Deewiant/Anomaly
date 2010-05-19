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

	// TODO?
	public void onScannedRobot(final Enemy dude) {}

	protected final void goTo(final Point2D destination) {
		final Point2D botPos = new Point2D.Double(Global.bot.getX(), Global.bot.getY());

		double distance = botPos.distance(destination);
		double turn = Tools.atan2(destination, botPos) - Global.bot.getHeadingRadians();

		// woo, BackAsFront
		if (Math.cos(turn) < 0) {
			turn += Math.PI;
			distance = -distance;
		}

		// don't hit the wall while turning

		final Point2D currentPath = Tools.projectVector(botPos, Global.bot.getHeadingRadians(), Tools.between(distance, -20, 20));

		if (
			(
				currentPath.getX() < Tools.BOT_WIDTH *1.5 || currentPath.getX() > Global.mapWidth  - Tools.BOT_WIDTH *1.5 ||
				currentPath.getY() < Tools.BOT_HEIGHT*1.5 || currentPath.getY() > Global.mapHeight - Tools.BOT_HEIGHT*1.5
			) &&
			Math.abs(Global.bot.getTurnRemainingRadians()) > 0
		)
			distance = 0;

		turn = Utils.normalRelativeAngle(turn);

		Global.bot.setTurnRightRadians(turn);
		Global.bot.setAhead(distance);
	}
}
