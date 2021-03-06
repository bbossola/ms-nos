package com.workshare.msnos.core.routing;

import static com.workshare.msnos.core.CoreHelper.asSet;
import static com.workshare.msnos.core.CoreHelper.newAgentIden;
import static com.workshare.msnos.core.GatewaysHelper.newHttpGateway;
import static com.workshare.msnos.core.GatewaysHelper.newUDPGateway;
import static com.workshare.msnos.core.GatewaysHelper.newWWWGateway;
import static com.workshare.msnos.core.MessagesHelper.newPingMessage;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;

import org.junit.Test;

import com.workshare.msnos.core.Cloud;
import com.workshare.msnos.core.Gateway;
import com.workshare.msnos.core.Identifiable;
import com.workshare.msnos.core.Message;
import com.workshare.msnos.core.Receipt;
import com.workshare.msnos.core.protocols.ip.http.HttpGateway;
import com.workshare.msnos.core.protocols.ip.udp.UDPGateway;
import com.workshare.msnos.core.protocols.ip.www.WWWGateway;

public class RouterBasicTest {

    @Test
    public void shouldPickuppGateways() throws Exception {
        HttpGateway http = newHttpGateway();
        UDPGateway udp = newUDPGateway();
        WWWGateway www = newWWWGateway();
        Set<Gateway> gates = asSet(http, udp, www);
    
        Router router = new Router(mock(Cloud.class), gates);
        
        assertEquals(udp, router.udpGateway());
        assertEquals(http, router.httpGateway());
        assertEquals(www, router.wwwGateway());
    }
    
    @Test
    public void shouldReturnFailedWhenGatwayBooms() throws Exception {
        UDPGateway udp = newFaiingMockUDPGateway();
        Router router = new Router(mock(Cloud.class), udp, null, null);

        Message message = newPingMessage(newAgentIden());
        Receipt receipt = router.route(message);
        
        assertEquals(Message.Status.FAILED, receipt.getStatus());
    }

    @Test
    public void shouldReturnFailedWhenNoRoutes() throws Exception {
        Router router = new Router(mock(Cloud.class), null, null, null, new Route[]{});

        Message message = newPingMessage(newAgentIden());
        Receipt receipt = router.route(message);
        
        assertEquals(Message.Status.FAILED, receipt.getStatus());
    }

    @Test
    public void shouldNotSendViaUDPReturnFailedWhenGatwayBooms() throws Exception {
        HttpGateway http = newHttpGateway();
        UDPGateway udp = newMockUDPGateway();
        WWWGateway www = newWWWGateway();
        Router router = new Router(mock(Cloud.class), asSet(http, udp, www));

        Message message = newPingMessage(newAgentIden()).fromGate(udp.name());
        router.sendViaUDP(message, 10, "HOW");

        verify(udp,never()).send(any(Cloud.class), any(Message.class), any(Identifiable.class));
    }

    private UDPGateway newFaiingMockUDPGateway() throws IOException {
        UDPGateway gate = newMockUDPGateway();
        when(gate.send(any(Cloud.class), any(Message.class), any(Identifiable.class))).thenThrow(new IOException("boom!"));
        return gate;
    }

    private UDPGateway newMockUDPGateway() {
        UDPGateway gate = mock(UDPGateway.class);
        when(gate.name()).thenReturn("UDP");
        return gate;
    }




}
