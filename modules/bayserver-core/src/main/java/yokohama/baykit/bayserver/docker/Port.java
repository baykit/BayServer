package yokohama.baykit.bayserver.docker;

import yokohama.baykit.bayserver.HttpException;
import yokohama.baykit.bayserver.agent.transporter.DataListener;
import yokohama.baykit.bayserver.agent.transporter.SelectHandler;
import yokohama.baykit.bayserver.common.InboundShip;
import yokohama.baykit.bayserver.common.Rudder;
import yokohama.baykit.bayserver.protocol.ProtocolHandler;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;

public interface Port {

    String protocol();

    String host();

    int port();

    String socketPath();

    SocketAddress address() throws IOException;

    boolean anchored();

    boolean secure();

    int timeoutSec();

    void checkAdmitted(SocketChannel ch) throws HttpException;

    ArrayList<String[]> additionalHeaders();

    Collection<City> cities();

    City findCity(String name);

    DataListener newDataListener(int agentId, Rudder rd) throws IOException;

    SelectHandler newSelectHandler(int agentId, Rudder rd) throws IOException;

    void returnProtocolHandler(int agentId, ProtocolHandler protoHnd);

    void returnShip(InboundShip ship);

}
