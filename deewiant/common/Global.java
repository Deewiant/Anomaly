// File created: 2007-11-22 19:37:37

package deewiant.common;

import robocode.AdvancedRobot;

import java.io.PrintStream;
import java.util.Map;

public final class Global {
	private Global() {}

	public static Enemy              target;
	public static Map<String, Enemy> dudes;
	public static AdvancedRobot      bot;
	public static PrintStream        out;
	public static double
		mapWidth, mapHeight,
		damageTaken;
}
