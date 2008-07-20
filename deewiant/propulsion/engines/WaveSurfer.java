// File created: 2007-11-22 19:03:24

package deewiant.propulsion.engines;

import deewiant.propulsion.engines.model.Engine;
import deewiant.common.Enemy;

/*

as I currently see it:
	fire off a wave when the enemy shoots
	when hit, ++danger of the guess factor the enemy shot from
	when missed (i.e. wave front passes your position without getting hit), danger of the guess factor you chose -= 0.1 (he might shoot it next time, so not -= 1)
	when moving, pick the guess factor with the least danger

*/
public final class WaveSurfer extends Engine {
	public WaveSurfer() { super("Wave Surfing"); }

	// the WaveSurfing-specific stuff which needs to be remembered between rounds is updated here
	// the WaveSurfing-specific stuff which does not need to be remembered is updated along with everything else, in Anomaly.onScannedRobot()
	// i.e. dude.memory is updated here, dude is updated elsewhere
	public void onScannedRobot(final Enemy dude) {
//		dude.statify();
//		++dude.memory.scans;
	}

	public void move() {
	}
}
/*
class MovementWave extends Wave {
}

class Move {
}

class Hit {
}
*/
