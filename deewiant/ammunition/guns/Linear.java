// File created: 2007-11-22 18:48:28

package deewiant.ammunition.guns;

import deewiant.ammunition.guns.common.Circulinear;
import deewiant.ammunition.guns.model.Gun;

public final class Linear extends Gun {
	public Linear() { super("Linear"); }

	public final void setSights() {
		super.targetAngle = Circulinear.calculate(firePower, false);
	}
}
