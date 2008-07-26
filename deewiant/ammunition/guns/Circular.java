// File created: 2007-11-22 18:51:51

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.common.Circulinear;
import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Enemy;

public final class Circular extends Gun {
	public Circular() { super("Circular"); }

	public final double setSights(final Enemy dude, final double bSpeed) {
		return Circulinear.calculate(dude, bSpeed, true);
	}
}
