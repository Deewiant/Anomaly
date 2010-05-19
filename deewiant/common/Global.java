// File created: 2007-11-22 19:37:37

package deewiant.common;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import robocode.AdvancedRobot;

public final class Global {
	private Global() {}

	public static Enemy                target;
	public static Map<String, Integer> dudeIds = new HashMap<String,Integer>();
	public static ArrayList<Enemy>     dudes   = new ArrayList<Enemy>();
	public static int                  id = 0;
	public static AdvancedRobot        bot;
	public static PrintStream          out;
	public static Point2D              me    = new Point2D.Double();
	public static Rectangle2D          meBox = new Rectangle2D.Double();
	public static double
		mapWidth, mapHeight,
		damageTaken;
}
