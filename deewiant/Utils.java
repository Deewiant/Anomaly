package deewiant;

import java.awt.geom.Point2D;

public class Utils {
	static double normaliseBearing(double ang) {
		while (ang > Math.PI)
			ang -= 2 * Math.PI;
		while (ang < -Math.PI)
			ang += 2 * Math.PI;
		return ang;
	}

	static double normaliseHeading(double ang) {
		while (ang > 2 * Math.PI)
			ang -= 2 * Math.PI;
		while (ang < 0)
			ang += 2 * Math.PI;
		return ang;
	}

	static double distance(double x1, double y1, double x2, double y2) {
		return Math.hypot(x2 - x1, y2 - y1);
	}

	// dot product
	static double dot(double x1, double y1, double x2, double y2) {
		return (x1 * y1 + x2 * y2);
	}

	// vector length
	static double length(double x, double y) {
		return Math.hypot(x, y);
	}
	static double length(Point2D.Double vec) {
		return Utils.length(vec.x, vec.y);
	}

	static Point2D.Double projectVector(Point2D.Double from, double angle, double length) {
		return new Point2D.Double(from.x + Math.sin(angle) * length, from.y + Math.cos(angle) * length);
	}
	static Point2D.Double projectVector(double fromX, double fromY, double toX, double toY) {
		double angle = Math.atan2(toX - fromX, toY - fromY);
		double length = Utils.length(toX - fromX, toY - fromY);
		return Utils.projectVector(new Point2D.Double(fromX, fromY), angle, length);
	}
	
	static double atan2(Point2D.Double b, Point2D.Double a) {
		return Math.atan2(b.x - a.x, b.y - a.y);
	}
}