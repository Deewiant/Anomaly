// File created: prior to 2005-12-01

package deewiant.perception;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public final class Perception {
	private static final Rectangle2D[] quadrants = new Rectangle2D.Double[9];
	private        final double        gunCoolingRate;

	private double
		radarHeading,
		prevRadarHeading,
		priorToCircle = -1;

	private boolean
		// whether the next sweep is to go clockwise
		// the current direction is !clockwise
		clockwise     = true,
		correcting    = false, // for melee corners
		wasOutsideArc = true,
		// leavingArc: avoid spinning back and forth between two opposites
		// (outside arc, radar is NW, opposite is E, turns clockwise.
		//  outside arc, radar is NE, opposite is W, turns anticlockwise.
		//  OOPS.)
		leavingArc = false,
		// somebody nearby that we haven't seen, woops!
		pleaseFullCircle = false;

	// temp arc used in a couple of places
	private final Arc2D arc = new Arc2D.Double();

	private static final double[]
		arcCentres = {
			// edges
			1 * Math.PI,
			3 * Math.PI / 2,
			0,
			1 * Math.PI / 2,

			// corners
			3 * Math.PI / 4,
			5 * Math.PI / 4,
			7 * Math.PI / 4,
			1 * Math.PI / 4,
		},
		arcExtents = {
			Math.PI,   Math.PI,   Math.PI,   Math.PI,
			Math.PI/2, Math.PI/2, Math.PI/2, Math.PI/2,
		};

	public Perception() {
		gunCoolingRate = Global.bot.getGunCoolingRate();

		final double
			cornerSize = Math.hypot(4 * Tools.BOT_WIDTH, 4 * Tools.BOT_HEIGHT),
			w = Global.mapWidth,
			h = Global.mapHeight;

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

		if (quadrants[0] != null)
			return;

		quadrants[0] = new Rectangle2D.Double(    cornerSize,     cornerSize, w - 2*cornerSize, h - 2*cornerSize);
		quadrants[1] = new Rectangle2D.Double(    cornerSize, h - cornerSize, w - 2*cornerSize,       cornerSize);
		quadrants[2] = new Rectangle2D.Double(w - cornerSize,     cornerSize,       cornerSize, h - 2*cornerSize);
		quadrants[3] = new Rectangle2D.Double(    cornerSize,              0, w - 2*cornerSize,       cornerSize);
		quadrants[4] = new Rectangle2D.Double(             0,     cornerSize,       cornerSize, h - 2*cornerSize);
		quadrants[5] = new Rectangle2D.Double(             0, h - cornerSize,       cornerSize,       cornerSize);
		quadrants[6] = new Rectangle2D.Double(w - cornerSize, h - cornerSize,       cornerSize,       cornerSize);
		quadrants[7] = new Rectangle2D.Double(w - cornerSize,              0,       cornerSize,       cornerSize);
		quadrants[8] = new Rectangle2D.Double(             0,              0,       cornerSize,       cornerSize);
	}

	public void hiddenDudeAt(final double heading) {
		if (getQuadrant() != 0) {
			pleaseFullCircle = true;
			// spin toward heading as fast as possible
			clockwise =
				Utils.normalRelativeAngle(
					Global.bot.getRadarHeadingRadians() - heading)
				< 0;
		}
	}

	public void perceive() {
		final boolean melee = Global.bot.getOthers() > 1;

		// if just started, spin 360 degrees once
		if (Global.bot.getTime() < 8) {
			Global.bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
			return;
		}

		radarHeading = Global.bot.getRadarHeadingRadians();

		// check for long-lost dudes
		// no need if the map is so small that we can see from corner to corner
		if (
			Global.mapWidth*Global.mapWidth + Global.mapHeight*Global.mapHeight
			> Rules.RADAR_SCAN_RADIUS*Rules.RADAR_SCAN_RADIUS
		) {
			final double diff = Utils.normalRelativeAngle(radarHeading - prevRadarHeading);

			// copied from RobotPeer
			arc.setArc(
				Global.bot.getX() - Rules.RADAR_SCAN_RADIUS,
				Global.bot.getY() - Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				prevRadarHeading * (180.0 / Math.PI),
				diff             * (180.0 / Math.PI),
				Arc2D.PIE);

			for (final Enemy dude : Global.dudes.values())
				if (dude.old && !dude.positionUnknown && arc.intersects(dude.boundingBox))
					dude.positionUnknown = true;
		}

		prevRadarHeading = radarHeading;

		// lock if we have a target
		if (Global.target != null) {
			if (melee) {
				// in melee, lock only if we're about to shoot
				if (readyToLock() && Global.bot.getEnergy() >= 0.2) {

					final long seenDelta = Global.bot.getTime() - Global.target.scanTime;

					if (seenDelta >= 2*Tools.LOCK_ADVANCE) {
						if (Global.target.distance < Rules.RADAR_SCAN_RADIUS/2) {
							// target slipped in close without being seen
							// happens VERY rarely, a pain to test
							// sample.Walls is good at this, slipping into a
							// corner without being spotted

							if (correcting && seenDelta >= 3*Tools.LOCK_ADVANCE)
								// been correcting for too long
								// (this has never happened in testing
								//  and is only a safety measure)
								Global.bot.setTurnRadarRightRadians(clockwise ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);

							else {
								// lock to last known pos, turn a bit extra
								lock(Global.target.absBearing, Math.PI/2);
								correcting = true;
							}
							return;
						}
					} else {
						lock(Global.target.absBearing);
						correcting = false;
						return;
					}
				}
			// one-on-one, lock if recently seen
			} else if (Global.bot.getTime() - Global.target.scanTime < 5*Tools.LOCK_ADVANCE) {
				lock(Global.target.absBearing);
				return;
			}
		}
		correcting = false;

		if (pleaseFullCircle) {
			if (Tools.near(priorToCircle, radarHeading)) {
				pleaseFullCircle = false;
				priorToCircle = -1;
			} else {
				if (priorToCircle < 0)
					priorToCircle = radarHeading;
				fullCircle();
				return;
			}
		}

		/*
		 * if in a corner, scan only the  90 degree arc away from the corner
		 * if in an edge,  scan only the 180 degree arc away from the edge
		 * if in the middle, 360 degrees
		 */

		final double
			x = Global.bot.getX(),
			y = Global.bot.getY();
		final Point2D me = new Point2D.Double(x, y);

		for (final Enemy dude : Global.dudes.values())
		if (!dude.old && dude.distanceSq(me) < 150*150) {
			fullCircle();
			return;
		}

		final int q = getQuadrant(x, y);
		if (q == 0)
			fullCircle();
		else
			scanArc(arcCentres[q-1], arcExtents[q-1]);
	}

	public void victoryDance() {
		Global.bot.setTurnRadarRightRadians(clockwise ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
	}

	public boolean readyToLock() {
		return Global.bot.getGunHeat() / gunCoolingRate < Tools.LOCK_ADVANCE;
	}

	private void fullCircle() {
		Global.bot.setTurnRadarRightRadians(clockwise ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
		nextEdge = Double.NaN;
		wasOutsideArc = false;
	}

	private void lock(final double th) {
		lock(th, Rules.RADAR_TURN_RATE_RADIANS / 2);
	}
	private void lock(final double targetHeading, final double offset) {
		final double toEnemyCentre = Utils.normalRelativeAngle(targetHeading - radarHeading);

		clockwise = toEnemyCentre > 0;

		Global.bot.setTurnRadarRightRadians(toEnemyCentre + (clockwise ? offset : -offset));
		wasOutsideArc = false;
	}

	private int getQuadrant() {
		return getQuadrant(Global.bot.getX(), Global.bot.getY());
	}
	private int getQuadrant(final double x, final double y) {
		int q = 0;
		for (Rectangle2D quad : quadrants) {
			if (quad.contains(x, y))
				break;
			++q;
		}
		return q;
	}

	private double nextEdge;

	private void scanArc(final double arcCentre, final double arc) {
		final double edge1 = Utils.normalAbsoluteAngle(arcCentre - arc / 2);
		final double edge2 = Utils.normalAbsoluteAngle(arcCentre + arc / 2);

		this.arc.setAngleStart (edge1 * 180.0 / Math.PI);
		this.arc.setAngleExtent(  arc * 180.0 / Math.PI);
		final boolean outsideArc = !this.arc.containsAngle(radarHeading * 180.0 / Math.PI);

		if (outsideArc) {
			if (wasOutsideArc) {
				// get the opposite edge
				// move the radar through the quickest path to that edge

				if (leavingArc)
					return;

				nextEdge = getNextEdge(arcCentre, edge1, edge2);
				doScan(nextEdge);

				leavingArc = true;

			} else {
				// we are now outside the arc, but weren't just now
				// so we reached the edge: turn back and go to the other one

				if (clockwise) {
					clockwise = false;
					doExactScan(-arc);
				} else {
					clockwise = true;
					doExactScan(arc);
				}

				leavingArc = false;
			}
		} else {
			// we are currently inside the arc
			// go to the next edge

			nextEdge = getNextEdge(radarHeading, edge1, edge2);
			doScan(nextEdge);

			leavingArc = false;
		}

		wasOutsideArc = outsideArc;
	}

	private void doScan(final double edge) {
		final double radarTurn = Utils.normalRelativeAngle(edge - radarHeading);
		clockwise  = radarTurn > 0;

		doExactScan(radarTurn);
	}

	private void doExactScan(final double radarTurn) {
		// add a bit of extra to the turn so we scan the whole width of the
		// quadrant, not just to the middle
		Global.bot.setTurnRadarRightRadians(radarTurn + Math.signum(radarTurn) * (Math.PI / 16));
	}

	private double getNextEdge(final double whence, final double edge1, final double edge2) {
		return (clockwise ? (
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
		for (final Rectangle2D quad: quadrants) {
			if (PAINT_QUADRANTS)
				g.draw(quad);
			if (quad.contains(Global.bot.getX(), Global.bot.getY())) {
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
		final double radarPos = Global.bot.getRadarHeadingRadians();

		final double edge1 = Utils.normalAbsoluteAngle(arcCentre - arc / 2);
		final double edge2 = Utils.normalAbsoluteAngle(arcCentre + arc / 2);

		final Point2D p = new Point2D.Double(Global.bot.getX(), Global.bot.getY());

		final Line2D
			centreLine = new Line2D.Double(p, Tools.projectVector(p, arcCentre, Rules.RADAR_SCAN_RADIUS)),
		   edge1Line = new Line2D.Double(p, Tools.projectVector(p, edge1, Rules.RADAR_SCAN_RADIUS)),
		   edge2Line = new Line2D.Double(p, Tools.projectVector(p, edge2, Rules.RADAR_SCAN_RADIUS));

		g.setColor(Color.YELLOW);
		g.draw(edge1Line);
		g.draw(edge2Line);

		g.setColor(Color.GREEN);
		g.draw(centreLine);
	}

	private static final boolean
		PAINT_QUADRANTS = false,
		PAINT_ARCS      = false;
}
