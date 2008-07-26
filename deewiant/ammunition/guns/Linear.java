// File created: 2007-11-22 18:48:28

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.common.Circulinear;
import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Enemy;

public final class Linear extends Gun {
	public Linear() { super("Linear"); }

	public final double setSights(final Enemy dude, final double bSpeed) {
		return Circulinear.calculate(dude, bSpeed, false);
	}
}
