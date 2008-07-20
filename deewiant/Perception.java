// File created: prior to 2005-12-01

package deewiant;

import robocode.*;
import robocode.util.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.*;
import java.io.PrintStream;

final public class Perception {
	private final        AdvancedRobot bot;
	private final        PrintStream   out;
	public  final static double        SCAN_RANGE = 1200;
	public               boolean       clockwise = false; // whether the next sweep goes clockwise or not, i.e. the current direction is !clockwise
	private              boolean       wasOutsideArc = true;
	private final        Rectangle2D[] quadrants;
	private final        double        gunCoolingRate;

	public Perception(final AdvancedRobot r) {
		bot = r;
		out = bot.out;
		gunCoolingRate = bot.getGunCoolingRate();

		final double
			cornerSize = Math.hypot(5 * Tools.BOT_WIDTH, 5 * Tools.BOT_HEIGHT),
			mapWidth   = bot.getBattleFieldWidth(),
			mapHeight  = bot.getBattleFieldHeight();

		/* split battlefield into 9 quadrants:
		 *   one for each corner (5-8, clockwise from top)
		 *   one for each edge   (1-4, clockwise from top)
		 *   one for the middle  (0)
		 * (in the array in order of size)
		 * so (note origin O at *lower* left corner):
		 *
		 * +---+---------+---+
		 * |   |         |   |
		 * | 5 |    1    | 6 |
		 * |   |         |   |
		 * +---+---------+---+
		 * |   |         |   |
		 * |   |         |   |
		 * |   |         |   |
		 * |   |         |   |
		 * | 4 |    0    | 2 |
		 * |   |         |   |
		 * |   |         |   |
		 * |   |         |   |
		 * |   |         |   |
		 * +---+---------+---+
		 * |   |         |   |
		 * | 8 |    3    | 7 |
		 * |   |         |   |
		 * O---+---------+---+
		 *
		 * it also seems that though the Java Platform doc says it's the upper left
		 * corner, it's actually the lower left we're passing to the constructor (and it works)
		 */

		quadrants    = new Rectangle2D.Double[9];
		quadrants[0] = new Rectangle2D.Double(           cornerSize,             cornerSize, mapWidth - 2*cornerSize, mapHeight - 2*cornerSize);
		quadrants[1] = new Rectangle2D.Double(           cornerSize, mapHeight - cornerSize,              mapWidth - 2*cornerSize,               cornerSize);
		quadrants[2] = new Rectangle2D.Double(mapWidth - cornerSize,             cornerSize,              cornerSize, mapHeight - 2*cornerSize);
		quadrants[3] = new Rectangle2D.Double(           cornerSize,                      0, mapWidth - 2*cornerSize,               cornerSize);
		quadrants[4] = new Rectangle2D.Double(                    0,             cornerSize,              cornerSize, mapHeight - 2*cornerSize);
		quadrants[5] = new Rectangle2D.Double(                    0, mapHeight - cornerSize,              cornerSize,               cornerSize);
		quadrants[6] = new Rectangle2D.Double(mapWidth - cornerSize, mapHeight - cornerSize,              cornerSize,               cornerSize);
		quadrants[7] = new Rectangle2D.Double(mapWidth - cornerSize,                      0,              cornerSize,               cornerSize);
		quadrants[8] = new Rectangle2D.Double(                    0,                      0,              cornerSize,               cornerSize);
	}

	public void perceive(final Enemy target) {
		final boolean melee = bot.getOthers() > 1;

		// lock if we have a target whom we've seen recently
		// in melee, lock only if we're about to shoot
		if (target != null                       &&
		    bot.getTime() - target.scanTime < 3  &&
		    (!melee || (melee && readyToLock() && bot.getEnergy() > 0.1))
		) {
			lock(target.bearing);
			return;
		}
		/*
		 * if in a corner, scan only the  90 degree arc away from the corner
		 * if in an edge,  scan only the 180 degree arc away from the edge
		 * if in the middle, 360 degrees
		 */

		final double
			x = bot.getX(),
			y = bot.getY();
		int q = 0;
		for (Rectangle2D quad: quadrants) {
			if (quad.contains(x, y))
				break;
			++q;
		}

		switch (q) {
			// in the middle
			case 0: bot.setTurnRadarRightRadians(clockwise ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY); break;

			// edges
			case 1: scanArc(    Math.PI,     Math.PI); break;
			case 2: scanArc(3 * Math.PI / 2, Math.PI); break;
			case 3: scanArc(              0, Math.PI); break;
			case 4: scanArc(    Math.PI / 2, Math.PI); break;

			// corners
			case 5: scanArc(3 * Math.PI / 4, Math.PI / 2); break;
			case 6: scanArc(5 * Math.PI / 4, Math.PI / 2); break;
			case 7: scanArc(7 * Math.PI / 4, Math.PI / 2); break;
			case 8: scanArc(    Math.PI / 4, Math.PI / 2); break;

			default: out.println("Not in any quadrant"); assert false;
		}
	}

	public void victoryDance() {
		bot.setTurnRadarLeftRadians(clockwise ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
	}

	public boolean readyToLock() {
		return bot.getGunHeat() / gunCoolingRate < 6;
	}

	private void lock(final double targetBearing) {
		final double radarTurn = Utils.normalRelativeAngle(bot.getRadarHeadingRadians() - (bot.getHeadingRadians() + targetBearing));
		clockwise = radarTurn < 0;
		// Math.PI / 4 happens to be the radar turn speed, so turn half of that in whichever direction
		bot.setTurnRadarLeftRadians(radarTurn + (clockwise ? -(Math.PI / 8) : (Math.PI / 8)));
	}

	private void scanArc(final double arcCentre, final double arc) {
		final double edge1 = Utils.normalAbsoluteAngle(arcCentre - arc / 2);
		final double edge2 = Utils.normalAbsoluteAngle(arcCentre + arc / 2);
		final double radarPos = bot.getRadarHeadingRadians();

// todo: inline the following so we don't need to create the Arc2D object

		final Arc2D a = new Arc2D.Double();
		a.setAngleStart (edge1 * 180.0 / Math.PI);
		a.setAngleExtent(  arc * 180.0 / Math.PI);
		final boolean outsideArc = !a.containsAngle(radarPos * 180.0 / Math.PI);

		double radarTurn;

		if (outsideArc) {
			if (wasOutsideArc) {
				// get the edge which is in the opposite of the radar's current direction from arcCentre
				// move the radar through the quickest path to that edge

				final double targetEdge = this.nextEdge(arcCentre, edge1, edge2);
				radarTurn = Utils.normalRelativeAngle(targetEdge - radarPos);
			} else {
				// we are now outside the arc, but weren't just now
				// so we reached the edge: turn back and go to the other one

				clockwise ^= true;
				radarTurn = (clockwise ? arc : -arc);
			}
		} else {
			// we are currently inside the arc
			// go to the next edge

			final double nextEdge = this.nextEdge(radarPos, edge1, edge2);
			radarTurn = Utils.normalRelativeAngle(nextEdge - radarPos);
		}

		// add a bit of extra to the turn so we scan the whole width of the quadrant,
		// not just to the middle
		bot.setTurnRadarRightRadians(radarTurn + Math.signum(radarTurn) * Math.PI / 16);

		wasOutsideArc = outsideArc;
	}

	private double nextEdge(final double whence, final double edge1, final double edge2) {
		return (clockwise
			? (
				(Utils.normalRelativeAngle(edge1 - whence) >= Utils.normalRelativeAngle(edge2 - whence))
					? edge1 : edge2
			) : (
				(Utils.normalRelativeAngle(edge1 - whence) <= Utils.normalRelativeAngle(edge2 - whence))
					? edge1 : edge2
			)
		);
	}

	// Only painting stuff henceforth

	public void onPaint(final Graphics2D g) {
		if (!PAINT_QUADRANTS && !PAINT_ARCS)
			return;

		int q = 0, tq = 0;
		g.setColor(Color.WHITE);
		for (Rectangle2D quad: quadrants) {
			if (PAINT_QUADRANTS)
				g.draw(quad);
			if (quad.contains(bot.getX(), bot.getY())) {
				tq = q;
				if (!PAINT_QUADRANTS)
					break;
			}
			if (++q == 1)
				g.setColor(Color.BLUE);
			else if (q == 5)
				g.setColor(Color.RED);
		}

		if (PAINT_ARCS) switch (tq) {
			case 1: paintArc(g,     Math.PI,      Math.PI); break;
			case 2: paintArc(g, 3 * Math.PI / 2,  Math.PI); break;
			case 3: paintArc(g,               0, -Math.PI); break;
			case 4: paintArc(g,     Math.PI / 2, -Math.PI); break;

			case 5: paintArc(g, 3 * Math.PI / 4,  Math.PI / 2); break;
			case 6: paintArc(g, 5 * Math.PI / 4,  Math.PI / 2); break;
			case 7: paintArc(g, 7 * Math.PI / 4,  Math.PI / 2); break;
			case 8: paintArc(g,     Math.PI / 4,  Math.PI / 2); break;

			default: break;
		}
	}

	private void paintArc(final Graphics2D g, final double arcCentre, double arc) {
	/*	final double edge1 = Utils.normalAbsoluteAngle(arcCentre - arc / 2);
		final double edge2 = Utils.normalAbsoluteAngle(arcCentre + arc / 2);

		final Point2D pos = new Point2D.Double(bot.getX(), bot.getY());

		final Line2D centreLine = new Line2D.Double(p, Tools.projectVector(p, arcCentre, SCAN_RANGE)),
		              edge1Line = new Line2D.Double(p, Tools.projectVector(p, edge1, SCAN_RANGE)),
		              edge2Line = new Line2D.Double(p, Tools.projectVector(p, edge2, SCAN_RANGE));

		boolean outsideArc;
		if (arcCentre > edge1 && arcCentre < edge2)
			outsideArc = (radarPos < edge1 || radarPos > edge2);
		else
			outsideArc = (radarPos > edge1 || radarPos < edge2);

		if (wasOutsideArc && outsideArc) {
			final double targetEdge = this.nextEdge(arcCentre, edge1, edge2);

			final Line2D line = new Line2D.Double(p, Tools.projectVector(p, targetEdge, SCAN_RANGE));

			g.setColor(Color.RED);
			g.draw(line);

		} else if (!outsideArc) {
			final double nextEdge = this.nextEdge(radarPos, edge1, edge2);

			final Line2D line = new Line2D.Double(p, Tools.projectVector(p, nextEdge, SCAN_RANGE));

			g.setColor(Color.WHITE);
			g.draw(line);
		} else {
			final double radarTurn = (clockwise ? arc : -arc);

			final Line2D line = new Line2D.Double(p, Tools.projectVector(p, radarPos - radarTurn, SCAN_RANGE));
			final Line2D shortLine = new Line2D.Double(p, Tools.projectVector(p, radarPos - radarTurn, 100));
			final Ellipse2D targetEllipse = new Ellipse2D.Double(shortLine.getX2(), shortLine.getY2() - 8, 16, 16);

			g.setColor(Color.BLUE);
			g.draw(line);
			g.draw(targetEllipse);
		}

		g.setColor(Color.YELLOW);
		final Line2D e1l = new Line2D.Double(p, Tools.projectVector(p, edge1, SCAN_RANGE));
		final Line2D e2l = new Line2D.Double(p, Tools.projectVector(p, edge1, SCAN_RANGE));
		g.draw(e1l);
		g.draw(e2l);

		g.setColor(Color.GREEN);
		g.draw(acl);*/
	}

	static final boolean
		PAINT_QUADRANTS = false,
		PAINT_ARCS      = false;
}
