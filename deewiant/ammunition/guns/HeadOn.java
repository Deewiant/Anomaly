// File created: 2007-11-22 18:22:50

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Global;
import deewiant.common.Enemy;

public final class HeadOn extends Gun {
	public HeadOn() { super("Head-on"); }

	public final double setSights(final Enemy dude, final double bSpeed) {
		return dude.absBearing;
	}
}
