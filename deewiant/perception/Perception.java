// File created: prior to 2005-12-01

package deewiant.perception;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;

public final class Perception {
	private static final Rectangle2D[] quadrants = new Rectangle2D.Double[9];
	private        final double        gunCoolingRate;

	private static final double        EXTRATURN = Math.PI / 16;

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
			cornerSize = Math.hypot(5 * Tools.BOT_WIDTH, 5 * Tools.BOT_HEIGHT),
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
		 * it also seems that though the Java Platform doc says it's the upper
		 * left corner, it's actually the lower left we're passing to the
		 * constructor (and it works)
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

	// for when we've been shot at from a
	public void hiddenDudeAt(final double bearing) {
		if (getQuadrant() != 0) {
			pleaseFullCircle = true;
			// spin toward bearing as fast as possible
			clockwise = bearing >= 0;
		}
	}

	public void perceive() {
		final boolean melee = Global.bot.getOthers() > 1;

		// if just started, spin 360 degrees once
		final long now = Global.bot.getTime();
		if (now < 8) {
			Global.bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
			return;
		}

		radarHeading = Global.bot.getRadarHeadingRadians();

		// check for long-lost dudes
		// copied from RobotPeer
		final double
			x = Global.bot.getX(),
			y = Global.bot.getY();
		final Point2D me = new Point2D.Double(x, y);
		{
			final double prevAngle =
				Utils.normalAbsoluteAngle(prevRadarHeading - Math.PI/2);
			final double diff =
				Utils.normalRelativeAngle(radarHeading - prevRadarHeading);

			arc.setArc(
				Global.bot.getX() - Rules.RADAR_SCAN_RADIUS,
				Global.bot.getY() - Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				prevAngle * (180.0 / Math.PI),
				diff      * (180.0 / Math.PI),
				Arc2D.PIE);

			for (final Enemy dude : Global.dudes)
			if (!dude.positionUnknown &&
				(
					(!dude.justSeen && arc.intersects(dude.boundingBox)) ||
					(me.distanceSq(dude) >
						Rules.RADAR_SCAN_RADIUS*Rules.RADAR_SCAN_RADIUS)
				)
			)
				dude.positionUnknown = true;
		}

		prevRadarHeading = radarHeading;

		// lock if we have a target
		if (Global.target != null) {
			if (melee) {
				// in melee, lock only if we're about to shoot
				if (readyToLock() && Global.bot.getEnergy() >= 0.2) {

					final long seenDelta =
						now - Global.target.scanTime;

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
								fullCircle();

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
			} else if (
				!melee && now - Global.target.scanTime < 5*Tools.LOCK_ADVANCE
			) {
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

		// if this isn't true, we certainly can't see all!
		boolean canSeeAll = Global.dudes.size() == Global.bot.getOthers();
		// ClockWise, CounterClockWise
		double
			nextCW  = Double.POSITIVE_INFINITY,
			nextCCW = Double.NEGATIVE_INFINITY;

		// to find the minimal arc which covers all dudes, we:
		// sort the list of absBearings
		// consider all two consecutive pairs, including (last,first)
		// find the one with the maximum angle (second - first)
		// that is the maximum arc which covers no dudes
		// so if we invert it, we get the minimum arc which covers them all.
		final ArrayList<Double> absBearings =
			new ArrayList<Double>(Global.dudes.size()+1);

		for (final Enemy dude : Global.dudes)
		if (!dude.dead)
		if (dude.positionUnknown)
			canSeeAll = false;
		else {
			final double absBearing = Tools.currentAbsBearing(dude, me);

			absBearings.add(absBearing);

			final double dist =
				Utils.normalAbsoluteAngle(absBearing - radarHeading);

			if (dist < nextCW)
				nextCW = dist;
			if (dist > nextCCW)
				nextCCW = dist;
		}
		nextCCW -= Math.PI*2;

		if (absBearings.size() > 1) {
			Collections.sort(absBearings);

			// easier than handling the edge case manually
			absBearings.add(absBearings.get(0));

			double maxExtent = Double.NEGATIVE_INFINITY;
			int maxStart = 0;
			for (int i = 0; i < absBearings.size() - 1; ++i) {
				final double diff =
					Utils.normalAbsoluteAngle(
						absBearings.get(i+1) - absBearings.get(i));

				if (diff > maxExtent) {
					maxExtent = diff;
					maxStart = i+1;
				}
			}

			arc.setAngleStart (Tools.toArcAngle(absBearings.get(maxStart)));
			arc.setAngleExtent(360-Tools.toDeg(maxExtent));

			if (PAINT_ENEMY_ARC) {
				Graphics2D g = Global.bot.getGraphics();
				g.setColor(Color.CYAN);
				g.draw(arc);
			}
		} else
			arc.setAngleExtent(0);

		// continue turning in the same direction, flip directions if distance to
		// next guy is > half a circle

		boolean twixtEnemiesCW = clockwise;
		if (
			( clockwise && nextCW  >  Math.PI) ||
			(!clockwise && nextCCW < -Math.PI)
		)
			twixtEnemiesCW ^= true;

		// TODO: if next dude is not edgemost, or arc extent > 180, spin as much
		// as possible, not only next*, in that direction
		// i.e. spin to the last* not next*
		final double twixtEnemiesTurn =
			twixtEnemiesCW ? nextCW : nextCCW;

		// if all enemies were visible, just turn among them
		if (canSeeAll) {
			doExactScan(twixtEnemiesTurn, twixtEnemiesCW);
			return;
		}

		/*
		 * if in a corner, scan only the  90 degree arc away from the corner
		 * if in an edge,  scan only the 180 degree arc away from the edge
		 * if in the middle, 360 degrees
		 */

		final int q = getQuadrant(x, y);
		if (q == 0)
			fullCircle();
		else {
			final double
				arcCentre = arcCentres[q-1],
				arcExtent = arcExtents[q-1];

			// use the greater of this angle and the twixt-enemies angle
			if (arcExtent > Tools.toRad(arc.getAngleExtent())) {
				final double
					edge1 = Utils.normalAbsoluteAngle(arcCentre - arcExtent / 2);

				// Having actually read the documentation this time, I wonder how
				// exactly scanArc() works without using Tools.*ArcAngle at all.

				arc.setAngleStart (Tools.toArcAngle(edge1 -  EXTRATURN));
				arc.setAngleExtent(Tools.toDeg(arcExtent + 2*EXTRATURN));

				if (PAINT_EDGE_ARCS) {
					Graphics2D g = Global.bot.getGraphics();
					g.setColor(Color.MAGENTA);
					g.draw(arc);
				}

				if (possibleSlippage(arc)) {
					fullCircle();
					return;
				}

				final double
					edge2 = Utils.normalAbsoluteAngle(arcCentre + arcExtent / 2);

				scanArc(arcCentre, arcExtent, edge1, edge2);
			} else {
				if (possibleSlippage(arc)) {
					fullCircle();
					return;
				}

				doExactScan(twixtEnemiesTurn, twixtEnemiesCW);
			}
		}
	}

	public void victoryDance() {
		Global.bot.setTurnRadarRightRadians(
			clockwise
				? Double.NEGATIVE_INFINITY
				: Double.POSITIVE_INFINITY);
	}

	public boolean readyToLock() {
		return Global.bot.getGunHeat() / gunCoolingRate < Tools.LOCK_ADVANCE;
	}

	//
	// privates only (and painting)
	//

	private void fullCircle() {
		Global.bot.setTurnRadarRightRadians(
			clockwise
				? Double.POSITIVE_INFINITY
				: Double.NEGATIVE_INFINITY);
		wasOutsideArc = false;
	}

	private void lock(final double th) {
		lock(th, Rules.RADAR_TURN_RATE_RADIANS / 2);
	}
	private void lock(final double targetHeading, final double offset) {
		final double toEnemyCentre =
			Utils.normalRelativeAngle(targetHeading - radarHeading);

		clockwise = toEnemyCentre > 0;

		Global.bot.setTurnRadarRightRadians(
			toEnemyCentre +
			(clockwise ? offset : -offset));

		wasOutsideArc = false;
	}

	private boolean possibleSlippage(final Arc2D arc) {
		final long now = Global.bot.getTime();

		final Point2D me =
			new Point2D.Double(Global.bot.getX(), Global.bot.getY());

		for (final Enemy dude : Global.dudes)
		if (!dude.old) {
			final Point2D pos = dude.guessPosition(now);

			final double absBearing = Tools.currentAbsBearing(pos, me);

			if (!arc.containsAngle(Tools.toArcAngle(absBearing))) {
				return true;
			}
		}
		return false;
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

	private void scanArc(
		final double arcCentre, final double arcExtent,
		final double edge1, final double edge2
	) {
		arc.setAngleStart (edge1     * (180.0 / Math.PI));
		arc.setAngleExtent(arcExtent * (180.0 / Math.PI));

		final boolean outsideArc =
			!this.arc.containsAngle(radarHeading * 180.0 / Math.PI);

		boolean toNext = false;
		double nextEdge = Double.NaN;

		if (outsideArc) {
			if (wasOutsideArc) {
				// get the opposite edge
				// move the radar through the quickest path to that edge

				if (leavingArc)
					return;

				nextEdge = getNextEdge(arcCentre, edge1, edge2);
				toNext = true;

				leavingArc = true;

			} else {
				// we are now outside the arc, but weren't just now
				// so we reached the edge: turn back and go to the other one

				if (clockwise) {
					clockwise = false;
					doExactScan(-arcExtent);
				} else {
					clockwise = true;
					doExactScan( arcExtent);
				}

				leavingArc = false;
			}
		} else {
			// we are currently inside the arc
			// go to the next edge

			nextEdge = getNextEdge(radarHeading, edge1, edge2);
			toNext = true;

			leavingArc = false;
		}

		if (toNext) {
			final double turn =
				Utils.normalRelativeAngle(nextEdge - radarHeading);
			clockwise = turn > 0;
			doExactScan(turn);
		}

		wasOutsideArc = outsideArc;
	}

	private void doExactScan(final double turn) {
		// Add a bit of extra to the turn to account for bot movement, if
		// scanning at a bot, or for scanning the whole quadrant, not just to the
		// middle, if scanning a quadrant.
		Global.bot.setTurnRadarRightRadians(
			turn + Math.signum(turn) * EXTRATURN);
	}
	private void doExactScan(final double turn, final boolean cw) {
		clockwise = cw;
		doExactScan(turn);
	}

	private double getNextEdge(
		final double whence,
		final double edge1, final double edge2
	) {
		final double
			dist1 = Utils.normalRelativeAngle(edge1 - whence),
			dist2 = Utils.normalRelativeAngle(edge2 - whence);
		return
			clockwise
				? (dist1 >= dist2 ? edge1 : edge2)
				: (dist1 <= dist2 ? edge1 : edge2);
	}

	//
	// Only painting stuff henceforth
	//

	public void onPaint(final Graphics2D g) {
		if (!PAINT_QUADRANTS)
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
	}

	private static final boolean
		PAINT_QUADRANTS = false,
		PAINT_EDGE_ARCS = false,
		PAINT_ENEMY_ARC = true;
}
