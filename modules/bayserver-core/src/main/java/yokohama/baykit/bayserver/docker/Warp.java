package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.ship.Ship;

public interface Warp extends Club {

    String host();
    int port();
    String warpBase();
    int timeoutSec();
    void onEndTour(Ship warpShip);
    void onEndShip(Ship warpShip);
}
