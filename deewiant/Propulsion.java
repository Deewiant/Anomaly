// File created: prior to 2005-12-01

package deewiant;

import robocode.*;
import robocode.util.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Random;
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
		public RiskMinimiser() {
			super("Minimum Risk Movement");
		}
		private final Point2D[] prevPoints = new Point2D[1];

		private Point2D current, next;
		private double nextRisk;

		private double botNRG, mapWidth, mapHeight;

		private void updateBot() {
			current = new Point2D.Double(bot.getX(), bot.getY());
			botNRG  = bot.getEnergy();
			//////// UH OH
			mapWidth = bot.getBattleFieldWidth();
			mapHeight = bot.getBattleFieldHeight();
		}

		private static final double
			DIST_FACTOR   = Rules.RADAR_SCAN_RADIUS*Rules.RADAR_SCAN_RADIUS,
			TINY_DIST     =  25,
			VSHORT_DIST   =  75,
			SHORT_DIST    = 100,
			MIDDLE_DIST   = 150,
			LONG_DIST     = 250,
			TINY_DISTSQ   = TINY_DIST*TINY_DIST,
			VSHORT_DISTSQ = VSHORT_DIST*VSHORT_DIST,
			SHORT_DISTSQ  = SHORT_DIST*SHORT_DIST,
			MIDDLE_DISTSQ = MIDDLE_DIST*MIDDLE_DIST,
			LONG_DISTSQ   = LONG_DIST*LONG_DIST;

		Enemy target;

		public void move(final Enemy target) {

			this.target = target;

			updateBot();

			if (next != null && current.distanceSq(next) < TINY_DISTSQ && bot.getVelocity() > 0)
				return;

			for (int i = 0; i < prevPoints.length; ++i)
			if (prevPoints[i] != null)
				if (current.distanceSq(prevPoints[i]) > LONG_DISTSQ)
					prevPoints[i] = null;

			if (next == null) {
				next = current;
				nextRisk = Double.POSITIVE_INFINITY;
			// try to move around a lot, don't stay in one place for long
			} else if (next.distanceSq(current) < TINY_DISTSQ)
				nextRisk = Double.POSITIVE_INFINITY;
			else
				// recalculate, situation may have changed
				nextRisk = risk(next);

			out.println("STARTING AT " + nextRisk);

			double distance = MIDDLE_DIST;
			if (target != null)
				distance = Math.min(distance, 0.8 * current.distance(target));

			for (double angle = 0.0; angle < 2 * Math.PI; angle += 0.1) {
				final double dist = Math.max(VSHORT_DIST, random.nextDouble()*distance);
				tryPoint(Tools.projectVector(current, angle, dist), dist);
			}

			super.goTo(next);
		}

		Random random = new Random();

		private void tryPoint(final Point2D point, final double dist) {
			if (
				point.getX() < Tools.BOT_WIDTH  || point.getX() > mapWidth  - Tools.BOT_WIDTH ||
				point.getY() < Tools.BOT_HEIGHT || point.getY() > mapHeight - Tools.BOT_HEIGHT
			)
				return;

			final double risk = risk(point, dist);

			out.println("ATTEMPT " + risk);
			if (risk < nextRisk) {
				addPrev(current);
				next = point;
				nextRisk = risk;
			}
		}

		private void addPrev(final Point2D p) {
			if (prevPoints.length == 1) {
				prevPoints[0] = p;
				return;
			}

			double maxDist = Double.NEGATIVE_INFINITY;
			int m = prevPoints.length, n = m;

			for (int i = 0; i < prevPoints.length; ++i)
			if (prevPoints[i] == null)
				n = i;
			else {
				final double dist = prevPoints[i].distanceSq(current);
				if (dist < TINY_DISTSQ)
					return;
				else if (dist > maxDist) {
					maxDist = dist;
					m = i;
				}
			}

			if (n < prevPoints.length)
				prevPoints[n] = p;
			else
				prevPoints[m] = p;
		}

		private double risk(final Point2D point) {
			return risk(point, current.distanceSq(point));
		}
		private double risk(final Point2D point, final double distSq) {

			double fullRisk = 0;

			final long timeToPoint = (long)(distSq/Rules.MAX_VELOCITY);
			final Line2D lineToPoint = new Line2D.Double(current, point);

			for (final Enemy dude : dudes) {
	/*			double risk = 0;

	//			double damageQuotient;
	//			if (damageTaken > 0)
	//				damageQuotient = dude.hurtMe / damageTaken;
	//			else
	//				damageQuotient = 0;
	//
	//			double invAccuracy;
	//			if (dude.hitCount > 0)
	//				invAccuracy = (double)dude.shotAtCount / dude.hitCount;
	//			else
	//				invAccuracy = 0;

	//			risk += (1 + invAccuracy) * (1 + linearity(point, dude)) * (1 + damageQuotient) * dude.energy / botNRG;
	*/
				final double distanceSq = dude.distanceSq(point);

				// assume that the enemy targets you if you're closer than everyone else
				int targetedLikelihood = 0;
				for (final Enemy dude2 : dudes)
				if (
					dude2 != dude &&
					bot.getTime() - dude.lastShootTime <= 360.0/Rules.RADAR_TURN_RATE
					&& dude.distanceSq(dude2) >= distanceSq
				)
						++targetedLikelihood;

				double risk =
					Math.min(dude.energy / botNRG, 2) *
					(1 + linearity(point, dude) + targetedLikelihood) *
					(distanceSq < VSHORT_DISTSQ ? 5 : 1) / distanceSq;

				Rectangle2D VICINITY = new Rectangle2D.Double(
					dude.x - Tools.BOT_WIDTH *1.25,
					dude.y - Tools.BOT_HEIGHT*1.25,
					Tools.BOT_WIDTH  * 2.5,
					Tools.BOT_HEIGHT * 2.5);
				if (lineToPoint.intersects(VICINITY))
					risk *= 40;

				fullRisk += risk;
			}

   		if (!((dudes.size() & 1) == 0 && bot.getTime() - lastHit > 8))
   		for (final Point2D p : prevPoints)
   		if (p != null)
   			fullRisk += 5.0 / p.distanceSq(point);

			fullRisk += 100.0 / distSq;

			return fullRisk;
		}

		
		// David Alves's "perpendicularity":
		//    1 if, when going to a, we'd move directly towards b
		//    0 if we'd be moving perpendicular to b
		// I prefer "linearity", perpendicularity would be the other way around
		private double linearity(final Point2D a, final Point2D b) {
			return Math.abs(Math.cos(Tools.atan2(current, a) - Tools.atan2(current, b)));
		}
	}
 // end RiskMinimiser
		long lastHit = 0;
		void hitByBullet(final long time) {
			lastHit = time;
		}

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
