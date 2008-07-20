// File created: prior to 2005-12-01

package deewiant;

import robocode.*;
import robocode.util.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

final public class Propulsion {
	private final AdvancedRobot     bot;
	private final PrintStream       out;
	private       Collection<Enemy> dudes;
	private       Engine            engine;

	public Propulsion(AdvancedRobot r) {
		bot = r;
		out = bot.out;

		dudes = new ArrayList<Enemy>(1);
	}

	public void propel(final Enemy e) {
		if (engine != null)
			engine.move(e);
	}

	public void victoryDance() {
		bot.setMaxVelocity(0);
		bot.setTurnLeftRadians(Double.POSITIVE_INFINITY);
	}

	public void updateDudes(final Collection<Enemy> dudes) {
		this.dudes = dudes;
	}
	public void onScannedRobot(final Enemy dude) {
		// there just _has_ to be a better way of doing this.
		if (bot.getOthers() == 1) {
			if (dudes.size() == 1 && rammerTime((dudes.toArray(new Enemy[1])[0])) && !(engine instanceof Rammer))
				engine = new Rammer();
//			else if (!(engine instanceof Rammer) && !(engine instanceof WaveSurfer))
//				engine = new WaveSurfer();
			else if (!(engine instanceof Rammer) && !(engine instanceof RiskMinimiser))
				engine = new RiskMinimiser();
		} else
			if (!(engine instanceof RiskMinimiser))
				engine = new RiskMinimiser();

		engine.onScannedRobot(dude);
	}

	abstract private class Engine {
		Engine(String s) { out.println("Engine set to: " + s); }

		public abstract void move(Enemy dude);

		public void onScannedRobot(final Enemy dude) { }

		final private void goTo(final Point2D destination) {
			final Point2D botPos = new Point2D.Double(bot.getX(), bot.getY());

			double distance = botPos.distance(destination);
			double turn = Tools.atan2(destination, botPos) - bot.getHeadingRadians();

			// woo, BackAsFront
			if (Math.cos(turn) < 0) {
				turn += Math.PI;
				distance = -distance;
			}

			// don't hit the wall while turning

			final Point2D currentPath = Tools.projectVector(botPos, bot.getHeadingRadians(), Tools.between(distance, -20, 20));

			if (
				(
					currentPath.getX() < Tools.BOT_WIDTH *1.5 || currentPath.getX() > bot.getBattleFieldWidth () - Tools.BOT_WIDTH *1.5 ||
					currentPath.getY() < Tools.BOT_HEIGHT*1.5 || currentPath.getY() > bot.getBattleFieldHeight() - Tools.BOT_HEIGHT*1.5
				) &&
				Math.abs(bot.getTurnRemainingRadians()) > 0
			)
				distance = 0;

			bot.setTurnRightRadians(Utils.normalRelativeAngle(turn));
			bot.setAhead(distance);
		}
	}

	// for melee
	final private class RiskMinimiser extends Engine {
		RiskMinimiser() { super("Minimum Risk Movement"); }

		private Point2D previous, current, next;

		private double
			mapWidth, mapHeight,
			botNRG;

		private void updateBot() {
			mapWidth  = bot.getBattleFieldWidth();
			mapHeight = bot.getBattleFieldHeight();
			current   = new Point2D.Double(bot.getX(), bot.getY());
			botNRG    = bot.getEnergy();
		}

		public void move(final Enemy target) {
			if (target == null)
				return;

			updateBot();

			if (previous == null)
				previous = current;
			if (next == null)
				next = previous;

			boolean newNext = false;
			final double distance = Math.min(300, current.distance(target) / 2);

			for (double angle = 0.0; angle < 2 * Math.PI; angle += 0.1) {
				final Point2D point = Tools.projectVector(current, angle, distance);

				if (
					point.getX() < Tools.BOT_WIDTH  || point.getX() > mapWidth  - Tools.BOT_WIDTH ||
					point.getY() < Tools.BOT_HEIGHT || point.getY() > mapHeight - Tools.BOT_HEIGHT
				)
					continue;

				if (risk(point) < risk(next)) {
					newNext = true;
					next = point;
				}
			}

			if (newNext)
				previous = current;

			super.goTo(next);
		}

		private double risk(final Point2D point) {
			double fullRisk = /*4.0 / previous.distanceSq(point) + */1.0 / (10 * current.distanceSq(point));

			Line2D lineToPoint = new Line2D.Double(current, point);

			Rectangle2D enemyArea = new Rectangle2D.Double(
				0, 0,
				Tools.BOT_WIDTH * 2,
				Tools.BOT_HEIGHT * 2
			);

			for (Enemy dude: dudes) {
				double risk = Math.max(botNRG, dude.energy) / point.distanceSq(dude);

				// from HawkOnFire/Kawigi
				// David Alves's "perpendicularity": 1 if moving right at enemy, 0 if moving perpendicular
				final double perpendicularity = Math.abs(Math.cos(Tools.atan2(current, point)) - Tools.atan2(dude, point));

				// we want to know how likely it is that we'll be targeted at point
				// assume that the enemy targets you if you're 90% closer than everyone else
				// so, check dude's distance to every other enemy and compare it to its distance to point
				// if point is closer, that's bad, so increase the risk
				double targetedLikelihood = 0.0;
				for (Enemy dude2: dudes)
					if (dude2 != dude && dude.distance(dude2) * 0.9 > dude.distance(point))
						++targetedLikelihood;

				risk *= 1 + targetedLikelihood * perpendicularity;

				// if path to point collides with dude, very bad
				// from Kawigi:
				// "I'm figuring that if to [sic] 36x36 squares will hit each other moving along a certain vector
				// (my vector, and I assume they'll be in roughly the same place), then a line representing
				// the vector will intersect a 72x72 square centered in the same place."
				// so instead of checking whether a 36x36x(distance-to-bot) rectangle intersects with a 36x36 bot,
				// check whether a vector intersects with a 72x72 bot
				enemyArea.setRect(dude.getX() - Tools.BOT_WIDTH, dude.getY() - Tools.BOT_HEIGHT, enemyArea.getWidth(), enemyArea.getHeight());
				if (lineToPoint.intersects(enemyArea))
					risk *= 8;

				fullRisk += risk;
			}

			return fullRisk;
		}
	} // end RiskMinimiser

	// for a disabled or 0.0 energy dude in one-on-one
	final private class Rammer extends Engine {
		Rammer() { super("Ramming"); }
		public void move(final Enemy s) { super.goTo(s); }
	}
	// is it Rammer time?
	// assume that the given Enemy is the only dude left
	private boolean rammerTime(final Enemy e) {
		return (
			e.energy < 0.2 ||
			(bot.getEnergy() / e.energy >= 10 && bot.getTime() - e.lastShootTime > 20)
		);
	}

	// for normal one-on-one
/*

as I currently see it:
	fire off a wave when the enemy shoots
	when hit, ++danger of the guess factor the enemy shot from
	when missed (i.e. wave front passes your position without getting hit), danger of the guess factor you chose -= 0.1 (he might shoot it next time, so not -= 1)
	when moving, pick the guess factor with the least danger

*/
/*	final private class WaveSurfer extends Engine {
		WaveSurfer() { super("Wave Surfing"); }

		// the WaveSurfing-specific stuff which needs to be remembered between rounds is updated here
		// the WaveSurfing-specific stuff which does not need to be remembered is updated along with everything else, in Anomaly.onScannedRobot()
		// i.e. dude.memory is updated here, dude is updated elsewhere
		public void onScannedRobot(Enemy dude) {
			dude.statify();
			++dude.memory.scans;
		}


		public void move(Enemy target) {
			return;
		}

		private class MovementWave extends Wave {
		}

		private class Move {
		}

		private class Hit {
		}
	}
*/
} // end Propulsion
