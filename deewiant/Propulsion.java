package deewiant;
import robocode.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

final public class Propulsion {
	private AdvancedRobot     bot;
	private PrintStream       out;
	private Collection<Enemy> dudes;
	private Engine            engine;

	public Propulsion(AdvancedRobot r) {
		bot = r;
		out = bot.out;

		dudes = new ArrayList<Enemy>(0);
		engine = new RiskMinimiser();
	}

	public void propel(Enemy e) {
		goTo(engine.destination(e));
		return;
	}

	public void victoryDance() {
		bot.setMaxVelocity(0);
		bot.setTurnLeftRadians(Double.POSITIVE_INFINITY);
	}

	public void updateEnemies(Collection<Enemy> dudes) {
		this.dudes = dudes;

		if (dudes.size() == 1 && !(engine instanceof Rammer) && ((Enemy)(dudes.toArray()[0])).energy < 0.1)
			engine = new Rammer();
	}

	private void goTo(Point2D.Double destination) {
		Point2D.Double botPos = new Point2D.Double(bot.getX(), bot.getY());

		double distance = botPos.distance(destination);
		double turn = Utils.atan2(destination, botPos) - bot.getHeadingRadians();

		// woo, BackAsFront
		if (Math.cos(turn) < 0) {
			turn += Math.PI;
			distance = -distance;
		}

		bot.setTurnRightRadians(Utils.normaliseBearing(turn));

		// interesting snippet from FloodHT... don't hit the wall while turning
		// hooray for parentheses... look out kids, the LISP monster is coming!
		if (!new Rectangle2D.Double(30, 30, bot.getBattleFieldWidth()-60, bot.getBattleFieldHeight()-60).contains((Utils.projectVector(botPos, bot.getHeadingRadians(), Math.max(-20, Math.min(20, distance))))))
			distance = 0;

		// and hence, don't move while turning (much)
		bot.setAhead(Math.abs(bot.getTurnRemainingRadians()) > 1 ? 0 : distance);
	}

	private abstract class Engine {
		public abstract Point2D.Double destination(Enemy dude);
		Engine(String s) { out.println("Movement set to: " + s); }
	}

	final private class RiskMinimiser extends Engine {
		private Point2D.Double current,
		                       previous,
		                       next;

		private double mapWidth, mapHeight,
		               botWidth, botHeight,
		               botNRG;

		RiskMinimiser() { super("Minimum Risk Movement"); }

		private void updateBot() {
			mapWidth  = bot.getBattleFieldWidth();
			mapHeight = bot.getBattleFieldHeight();
			current   = new Point2D.Double(bot.getX(), bot.getY());
			botWidth  = bot.getWidth();
			botHeight = bot.getHeight();
			botNRG    = bot.getEnergy();
		}

		public Point2D.Double destination(Enemy target) {
			updateBot();

			if (target == null)
				return current; // id est, don't move

			if (previous == null)
				previous = current;
			if (next == null)
				next = previous;

			boolean newNext = false;
			double distance = Math.min(300, current.distance(target) / 2);
			for (double angle = 0.0; angle < 2 * Math.PI; angle += 0.1) {
				Point2D.Double point = Utils.projectVector(current, angle, distance);

				if (point.x < botWidth || point.x > mapWidth - botWidth ||
				    point.y < botHeight || point.y > mapHeight - botHeight)
				    continue;

				if (risk(point) < risk(next)) {
					newNext = true;
					next = point;
				}
			}

			if (newNext)
				previous = current;

			return next;
		}

		private double risk(Point2D.Double point) {
			double fullRisk = 4 / previous.distanceSq(point) + 0.1 / current.distanceSq(point);

			for (Enemy dude : dudes) {
				double risk = Math.max(botNRG, dude.energy) / point.distanceSq(dude);

				// from HawkOnFire/Kawigi
				// David Alves's "perpendicularity": 1 if moving right at enemy, 0 if moving perpendicular
				double perpendicularity = Math.abs(Math.cos(Utils.atan2(current, point)) - Utils.atan2(dude, point));
				double willTheyShootMe = likelyToBeTargeted(point, dude);

				risk *= 1 + willTheyShootMe * perpendicularity;

				// if path to point collides with dude, very bad
				// from Kawigi:
				// "I'm figuring that if to [sic] 36x36 squares will hit each other moving along a certain vector
				// (my vector, and I assume they'll be in roughly the same place), then a line representing
				// the vector will intersect a 72x72 square centered in the same place."
				if (new Line2D.Double(current, point).intersects(new Rectangle2D.Double(dude.x - 36, dude.y - 36, 72, 72)))
					return Double.POSITIVE_INFINITY;

				fullRisk += risk;
			}

			return fullRisk;
		}

		// guess how likely it is that scaryGuy will target me if I go to point
		// mostly Kawigi
		private double likelyToBeTargeted(Point2D.Double point, Enemy scaryGuy) {
			// how many enemies are closer to scaryGuy than point is
			int closerDudeNum = 0;

			for (Enemy dude : dudes)
				if (dude != scaryGuy && scaryGuy.distance(dude) * 0.9 < scaryGuy.distance(current))
					++closerDudeNum;

			if (closerDudeNum == 0)
				return 1;
			else
				return 1.0 / closerDudeNum;
		}
	} // end RiskMinimiser

	final private class Rammer extends Engine {
		Rammer() { super("Ramming"); }
		public Point2D.Double destination(Enemy target) { return target; }
	}
} // end Propulsion
