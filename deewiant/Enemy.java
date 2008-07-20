// File created: prior to 2005-11-30
package deewiant;
import robocode.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

// kudos to Kawigi for this Point2D inheritance idea
final public class Enemy extends Point2D.Double {
	public String name;
	public double /*absBearing,*/
	              avgApproachVelocity,
	              bearing,
	              deltaHeading,
	              distance,
	              energy,
	              firePower,
	              heading,
	              velocity;
	public long   scanTime,
	              lastAccelTime,
	              lastShootTime;

	public double prevEnergy,
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

	public Point2D.Double guessPosition(final long when) {
		// http://www-128.ibm.com/developerworks/library/j-circular/

		double newX, newY;
		final long diff = when - scanTime;

		if (Math.abs(deltaHeading) > 0.00001) {
			// circular path prediction
			final double
				radius     = velocity / deltaHeading,
				totHeading = diff * deltaHeading;

			newX = x + radius * (Math.cos(heading) - Math.cos(heading + totHeading));
			newY = y + radius * (Math.sin(heading + totHeading) - Math.sin(heading));
		} else {
			// linear path prediction
			newX = x + velocity * Math.sin(heading) * diff;
			newY = y + velocity * Math.cos(heading) * diff;
		}

		return new Point2D.Double(newX, newY);
	}
}
