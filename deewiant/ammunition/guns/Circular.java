// File created: 2007-11-22 18:51:51

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.common.Circulinear;
import deewiant.ammunition.guns.model.Gun;

public final class Circular extends Gun {
	public Circular() { super("Circular"); }

	public final void setSights() {
		super.targetAngle = Circulinear.calculate(firePower, true);
	}
}
