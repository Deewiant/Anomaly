// File created: 2007-11-22 18:59:30

package deewiant.propulsion.engines;

import deewiant.common.Global;
import deewiant.propulsion.engines.model.Engine;

public final class Rammer extends Engine {
	public Rammer() { super("Ramming"); }
	public void move() { if (Global.target != null) super.goTo(Global.target); }
}
