// File created: 2007-11-22 18:22:50

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.model.Gun;
import deewiant.common.Global;

public final class HeadOn extends Gun {
	public HeadOn() { super("Head-on"); }

	public final void setSights() {
		super.targetAngle = Global.target.absBearing;
	}
}
