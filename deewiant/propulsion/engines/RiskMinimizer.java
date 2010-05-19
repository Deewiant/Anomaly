// File created: 2007-11-22 18:58:32

package deewiant.propulsion.engines;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;
import deewiant.propulsion.engines.model.Engine;

public final class RiskMinimizer extends Engine {
	public RiskMinimizer() {
		super("Minimum Risk Movement");

		if (DRAW_POINTS) {
			drawPoints = new ArrayList<Point2D>();
			drawRisks  = new ArrayList<Double>();
		} else {
			drawPoints = null;
			drawRisks  = null;
		}
	}

	private static boolean DRAW_POINTS = true;
	private final List<Point2D> drawPoints;
	private final List<Double>  drawRisks;

	private final Random random = new Random();

	private final Point2D[] prevPoints = new Point2D[2];

	private Point2D
		prev = new Point2D.Double(),
		next = new Point2D.Double();
	private long choseAt = 0;
	private boolean justStarted = true;
	private double nextRisk = Double.POSITIVE_INFINITY;

	private static final double
		TINY_DISTSQ   = 50*50,
		MINDIST       = 100,
		MAXDIST       = 200;

	public void move() {

		final long now = Global.bot.getTime();

		// If we haven't moved some distance yet and are moving, don't pick a new
		// point.
		// Move for at least two ticks between decisions.
		if (!justStarted && (
			(now > choseAt && now - choseAt <= 2) ||
			(
				Global.bot.getDistanceRemaining() > 0 &&
				Global.me.distanceSq(prev) < TINY_DISTSQ
			)
		)) {
			if (DRAW_POINTS && next != null) {
				final Graphics2D g = Global.bot.getGraphics();
				g.setColor(Color.GREEN);
				drawPoint(g, next);
			}

			// XXX: we sometimes keep hitting this return statement whilst
			// stopped, this is a workaround
			super.goTo(next);

			return;
		}
		justStarted = false;

		for (int i = 0; i < prevPoints.length; ++i)
		if (prevPoints[i] != null)
			if (Global.me.distanceSq(prevPoints[i]) > MAXDIST*MAXDIST)
				prevPoints[i] = null;

		if (Global.me.distanceSq(next) < TINY_DISTSQ)
			// Don't ever stop moving...
			nextRisk = Double.POSITIVE_INFINITY;
		else
			// recalculate, situation may have changed
			nextRisk = risk(next);

		if (DRAW_POINTS) {
			drawPoints.add(next);
			drawRisks.add(nextRisk);
		}

		for (double angle = 0; angle < 2 * Math.PI; angle += 0.3) {
			final double distance =
				MINDIST + random.nextDouble()*(MAXDIST-MINDIST);
			tryPoint(Tools.projectVector(Global.me, angle, distance), distance);
		}

		if (DRAW_POINTS) {
			// {{{ draw 'em
			double
				minRisk = Double.POSITIVE_INFINITY,
				maxRisk = Double.NEGATIVE_INFINITY;

			for (final double r : drawRisks)
			if (Math.abs(r) != Double.POSITIVE_INFINITY) {
					if (r < minRisk) minRisk = r;
					if (r > maxRisk) maxRisk = r;
			}

			final Graphics2D g = Global.bot.getGraphics();

			for (int i = 0; i < drawPoints.size(); ++i)
				drawPoint(g,
					drawPoints.get(i),
					drawRisks.get(i), minRisk, maxRisk);

			g.setColor(Color.GRAY);

			for (final Point2D p : prevPoints)
			if (p != null)
				drawPoint(g, p);

			drawPoints.clear();
			drawRisks .clear();
			// }}}
		}

		prev.setLocation(Global.me.getX(), Global.me.getY());
		choseAt = now;
		super.goTo(next);
	}

	private double risk(final Point2D point) {
		return risk(point, Global.me.distanceSq(point));
	}
	private double risk(final Point2D point, final double distSq) {

		double fullRisk = 1;

		final Line2D lineToPoint = new Line2D.Double(Global.me, point);

		final long expectedTime = (long)(Math.sqrt(distSq)/Rules.MAX_VELOCITY);

		for (final Enemy dude : Global.dudes) {
			if (dude.dead || dude.positionUnknown)
				continue;

/*			double risk = 0;

//			double damageQuotient;
//			if (Global.damageTaken > 0)
//				damageQuotient = dude.hurtMe / Global.damageTaken;
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
			final double dudeDistSq = dude.distanceSq(point);

			// we want to know how likely it is that we'll be targeted at point
			// assume that the enemy targets you if you're 90% closer than
			// everyone else so, check dude's distance to every other enemy and
			// compare it to its distance to point if point is closer, that's bad,
			// so increase the risk
			int targetedLikelihood = 0, count = 0;
			for (final Enemy dude2 : Global.dudes)
			if (
				dude2 != dude &&
				!dude2.positionUnknown &&
				dude2.energy > 0.1
			) {
				++count;
				if (dude.distanceSq(dude2) * 0.81 > dudeDistSq)
					++targetedLikelihood;
			}

			final double targetProb;
			if (count > 0)
				targetProb = (double)targetedLikelihood / count;
			else
				targetProb = 0;

			double risk =
				Math.max(dude.energy / Global.bot.getEnergy(), 1) *
				(1 + 0.5*linearity(point, dude.guessPosition(expectedTime))) *
				(1 +     linearity(point, dude)) *
				(1 + targetProb) *
				distanceSqRisk(dudeDistSq);

			if (lineToPoint.intersects(dude.vicinity)) {
				if (lineToPoint.intersects(dude.boundingBox))
					return Double.POSITIVE_INFINITY;
				risk *= 100;
			}

			fullRisk += risk;
		}

   	for (final Point2D p : prevPoints)
   		if (p != null)
   			fullRisk += distanceSqRisk(p.distanceSq(point));

   	fullRisk += distanceSqRisk(distSq);

//		double turn =
//			Utils.normalRelativeAngle(
//				Tools.atan2(point, Global.me) - Global.bot.getHeadingRadians());
//		if (Math.abs(turn) > Math.PI/2) {
//			if (turn > 0)
//				turn -= Math.PI;
//			else
//				turn += Math.PI;
//		}
//		turn = Math.abs(turn);
//
//   	fullRisk *= 1 - turn/Math.PI;

		return fullRisk;
	}
	
	// David Alves's "perpendicularity":
	//    1 if, when going to a, we'd move directly towards b
	//    0 if we'd be moving perpendicular to b
	// I prefer "linearity", perpendicularity would be the other way around
	private double linearity(final Point2D a, final Point2D b) {
		return Math.abs(
			Math.cos(Tools.atan2(Global.me, a) - Tools.atan2(Global.me, b)));
	}

	// Risk 10 at distance <= 50, 1 at >= 400
	private double distanceSqRisk(final double distSq) {
		return Tools.fromToRange(
			50*50, 400*400,
			10, 1,
			Tools.between(50*50, 400*400, distSq));
	}

	private void tryPoint(final Point2D point, final double dist) {
		if (
			point.getX() < Tools.BOT_WIDTH  ||
			point.getX() > Global.mapWidth  - Tools.BOT_WIDTH ||
			point.getY() < Tools.BOT_HEIGHT ||
			point.getY() > Global.mapHeight - Tools.BOT_HEIGHT
		)
			return;

		final double risk = risk(point, dist);

		if (DRAW_POINTS) {
			drawPoints.add(point);
			drawRisks.add(risk);
		}

		if (risk < nextRisk) {
			addPrev(Global.me);
			next = point;
			nextRisk = risk;
		}
	}

	// Replace the prevPoint that is furthest away
	// If any prevPoint is our current position, don't place this one at all
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
		else if (prevPoints[i] == Global.me)
			return;
		else {
			final double dist = prevPoints[i].distanceSq(Global.me);
			if (dist > maxDist) {
				maxDist = dist;
				m = i;
			}
		}

		if (n < prevPoints.length)
			prevPoints[n] = p;
		else
			prevPoints[m] = p;
	}

	private void drawPoint(
		final Graphics2D g, final Point2D p,
		final double r, final double minR, final double maxR
	) {
		// green is at around 96, 1, 0.5
		// with red at         0, 1, 0.5
		g.setColor(Tools.HSLtoRGB(
			(float)(96 - Tools.fromToRange(minR, maxR, 0, 96, r)),
			1f, 0.5f));
		drawPoint(g, p);
	}

	private void drawPoint(final Graphics2D g, final Point2D p) {
		if (p == next)
			g.draw(new Line2D.Double(Global.me, p));

		g.draw(new Ellipse2D.Double(p.getX() - 6, p.getY() - 6, 12, 12));
	}
}
