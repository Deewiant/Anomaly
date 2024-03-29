// File created: prior to 2005-11-30

package deewiant.common;

import robocode.util.Utils;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class Tools {
	////// Anomaly specifics
	public final static int LOCK_ADVANCE = 5;

	////// RoboCode specifics
	public final static double BOT_WIDTH  = 36;
	public final static double BOT_HEIGHT = BOT_WIDTH;

	public static void setBotBox(final Rectangle2D box, final Point2D pt) {
		// right out of RobotPeer
		box.setRect(
			pt.getX() - Tools.BOT_WIDTH /2 + 2,
			pt.getY() - Tools.BOT_HEIGHT/2 + 2,
			Tools.BOT_WIDTH - 4,
			Tools.BOT_HEIGHT - 4);
	}

	////// Java specifics
	public static double toArcAngle(final double ang) {
		return toDeg(ang) - 90;
	}
	public static double fromArcAngle(final double ang) {
		return toRad(ang + 90);
	}

	////// general geometry
	public static Point2D.Double projectVector(
		final Point2D from,
		final double angle, final double length
	) {
		return new Point2D.Double(
			from.getX() + Math.sin(angle) * length,
			from.getY() + Math.cos(angle) * length);
	}

	public static double atan2(final Point2D b, final Point2D a) {
		// Yes the x,y argument order is correct: it effectively puts zero north
		// and makes it increase clockwise
		return Math.atan2(b.getX() - a.getX(), b.getY() - a.getY());
	}

	public static double absAngleInDirection(final Point2D v) {
		// Ditto atan2
		return Utils.normalAbsoluteAngle(Math.atan2(v.getX(), v.getY()));
	}

	public static double currentAbsBearing(final Point2D a, final Point2D me) {
		return Utils.normalAbsoluteAngle(atan2(a, me));
	}

	public static double toDeg(final double ang) {
		return ang * (180.0 / Math.PI);
	}
	public static double toRad(final double ang) {
		return ang * (Math.PI / 180.0);
	}

	////// general math
	// if var < min, var = min; if var > max, var = max
	public static double between(
		final double var, final double min, final double max
	) {
		return Math.max(min, Math.min(var, max));
	}
	public static float between(
		final float var, final float min, final float max
	) {
		return Math.max(min, Math.min(var, max));
	}
	public static boolean near(final double a, final double b) {
		return zero(a - b);
	}
	public static boolean zero(final double a) {
		return Math.abs(a) < .1;
	}

	// works fine even if min > max.
	public static double fromToRange(
		final double oldmin, final double oldmax,
		final double newmin, final double newmax,
		final double val
	) {
		return (val - oldmin) * (newmax - newmin) / (oldmax - oldmin) + newmin;
	}
	public static float fromToRange(
		final float oldmin, final float oldmax,
		final float newmin, final float newmax,
		final float val
	) {
		return (val - oldmin) * (newmax - newmin) / (oldmax - oldmin) + newmin;
	}

	////// colours
	// hue ranges from 0-360; saturation, luminance are 0-1
	// Color takes only floats for some reason...
	public static Color HSLtoRGB(final float h, final float s, final float l) {
		if (s == 0)
			return new Color(l, l, l);
		else {
			final float q = (l < 0.5f) ? l*(s+1) : (l+s - l*s);
			final float p = 2*l - q;
			final float hk = h / 360;

			final float r = normalizeColourComponent(hk + 1f/3);
			final float g = normalizeColourComponent(hk);
			final float b = normalizeColourComponent(hk - 1f/3);

			final float rf = finalizeColourComponent(r, p, q);
			final float gf = finalizeColourComponent(g, p, q);
			final float bf = finalizeColourComponent(b, p, q);

			return new Color(
				between(rf, 0, 1),
				between(gf, 0, 1),
				between(bf, 0, 1)
			);
		}
	}

	private static float finalizeColourComponent(
		final float c, final float p, final float q
	) {
		if (c < 1f/6)
			return p + 6*c*(q-p);
		else if (c < 0.5f)
			return q;
		else if (c < 2f/3)
			return p + 6*(2f/3 - c)*(q-p);
		else
			return p;
	}

	private static float normalizeColourComponent(final float c) {
		if (c < 0)
			return c + 1;
		else if (c > 1)
			return c - 1;
		else
			return c;
	}
}
