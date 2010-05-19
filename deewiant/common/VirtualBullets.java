// File created: 2008-08-06 19:04:51

package deewiant.common;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;

import robocode.Rules;

public final class VirtualBullets {
	public static final class VirtualBullet extends Point2D.Double {
		public double prevX, prevY, dx, dy, power;
		public boolean alive;
		public Line2D line;

		public VirtualBullet(
			final Point2D p,
			final double power,
			final double heading
		) {
			x = prevX = p.getX();
			y = prevY = p.getY();

			this.power = power;
			final double speed = Rules.getBulletSpeed(power);

			dx = speed * Math.sin(heading);
			dy = speed * Math.cos(heading);

			line = new Line2D.Double();
			alive = true;
		}

		public void move() {
			prevX = x;
			prevY = y;

			x += dx;
			y += dy;

			line.setLine(prevX, prevY, x, y);
		}
	}

	public static interface VirtualBulletHandler {
		// returns true if the bullet can be disposed of
		public boolean dealWith(final VirtualBullet bul);
	}

	private ArrayDeque<VirtualBullet> bullets =
		new ArrayDeque<VirtualBullet>();

	public void add(final VirtualBullet b) { bullets.add(b); }

	public void handleLiveOnes(final VirtualBulletHandler handler) {
		if (bullets.isEmpty())
			return;

		int removableBottom = -1, removableTop = -1;
		boolean irremovableBottom = false, irremovableTop = true;

		int i = 0;
		for (final VirtualBullet bul : bullets) {

			boolean removable = false;
			if (
				bul.alive  &&
				bul.x >= 0 && bul.x <= Global.mapWidth &&
				bul.y >= 0 && bul.y <= Global.mapHeight
			) {
				bul.move();
				if (handler.dealWith(bul))
					removable = true;
				else {
					irremovableBottom = true;
					irremovableTop    = true;
				}
			} else
				removable = true;

			if (removable) {
				bul.alive = false;
				if (!irremovableBottom)
					removableBottom = i;

				if (irremovableTop) {
					removableTop = i;
					irremovableTop = false;
				}
			}
			++i;
		}

		// remove as many as possible from bottom and top of deque

		if (removableBottom == bullets.size()-1) {
			bullets.clear();
			return;
		}
		while (removableBottom-- >= 0)
			bullets.removeFirst();

		if (!irremovableTop) {
			removableTop = bullets.size() - removableTop;
			while (removableTop-- >= 0)
				bullets.removeLast();
		}
	}
}
