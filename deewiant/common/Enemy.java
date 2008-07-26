// File created: prior to 2005-11-30

package deewiant.common;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import deewiant.ammunition.guns.model.Gun;

// kudos to Kawigi for Point2D inheritance idea
public final class Enemy extends Point2D.Double {
	public String name;
	public int id;

	public double
		absBearing, // angle from us to enemy, [0,2π)
		avgApproachVelocity,
		bearing,    // angle from us to enemy, [-π,π)
		deltaHeading,
		distance,
		energy,
		firePower,
		heading,    // angle enemy is moving in, [0,2π)
		velocity,
		hurtMe;
	public long
		scanTime,
		lastShootTime, // guess
		shotAtCount,
		hitCount,
		shotCount, // guess
		hitMeCount;
	public boolean
		dead,
		justSeen,
		old,
		positionUnknown;

	public Rectangle2D
		boundingBox = new Rectangle2D.Double(),
		vicinity    = new Rectangle2D.Double();

	public double
		prevEnergy,
		prevHeading,
		prevVelocity,
		prevX,
		prevY;

	////////////////////////////////////////////////////////////////////////////
	// accuracy info for Ammunition, remembered across rounds

	public static final class VirtualBullet {
		public double x, y, prevX, prevY, dx, dy;
		public boolean alive;
		public Line2D line;

		public void move() {
			prevX = x;
			prevY = y;

			x += dx;
			y += dy;

			line.setLine(prevX, prevY, x, y);
		}
	}

	public static final class VirtualGun {
		public Gun gun;
		public ArrayDeque<VirtualBullet> bullets;
	}

	public VirtualGun[] virtualGuns;
	////////////////////////////////////////////////////////////////////////////

	public void newInfo() {
		prevEnergy   = energy;
		prevHeading  = heading;
		prevVelocity = velocity;
		prevX        = x;
		prevY        = y;
	}

	public Point2D guessPosition(final long when) {
		return guessCircularPosition(when);
	}

	// http://www-128.ibm.com/developerworks/library/j-circular/
	public Point2D guessCircularPosition(final long when) {

		final long diff = when - scanTime;

		Point2D guess =
			Math.abs(deltaHeading) > 0.0001
				? circularPos(diff)
				: linearPos(diff);

		// assume that enemy stops at wall
		guess.setLocation(
			Tools.between(guess.getX(), 18, Global.mapWidth  - 18),
			Tools.between(guess.getY(), 18, Global.mapHeight - 18));

		return guess;
	}
	public Point2D guessLinearPosition(final long when) {
		return linearPos(when - scanTime);
	}
	private Point2D circularPos(final long diff) {
		final double
			radius = velocity / deltaHeading,
		   totHeading = diff * deltaHeading;

		return new Point2D.Double(
			x + radius * (Math.cos(heading) - Math.cos(heading + totHeading)),
			y + radius * (Math.sin(heading + totHeading) - Math.sin(heading)));
	}
	private Point2D linearPos(final long diff) {
		return new Point2D.Double(
			x + velocity * Math.sin(heading) * diff,
			y + velocity * Math.cos(heading) * diff);
	}
}
