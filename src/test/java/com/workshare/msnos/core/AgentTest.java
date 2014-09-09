package com.workshare.msnos.core;

import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.protocols.ip.AddressResolver;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.soup.time.SystemTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.workshare.msnos.core.Message.Type.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class AgentTest {

    private Cloud cloud;
    private LocalAgent karl;
    private LocalAgent smith;

    @Before
    public void before() throws Exception {
        System.setProperty("public.ip", "132.1.0.2");
        cloud = mock(Cloud.class);
        when(cloud.getIden()).thenReturn(new Iden(Iden.Type.CLD, UUID.randomUUID()));
        when(cloud.generateNextMessageUUID()).thenReturn(UUID.randomUUID());

        karl = new LocalAgent(UUID.randomUUID());
        karl.join(cloud);

        smith = new LocalAgent(UUID.randomUUID());
        smith.join(cloud);
    }

    @After
    public void after() throws Exception {
        SystemTime.reset();
    }

    @Test
    public void agentShouldAttachListenerToCloud() {
        verify(cloud, atLeastOnce()).addListener(any(Cloud.Listener.class));
    }

    @Test
    public void shouldSendPresenceWhenDiscoveryIsReceived() throws IOException {
        Message discovery = new MessageBuilder(Message.Type.DSC, cloud, smith).make();
        simulateMessageFromCloud(discovery);

        Message message = getLastMessageToCloud();

        assertNotNull(message);
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(PRS, message.getType());
    }

    @Test
    public void shouldSendPongWhenPingIsReceived() throws IOException {
        simulateMessageFromCloud(new MessageBuilder(Message.Type.PIN, cloud, smith).make());
        Message message = getLastMessageToCloud();

        assertNotNull(message);
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(PON, message.getType());
    }

    @Test
    public void shouldSendUnreliableMessageThroughCloud() throws Exception {
        smith.send(new MessageBuilder(Message.Type.PIN, smith, karl).sequence(12).make());

        Message message = getLastMessageToCloud();
        assertNotNull(message);
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(karl.getIden(), message.getTo());
        assertEquals(PIN, message.getType());
        assertEquals(false, message.isReliable());
    }

    @Test
    public void shouldSendReliableMessageThroughCloud() throws Exception {

        smith.send(new MessageBuilder(Message.Type.PIN, smith, karl).sequence(12).make().reliable());

        Message message = getLastMessageToCloud();
        assertNotNull(message);
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(karl.getIden(), message.getTo());
        assertEquals(PIN, message.getType());
        assertEquals(true, message.isReliable());
    }

    @Test
    public void presenceMessageShouldContainNetworkInfo() throws Exception {
        smith.send(new MessageBuilder(Message.Type.PRS, smith, cloud).sequence(12).with(new Presence(true)).make());

        Message message = getLastMessageToCloud();

        assertNotNull(message);
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(PRS, message.getType());

        assertNotNull(((Presence) message.getData()).getNetworks());
        assertEquals(((Presence) message.getData()).getNetworks(), getNetworks());
    }

    @Test
    public void localAgentLastAccessTimeShouldAlwaysBeNow() throws Exception {
        fakeSystemTime(123456L);

        LocalAgent jeff = new LocalAgent(UUID.randomUUID());
        jeff.join(cloud);

        assertEquals(jeff.getAccessTime(), SystemTime.asMillis());
    }

    @Test
    public void shouldUpdateAccessTimeWhenMessageIsReceived() {
        fakeSystemTime(123456790L);

        Message message = new MessageBuilder(MessageBuilder.Mode.RELAXED, Message.Type.PIN, cloud.getIden(), smith.getIden()).sequence(12).make();
        simulateMessageFromCloud(message);

        assertEquals(123456790L, smith.getAccessTime());
    }

    @Test
    public void otherAgentsShouldNOTStillSeeAgentOnLeave() throws Exception {
        smith.leave();
        assertFalse(karl.getCloud().getLocalAgents().contains(smith));
    }

    @Test
    public void agentShouldStoreNetworkInformationAfterJoin() throws Exception {
        smith.join(cloud);
        assertEquals(new Presence(true).getNetworks(), smith.getHosts());
    }

    @Test
    public void shouldCreateSequenceNumberOnCreation() throws Exception {
        smith.send(new MessageBuilder(Message.Type.PIN, smith, cloud).make());
        Message toCloud = getLastMessageToCloud();
        assertEquals(Long.valueOf(smith.getSeq() - 1), Long.valueOf(toCloud.getSeq()));
    }

    private Message getLastMessageToCloud() throws IOException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(cloud, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private void simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        cloudListener.getValue().onMessage(message);
    }

    private void fakeSystemTime(final long time) {
        SystemTime.setTimeSource(new SystemTime.TimeSource() {
            public long millis() {
                return time;
            }
        });
    }

    private static Set<Network> getNetworks() throws SocketException {
        Set<Network> nets = new HashSet<Network>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                nets.addAll(Network.list(nic, true, new AddressResolver()));
            }
        } catch (SocketException e) {
            System.out.println("AgentTest.getNetworks" + e);
            throw e;
        }
        return nets;
    }
}
