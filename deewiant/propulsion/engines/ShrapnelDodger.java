// File created: 2008-08-06 18:50:33

package deewiant.propulsion.engines;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import robocode.Rules;
import robocode.util.Utils;

import deewiant.common.Enemy;
import deewiant.common.Global;
import deewiant.common.Tools;
import deewiant.common.VirtualBullets;
import deewiant.propulsion.engines.model.Engine;
import static deewiant.common.VirtualBullets.VirtualBullet;
import static deewiant.common.VirtualBullets.VirtualBulletHandler;

public final class ShrapnelDodger extends Engine {
	public ShrapnelDodger() {
		super("Shrapnel Dodging");
	}

	private static final boolean
		DRAW_BULLETS = true,
		DRAW_VECTOR = true;

	private static final double
		BULLET_FORCE = -500,
		DUDE_FORCE   = -5000,
		WALL_FORCE   = -1000; // Only applied when near wall

	private final VirtualBullets bullets = new VirtualBullets();

	private static final class BulletAvoider implements VirtualBulletHandler {
		public double fx, fy;

		public boolean dealWith(final VirtualBullet bul) {
			if (DRAW_BULLETS) {
				final Graphics2D g = Global.bot.getGraphics();
				g.setColor(Color.RED);

				final double r = Math.min(4, bul.power * 4);
				g.draw(new Ellipse2D.Double(bul.x - r, bul.y - r, 2*r, 2*r));
			}

			if (Global.meBox.contains(bul.x, bul.y))
				return true;

			final double force   = BULLET_FORCE*bul.power / Global.me.distanceSq(bul);
			final double bearing = Tools.currentAbsBearing(bul, Global.me);

			fx += Math.sin(bearing) * force;
			fy += Math.cos(bearing) * force;

			return false;
		}
	}
	private static final BulletAvoider bulletAvoider = new BulletAvoider();

	public void gameOver() { bullets.clear(); }

	public void move() {

		// avoid bullets
		// (and draw them)
		bulletAvoider.fx = bulletAvoider.fy = 0;
		bullets.handleLiveOnes(bulletAvoider);

		double
			fx = bulletAvoider.fx,
			fy = bulletAvoider.fy;

Global.out.printf("Bullet avoidance: (%f, %f)\n", fx, fy);

		// avoid enemies
   	for (final Enemy dude : Global.dudes) if (!dude.old) {
			final double bearing = Tools.currentAbsBearing(dude, Global.me);
			final double force   = DUDE_FORCE / Global.me.distanceSq(dude);
Global.out.printf("%s: (%f, %f) (strength %f)\n", dude.name, Math.sin(bearing)*force,Math.cos(bearing)*force, force);
			fx += Math.sin(bearing) * force;
			fy += Math.cos(bearing) * force;
		}

		// avoid walls
		final double
			x = Global.me.getX(),
			y = Global.me.getY(),
			mw = Global.mapWidth,
			mh = Global.mapHeight,
			ww = Tools.BOT_WIDTH/2,
			wh = Tools.BOT_HEIGHT/2,
			 eastDist = Math.abs(mw - ww - x),
			 westDist = Math.abs(ww - x),
			northDist = Math.abs(mh - wh - y),
			southDist = Math.abs(wh - y);

		if ( eastDist < ww) { Global.out.printf("East  wall: (%f, 0) (dist %f)\n", WALL_FORCE / eastDist,  eastDist);   fx += WALL_FORCE /  eastDist; }
		if ( westDist < ww) { Global.out.printf("West  wall: (%f, 0) (dist %f)\n", -WALL_FORCE / westDist,  westDist);  fx -= WALL_FORCE /  westDist; }
		if (northDist < wh) { Global.out.printf("North wall: (0, %f) (dist %f)\n",  WALL_FORCE / northDist, northDist); fy += WALL_FORCE / northDist; }
		if (southDist < wh) { Global.out.printf("South wall: (0, %f) (dist %f)\n", -WALL_FORCE / southDist, southDist); fy -= WALL_FORCE / southDist; }

Global.out.printf("Total: (%f, %f)\n", fx, fy);
Global.out.printf("Turning toward: %f\n", Math.toDegrees(Math.atan2(fy, fx)));

		final double ahead;
		if (turnToward(Tools.atan2(new Point2D.Double(fx, fy), Global.me)))
			ahead = Double.NEGATIVE_INFINITY;
		else
			ahead = Double.POSITIVE_INFINITY;

		Global.bot.setAhead(ahead);
		Global.bot.setMaxVelocity(Double.POSITIVE_INFINITY);

		if (DRAW_VECTOR) {
			final Graphics2D g = Global.bot.getGraphics();
			g.setColor(Color.PINK);

			g.draw(new Line2D.Double(
				Global.me,
				Tools.projectVector(
					Global.me,
					Tools.atan2(new Point2D.Double(fx, fy), Global.me),
					100)));
		}
	}

	public void onFired(final Enemy dude) {
		// just HOT for now
		bullets.add(new VirtualBullet(
			dude,
			dude.firePower,
			Utils.normalAbsoluteAngle(dude.absBearing + Math.PI)));
	}
}
