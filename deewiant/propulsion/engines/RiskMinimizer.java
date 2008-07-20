// File created: 2007-11-22 18:58:32

package deewiant.propulsion.engines;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import robocode.Rules;

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

	private final List<Point2D> drawPoints;
	private final List<Double>  drawRisks;

	private final Point2D[] prevPoints = new Point2D[1];

	private Point2D current, next;
	private double nextRisk;

	private double botNRG;

	private void updateBot() {
		current = new Point2D.Double(Global.bot.getX(), Global.bot.getY());
		botNRG  = Global.bot.getEnergy();
	}

	private static final double
		DIST_FACTOR   = Rules.RADAR_SCAN_RADIUS*Rules.RADAR_SCAN_RADIUS,
		TINY_DISTSQ   = Tools.BOT_WIDTH*Tools.BOT_HEIGHT,
		SHORT_DIST    = 100,
		MIDDLE_DIST   = 150,
		LONG_DIST     = 250,
		SHORT_DISTSQ  = SHORT_DIST*SHORT_DIST,
		MIDDLE_DISTSQ = MIDDLE_DIST*MIDDLE_DIST,
		LONG_DISTSQ   = LONG_DIST*LONG_DIST;

	public void move() {

		if (next != null && current.distanceSq(next) < TINY_DISTSQ)
			return;

		updateBot();

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

		if (DRAW_POINTS) {
			drawPoints.clear();
			drawRisks.clear();
			drawPoints.add(next);
			drawRisks.add(nextRisk);
		}

		double distance = MIDDLE_DIST;
		if (Global.target != null)
			distance = Tools.between(distance, SHORT_DIST, 0.8 * current.distance(Global.target));

		for (double angle = 0; angle < 2 * Math.PI; angle += 0.3)
			tryPoint(Tools.projectVector(current, angle, distance), distance);

		if (distance == SHORT_DIST)
		for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI/4)
			tryPoint(Tools.projectVector(current, angle, LONG_DIST), LONG_DIST);

		super.goTo(next);
	}

	private void tryPoint(final Point2D point, final double dist) {
		if (
			point.getX() < Tools.BOT_WIDTH  || point.getX() > Global.mapWidth  - Tools.BOT_WIDTH ||
			point.getY() < Tools.BOT_HEIGHT || point.getY() > Global.mapHeight - Tools.BOT_HEIGHT
		)
			return;

		final double risk = risk(point, dist);

		if (DRAW_POINTS) {
			drawPoints.add(point);
			drawRisks.add(risk);
		}

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

		final Line2D lineToPoint = new Line2D.Double(current, point);

		for (final Enemy dude : Global.dudes.values()) {
			if (dude.positionUnknown)
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
			final double distanceSq = dude.distanceSq(point);

			// we want to know how likely it is that we'll be targeted at point
			// assume that the enemy targets you if you're 90% closer than everyone else
			// so, check dude's distance to every other enemy and compare it to its distance to point
			// if point is closer, that's bad, so increase the risk
			int targetedLikelihood = 0, count = 0;
			for (final Enemy dude2 : Global.dudes.values())
			if (
				dude2 != dude &&
				!dude2.positionUnknown &&
				Global.bot.getTime() - dude.lastShootTime <= 360.0/Rules.RADAR_TURN_RATE
			) {
				++count;
				if (dude.distanceSq(dude2) * 0.81 > distanceSq)
					++targetedLikelihood;
			}

			double targetProb;
			if (count > 0)
				targetProb = (double)targetedLikelihood / count;
			else
				targetProb = 0;

			double risk =
				Math.min(dude.energy / botNRG, 1) *
				(1 + linearity(point, dude.guessPosition((long)(distSq/Rules.MAX_VELOCITY)))) *
				(1 + linearity(point, dude)) *
				(1 + targetProb) *
				DIST_FACTOR / distanceSq;

			if (lineToPoint.intersects(dude.vicinity))
				risk *= 20;

			fullRisk += risk;
		}

   	for (final Point2D p : prevPoints)
   	if (p != null)
   		fullRisk += DIST_FACTOR / (10 * p.distanceSq(point));

		fullRisk += DIST_FACTOR / distSq;

		return fullRisk;
	}
	
	// David Alves's "perpendicularity":
	//    1 if, when going to a, we'd move directly towards b
	//    0 if we'd be moving perpendicular to b
	// I prefer "linearity", perpendicularity would be the other way around
	private double linearity(final Point2D a, final Point2D b) {
		return Math.abs(Math.cos(Tools.atan2(current, a) - Tools.atan2(current, b)));
	}

	public void onPaint(final Graphics2D g) {
		if (!DRAW_POINTS)
			return;

		for (int i = 0; i < drawPoints.size(); ++i) {
			final Point2D p = drawPoints.get(i);
			final double  r = Math.min(drawRisks.get(i)/100, 30*Global.bot.getOthers());

			g.setColor(Tools.HSLtoRGB(
				120 - 4*(float)r / Global.bot.getOthers(),
				1f, 0.5f));

			if (p == next)
				g.draw(new Line2D.Double(current, p));

			final Ellipse2D e = new Ellipse2D.Double(
				p.getX() - 6, p.getY() - 6,
				12, 12);

			g.draw(e);
		}

		g.setColor(Color.GRAY);

		for (final Point2D p : prevPoints)
		if (p != null)
			g.draw(new Ellipse2D.Double(
				p.getX() - 6, p.getY() - 6,
				12, 12));
	}

	private static boolean DRAW_POINTS = true;
}
