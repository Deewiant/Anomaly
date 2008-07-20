package deewiant;
import robocode.*;
import java.awt.geom.Point2D;

// kudos to Kawigi for this Point2D inheritance idea
final public class Enemy extends Point2D.Double {
	public String name;
	public double headingRad,
	              deltaHeading,
	              bearingRad,
	              distance,
	              energy,
	              speed;
	public long   scanTime;

	public Point2D.Double guessPosition(long when) {
		// http://www-128.ibm.com/developerworks/library/j-circular/

		double newX, newY;
		long diff = when - scanTime;

		if (Math.abs(deltaHeading) > 0.00001) {
			// circular path prediction
			double radius     = speed / deltaHeading,
			       totHeading = diff * deltaHeading;

			newX = x + radius * (Math.cos(headingRad) - Math.cos(headingRad + totHeading));
			newY = y + radius * (Math.sin(headingRad + totHeading) - Math.sin(headingRad));
		} else {
			// linear path prediction
			newX = x + Math.sin(headingRad) * speed * diff;
			newY = y + Math.cos(headingRad) * speed * diff;
		}

		return new Point2D.Double(newX, newY);
	}
}
