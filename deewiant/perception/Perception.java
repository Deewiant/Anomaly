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
		prevCW,
		prevCCW,
		prevToCW,
		prevToCCW,
		priorToCircle = -1;

	private boolean
		// True if going clockwise, false if going counterclockwise.
		//
		// The main purpose of clockwise is to prevent the spin direction
		// spontaneously changing when we switch between radar modes (corner arc
		// to edge arc, enemies arc to edge/corner arc, any arc to full circle,
		// etc.)
		clockwise = true,
		// somebody nearby that we haven't seen, woops!
		pleaseFullCircle = false,
		wasInArc = true;

	private final Arc2D
		// temp arc used here and there
		arc = new Arc2D.Double(),
		// the arc the radar last spun through
		radarArc = new Arc2D.Double();

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
		 */

		if (quadrants[0] == null) {
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
		radarHeading = Global.bot.getRadarHeadingRadians();

		// if just started, spin 360 degrees once
		final long now = Global.bot.getTime();
		if (now < 8)
			Global.bot.setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
		else {
			// check for long-lost dudes
			// straight from RobotPeer
			final double prevAngle =
				Utils.normalAbsoluteAngle(prevRadarHeading - Math.PI/2);
			final double diff =
				Utils.normalRelativeAngle(radarHeading - prevRadarHeading);

			radarArc.setArc(
				Global.me.getX() - Rules.RADAR_SCAN_RADIUS,
				Global.me.getY() - Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				2 * Rules.RADAR_SCAN_RADIUS,
				Tools.toDeg(prevAngle),
				Tools.toDeg(diff),
				Arc2D.PIE);

			for (final Enemy dude : Global.dudes)
			if (!dude.positionUnknown && !dude.justSeen &&
				(
					radarArc.contains(dude.boundingBox) ||

					dude.distance > Rules.RADAR_SCAN_RADIUS
					// More accurate than the above but if he's that far it's
					// usually only one or two turns before he's really out of sight
					// anyway, so just use the quicker check
					//
					// !dude.boundingBox.intersectsLine(
					// 	new Line2D.Double(
					// 		Global.me,
					// 		Tools.projectVector(
					// 			Global.me,
					// 			Tools.currentAbsBearing(dude, Global.me),
					// 			Rules.RADAR_SCAN_RADIUS)))
				)
			)
				dude.positionUnknown = true;

			arc.setArc(radarArc);

			// actually do stuff
			doPerceive(Global.bot.getOthers() > 1, now);
		}

		prevRadarHeading = radarHeading;
	}

	private void doPerceive(final boolean melee, final long now) {
		// lock if we have a target
		// in melee, only if we're about to shoot
		// in one-on-one, keep the lock for a short time after last seen
		if (
			Global.target != null && (
				( melee && readyToLock()) ||
				(!melee && now - Global.target.scanTime < 5*Tools.LOCK_ADVANCE))
		) {
			lock(Global.target.absBearing);
			return;
		}

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

		boolean canSeeAll = Global.dudes.size() >= Global.bot.getOthers();

		final ArrayList<Double>
			absBearings    = new ArrayList<Double>(2*Global.dudes.size()+1),
			unseenBearings = new ArrayList<Double>(2*Global.dudes.size());

		for (final Enemy dude : Global.dudes) if (!dude.dead)
		if (dude.positionUnknown) {
			canSeeAll = false;
			break;
		} else {
			// TODO FIXME XXX: this is a bit of a HACK
			// what we really want is the worst-case absBearings
			// i.e. assume he moves at maximum speed perpendicular to us forward
			// or backward
			// use both of those positions instead of these two
			// (not sure what to do about walls here: I guess correct would be to
			// find the pos, if it's within the wall keep the same distance but
			// move it along the circle so that it's within the battlefield)
			final double
				ab        = Tools.currentAbsBearing(dude,            Global.me),
				guessedAB = Tools.currentAbsBearing(dude.guessedPos, Global.me);

			absBearings.add(ab);
			absBearings.add(guessedAB);

			// When moving, we tend to have to turn "just a bit more" to reach
			// someone's exact pos, so use justSeen to break the cycle: consider
			// only those we haven't justSeen.
			if (!dude.justSeen) {
				unseenBearings.add(ab);
				unseenBearings.add(guessedAB);
			}
		}

		if (canSeeAll && absBearings.size() > 1) {
			// To find the minimal arc which covers all dudes, we:

			// - Sort the list of absBearings.
			Collections.sort(absBearings);

			// - Consider all two consecutive pairs, including (last,first).
			absBearings.add(absBearings.get(0));

			// - Find the one with the maximum angle (second - first).
			//   This is the maximum arc which covers no dudes.
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

			// - Invert it: this is the minimum arc which covers all dudes.
			final double arcExtent = 2*Math.PI - maxExtent;

			arc.setAngleStart (Tools.toArcAngle(absBearings.get(maxStart)));
			arc.setAngleExtent(Tools.toDeg(arcExtent));

			if (PAINT_ENEMY_ARC) {
				Graphics2D g = Global.bot.getGraphics();
				g.setColor(Color.CYAN);
				g.draw(arc);
			}

			final double arcCentre = absBearings.get(maxStart) + arcExtent/2;

			// if we can see everybody at once, lock in their midst
   		if (unseenBearings.isEmpty()) {
				lock(arcCentre);
				return;
			}

			// otherwise, turn between the dudes we didn't see now

			double
				cw  = arcCentre,
				ccw = cw,
				min = Double.POSITIVE_INFINITY,
				max = Double.NEGATIVE_INFINITY;

			for (final double absBearing : unseenBearings) {
				final double ab =
					Utils.normalRelativeAngle(absBearing - arcCentre);

				if (ab < min) { min = ab; ccw = absBearing; }
				if (ab > max) { max = ab; cw  = absBearing; }
			}
			assert (min <= max);

			scanExactArc(cw, ccw);
			return;
		}

		/*
		 * if in a corner, scan only the  90 degree arc away from the corner
		 * if in an edge,  scan only the 180 degree arc away from the edge
		 * if in the middle, 360 degrees
		 */

		final int q = getQuadrant(Global.me.getX(), Global.me.getY());
		if (q == 0)
			fullCircle();
		else {
			final double
				arcCentre = arcCentres[q-1],
				arcExtent = arcExtents[q-1];

			final double
				ccw = Utils.normalAbsoluteAngle(arcCentre - arcExtent / 2);

			arc.setAngleStart (Tools.toArcAngle(ccw  -   EXTRATURN));
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

			arc.setAngleStart (Tools.toArcAngle(ccw));
			arc.setAngleExtent(Tools.toDeg(arcExtent));

			final double
				cw = Utils.normalAbsoluteAngle(arcCentre + arcExtent / 2);

			scanInexactArc(cw, ccw);
		}
	}

	public void victoryDance() {
		Global.bot.setTurnRadarRightRadians(
			clockwise
				? Double.NEGATIVE_INFINITY
				: Double.POSITIVE_INFINITY);
	}

	public boolean readyToLock() {
		return
			Global.bot.getEnergy() >= 0.2 &&
			Global.bot.getGunHeat() / gunCoolingRate < Tools.LOCK_ADVANCE;
	}

	//
	// privates only (and painting)
	//

	private void fullCircle() {
		Global.bot.setTurnRadarRightRadians(
			clockwise
				? Double.POSITIVE_INFINITY
				: Double.NEGATIVE_INFINITY);
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
	}

	private boolean possibleSlippage(final Arc2D arc) {
		final long now = Global.bot.getTime();

		final Point2D me =
			new Point2D.Double(Global.bot.getX(), Global.bot.getY());

		for (final Enemy dude : Global.dudes)
		if (!dude.old) {
			final double absBearing =
				Tools.currentAbsBearing(dude.guessedPos, me);

			if (!arc.containsAngle(Tools.toArcAngle(absBearing)))
				return true;
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

	// Turn between cw and ccw.
	// Assumes cw is clockwise of arc's centre and ccw is counterclockwise.
	// Assumes arc represents the arc.
	// Assumes arc extent does not exceed 180 degrees.
	private void scanExactArc(final double cw, final double ccw) {
		final double
			toCW  = Utils.normalRelativeAngle( cw - radarHeading),
			toCCW = Utils.normalRelativeAngle(ccw - radarHeading);

		// We went outside the arc and it's all in one direction: go in that
		// direction.
		//
		// We need wasInArc since it's possible that we're on the completely
		// opposite side of the arc, in which case the signums would oscillate
		// and we'd be scanning the arc on the opposite side.
		if (wasInArc && Math.signum(toCW) == Math.signum(toCCW))
			clockwise = toCW > 0;

		prevCW   = cw;
		prevCCW  = ccw;
		wasInArc = arc.containsAngle(Tools.toArcAngle(radarHeading));

		doExactScan(
			wasInArc
				? (clockwise ? toCW : toCCW)
				// Turn toward the far end if outside arc.
				// Explanatory pic:
				//
				// \ /
				//  B---
				//
				// B is the bot, --- is the current pos, \/ is the arc
				// turning anticlockwise
				//
				// If we don't do this, we'd turn first to the / and stop there,
				// which is a waste if we could turn further.
				: (clockwise ? Math.max(toCW, toCCW) : Math.min(toCW, toCCW)));
	}

	// Turn between cw and ccw, but add EXTRATURN to the turn at each edge.
	// Same assumptions as above.
	private void scanInexactArc(final double cw, final double ccw) {
		double
			toCW  = Utils.normalRelativeAngle( cw - radarHeading),
			toCCW = Utils.normalRelativeAngle(ccw - radarHeading);

		// We need the non-signum tests here since with EXTRATURN, the arc extent
		// may exceed 180 degrees.
		if (wasInArc && (
			Math.signum(toCW) == Math.signum(toCCW) ||
			( clockwise && radarArc.containsAngle(Tools.toArcAngle( cw))) ||
			(!clockwise && radarArc.containsAngle(Tools.toArcAngle(ccw)))
		)) {
			clockwise ^= true;

			// HACK?
			// Takes care of when the arc extent is 180 degrees and we want to
			// spin within it all the time.
			if (Math.signum(prevToCW)  != Math.signum(toCW))  toCW  *= -1;
			if (Math.signum(prevToCCW) != Math.signum(toCCW)) toCCW *= -1;
		}

		prevCW    = cw;
		prevCCW   = ccw;
		prevToCW  = toCW;
		prevToCCW = toCCW;
		wasInArc  = arc.containsAngle(Tools.toArcAngle(radarHeading));

		final double turn =
			wasInArc
				? (clockwise ? toCW : toCCW)
				: (clockwise ? Math.max(toCW, toCCW) : Math.min(toCW, toCCW));

		doExactScan(turn + Math.signum(turn)*EXTRATURN);
	}

	private void doExactScan(final double turn) {
		Global.bot.setTurnRadarRightRadians(turn);
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
		PAINT_EDGE_ARCS = true,
		PAINT_ENEMY_ARC = true;
}
