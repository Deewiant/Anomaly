// File created: prior to 2005-11-30

package deewiant.common;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Map;

// kudos to Kawigi for Point2D inheritance idea
public final class Enemy extends Point2D.Double {
	public String name;
	public double
		absBearing,
		avgApproachVelocity,
		bearing,
		deltaHeading,
		distance,
		energy,
		firePower,
		heading,
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
		old,
		positionUnknown = true;

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
/*	// static info for WaveSurfing
	final protected class StaticInfo {
		private long scans;
	}
	private static Map<String, StaticInfo> info = new HashMap<String, StaticInfo>();
	public static StaticInfo memory;

	// you know, static, statify...
	public void statify() {
		if (!Enemy.info.containsKey(this.name))
			Enemy.info.put(this.name, new StaticInfo());
		memory = Enemy.info.get(this.name);
	}
*/	//\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

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

		return
			Math.abs(deltaHeading) > 0.0001
			? circularPos(diff)
			: linearPos(diff);
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
