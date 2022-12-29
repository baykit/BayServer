package baykit.bayserver.tour;


import baykit.bayserver.BayLog;
import baykit.bayserver.BayServer;
import baykit.bayserver.Sink;
import baykit.bayserver.HttpException;
import baykit.bayserver.docker.base.InboundShip;
import baykit.bayserver.docker.City;
import baykit.bayserver.docker.Club;
import baykit.bayserver.docker.Town;
import baykit.bayserver.util.*;

/**
 * Tour holds request information
 */
public class Tour implements Reusable {

    public enum TourState {
        UNINITIALIZED,
        PREPARING,
        RUNNING,
        ABORTED,
        ENDED,
        ZOMBIE,
    }

    public static final int TOUR_ID_NOCHECK = -1;
    public static final int INVALID_TOUR_ID = 0;

    public InboundShip ship;
    public int shipId;
    public final int objectId; // object id
    static final Counter oidCounter = new Counter();
    static final Counter idCounter = new Counter();

    public int tourId; // tour id
    public boolean errorHandling;
    public Town town;
    public City city;
    public Club club;

    public TourReq req = new TourReq(this);
    public TourRes res = new TourRes(this);

    public int interval;
    public boolean isSecure;
    public TourState state = TourState.UNINITIALIZED;

    public HttpException error;

    public Tour() {
        this.objectId = oidCounter.next();
    }

    public void init(int key, InboundShip sip) {
        if(isInitialized())
            throw new Sink(ship + " Tour already initialized: " + this);

        this.ship = sip;
        this.shipId = sip.id();
        this.tourId = idCounter.next();
        this.req.key = key;

        req.init(key);
        res.init();

        changeState(Tour.TOUR_ID_NOCHECK, TourState.PREPARING);
        BayLog.debug(this + " initialized");
    }

    public void reset() {
        req.reset();
        res.reset();
        city = null;
        town = null;
        club = null;
        errorHandling = false;

        tourId = INVALID_TOUR_ID;

        interval = 0;
        isSecure = false;
        //BayLog.trace("%s reset running false", this);
        changeState(Tour.TOUR_ID_NOCHECK, TourState.UNINITIALIZED);
        error = null;

        ship = null;
    }

    public int id() {
        return tourId;
    }


    public String toString() {
        return ship + " tour#" + tourId + "/" + objectId + "[key=" + req.key + "]";
    }

    public void go() throws HttpException {

        city = ship.portDocker().findCity(req.reqHost);
        if(city == null)
            city = BayServer.findCity(req.reqHost);

        changeState(Tour.TOUR_ID_NOCHECK, TourState.RUNNING);

        BayLog.debug("%s GO TOUR! ...( ^_^)/: url=%s", this, req.uri);

        if (city == null) {
            throw new HttpException(HttpStatus.NOT_FOUND, req.uri);
        }
        else {
            try {
                city.enter(this);
            }
            catch(HttpException e) {
                throw e;
            }
            catch(Exception e) {
                BayLog.error(e);
                throw new HttpException(HttpStatus.INTERNAL_SERVER_ERROR, e.toString());
            }
        }
    }

    public boolean isValid() {
        return state == TourState.PREPARING || state == TourState.RUNNING;
    }

    public boolean isPreparing() {
        return state == TourState.PREPARING;
    }

    public boolean isRunning() {
        return state == TourState.RUNNING;
    }

    public boolean isAborted() {
        return state == TourState.ABORTED;
    }

    public boolean isZombie() {
        return state == TourState.ZOMBIE;
    }

    public boolean isEnded() {
        return state == TourState.ENDED;
    }

    public boolean isInitialized() {
        return state != TourState.UNINITIALIZED;
    }

    public void changeState(int checkId, TourState newState) {
        BayLog.trace("%s change state: %s", this, newState);
        checkTourId(checkId);
        this.state = newState;
    }


    public void checkTourId(int checkId) {
        if(checkId == TOUR_ID_NOCHECK)
            return;

        if(!isInitialized()) {
            throw new Sink("%s Tour not initialized", this);
        }
        if(checkId != this.tourId) {
            throw new Sink("%s Invalid tour id : %d", this, tourId);
        }
    }

}

