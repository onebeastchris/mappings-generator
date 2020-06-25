package org.geysermc.resources.state.type;

import org.geysermc.resources.Pair;
import org.geysermc.resources.state.StateMapper;
import org.geysermc.resources.state.StateRemapper;

@StateRemapper(value = "facing", blockRegex = ".*wall_sign.?$")
public class WallSignFacingMapper extends StateMapper<Integer> {

    @Override
    public Pair<String, Integer> translateState(String fullIdentifier, String value) {
        int facing = 0;
        switch (value) {
            case "north":
                facing = 2;
                break;
            case "south":
                facing = 3;
                break;
            case "west":
                facing = 4;
                break;
            case "east":
                facing = 5;
                break;
        }
        return new Pair<>("facing_direction", facing);
    }
}
