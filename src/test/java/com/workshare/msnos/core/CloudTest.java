package com.workshare.msnos.core;

import com.workshare.msnos.core.Cloud.Multicaster;
import com.workshare.msnos.core.Gateway.Listener;
import com.workshare.msnos.core.Message.Status;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.Presence;
import com.workshare.msnos.core.protocols.ip.udp.UDPGateway;
import com.workshare.msnos.soup.json.Json;
import com.workshare.msnos.soup.time.SystemTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.workshare.msnos.core.Message.Type.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public class CloudTest {

    private static final Iden SOMEONE = new Iden(Iden.Type.AGT, UUID.randomUUID());
    private static final Iden SOMEONELSE = new Iden(Iden.Type.AGT, UUID.randomUUID());
    private static final Iden MY_CLOUD = new Iden(Iden.Type.CLD, UUID.randomUUID());

    private Gateway gate1;
    private Gateway gate2;

    private Cloud thisCloud;
    private Cloud otherCloud;

    private ScheduledExecutorService scheduler;
    private List<Message> messages;

    @Before
    public void init() throws Exception {
        scheduler = mock(ScheduledExecutorService.class);

        Receipt unknownReceipt = mock(Receipt.class);
        when(unknownReceipt.getStatus()).thenReturn(Status.UNKNOWN);

        gate1 = mock(Gateway.class);
        when(gate1.send(any(Message.class))).thenReturn(unknownReceipt);
        gate2 = mock(Gateway.class);
        when(gate2.send(any(Message.class))).thenReturn(unknownReceipt);

        thisCloud = new Cloud(MY_CLOUD.getUUID(), new HashSet<Gateway>(Arrays.asList(gate1, gate2)), synchronousMulticaster(), scheduler);
        thisCloud.addListener(new Cloud.Listener() {
            @Override
            public void onMessage(Message message) {
                messages.add(message);
            }
        });

        messages = new ArrayList<Message>();

        otherCloud = new Cloud(UUID.randomUUID(), Collections.<Gateway>emptySet());
    }

    @After
    public void after() throws Exception {
        SystemTime.reset();
        scheduler.shutdown();
    }

    @Test
    public void shouldCreateDefaultGateways() throws Exception {
        thisCloud = new Cloud(UUID.randomUUID());

        Set<Gateway> gates = thisCloud.getGateways();

        assertEquals(1, gates.size());
        assertEquals(UDPGateway.class, gates.iterator().next().getClass());
    }

    @Test
    public void shouldSendPresenceMessageWhenAgentJoins() throws Exception {
        LocalAgent smith = new LocalAgent(UUID.randomUUID());

        smith.join(thisCloud);

        List<Message> messageList = getAllMessagesSent();
        assertTrue(!messageList.isEmpty());
        Message message = messageList.get(0);
        assertNotNull(message);
        assertEquals(Message.Type.PRS, message.getType());
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(thisCloud.getIden(), message.getTo());
    }

    @Test
    public void shouldSendDiscoveryMessageWhenAgentJoins() throws Exception {
        LocalAgent smith = new LocalAgent(UUID.randomUUID());

        smith.join(thisCloud);

        List<Message> messageList = getAllMessagesSent();
        Message message = messageList.get(1);
        assertNotNull(message);
        assertEquals(Message.Type.DSC, message.getType());
        assertEquals(smith.getIden(), message.getFrom());
        assertEquals(thisCloud.getIden(), message.getTo());
    }

    @Test
    public void shouldUpdateAgentsListWhenAgentJoins() throws Exception {
        LocalAgent smith = new LocalAgent(UUID.randomUUID());

        smith.join(thisCloud);

        assertTrue(thisCloud.getLocalAgents().contains(smith));
    }

    @Test
    public void shouldUpdateAgentsListWhenAgentPongs() throws Exception {
        UUID uuid = UUID.randomUUID();
        RemoteAgent smithRemote = new RemoteAgent(uuid);
        LocalAgent smith = new LocalAgent(uuid);

        simulateMessageFromNetwork(Messages.pong(smith, thisCloud));

        assertTrue(thisCloud.getRemoteAgents().contains(smithRemote));
    }

    @Test
    public void shouldRemoveAgentFromAgentsOnLeave() throws Exception {
        LocalAgent jeff = new LocalAgent(UUID.randomUUID());

        jeff.join(thisCloud);

        assertTrue(thisCloud.getLocalAgents().contains(jeff));

        jeff.leave(thisCloud);

        simulateAgentLeavingCloud(jeff, thisCloud);

        assertFalse(thisCloud.getLocalAgents().contains(jeff));
    }

    @Test
    public void shouldSendAbsenceWhenLeavingCloud() throws Exception {
        Presence data = new Presence(false);

        LocalAgent karl = new LocalAgent(UUID.randomUUID());

        karl.leave(thisCloud);

        Message message = getLastMessageSentToNetwork();

        assertNotNull(message);
        assertEquals(PRS, message.getType());
        assertEquals(karl.getIden(), message.getFrom());
        assertEquals(Json.toJsonString(data), Json.toJsonString(message.getData()));
    }

    @Test
    public void shouldNOTUpdateAgentsListWhenAgentJoinsTroughGatewayToAnotherCloud() throws Exception {
        LocalAgent frank = new LocalAgent(UUID.randomUUID());

        simulateAgentJoiningCloud(frank, otherCloud);

        assertFalse(thisCloud.getLocalAgents().contains(frank));
    }

    @Test
    public void shouldForwardAnyNonCoreMessageSentToThisCloud() throws Exception {
        simulateMessageFromNetwork(newMessage(APP, SOMEONE, thisCloud.getIden()));
        assertEquals(1, messages.size());
    }

    @Test
    public void shouldForwardAnyNonCoreMessageSentToAnAgentOfTheCloud() throws Exception {
        LocalAgent smith = new LocalAgent(UUID.randomUUID());
        smith.join(thisCloud);

        simulateMessageFromNetwork(newMessage(APP, SOMEONE, smith.getIden()));
        assertEquals(1, messages.size());
    }

    @Test
    public void shouldNOTForwardAnyNonCoreMessageSentToAnAgentOfAnotherCloud() throws Exception {
        LocalAgent smith = new LocalAgent(UUID.randomUUID());
        smith.join(otherCloud);

        simulateMessageFromNetwork(newMessage(APP, SOMEONE, smith.getIden()));
        assertEquals(0, messages.size());
    }

    @Test
    public void shouldForwardAnyNonCoreMessageSentToAnotherCloud() throws Exception {

        simulateMessageFromNetwork(newMessage(APP, SOMEONE, otherCloud.getIden()));
        assertEquals(0, messages.size());
    }

    @Test
    public void shouldSendMessagesTroughGateways() throws Exception {
        Message message = newMessage(APP, SOMEONE, thisCloud.getIden());
        thisCloud.send(message);
        verify(gate1).send(message);
        verify(gate2).send(message);
    }

    @Test
    public void shouldSendMessagesReturnUnknownStatusWhenUnreliable() throws Exception {
        Message message = newMessage(APP, SOMEONE, thisCloud.getIden());
        Receipt res = thisCloud.send(message);
        assertEquals(Status.UNKNOWN, res.getStatus());
    }

    @Test
    public void shouldSendMessagesReturnMultipleStatusWhenReliable() throws Exception {
        Receipt value1 = createMockFuture(Status.UNKNOWN);
        when(gate1.send(any(Message.class))).thenReturn(value1);

        Receipt value2 = createMockFuture(Status.UNKNOWN);
        when(gate2.send(any(Message.class))).thenReturn(value2);

        Message message = newReliableMessage(APP, SOMEONE, SOMEONELSE);
        Receipt res = thisCloud.send(message);
        assertEquals(MultiGatewayReceipt.class, res.getClass());

        MultiGatewayReceipt multi = (MultiGatewayReceipt) res;
        assertTrue(multi.getReceipts().contains(value1));
        assertTrue(multi.getReceipts().contains(value2));
    }

    @Test
    public void shouldUpdateRemoteAgentAccessTimeOnPresenceReceived() throws Exception {
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());

        fakeSystemTime(12345L);
        simulateMessageFromNetwork(Messages.presence(remoteAgent, thisCloud));

        fakeSystemTime(99999L);
        assertEquals(12345L, getRemoteAgentAccessTime(thisCloud, remoteAgent));
    }

    @Test
    public void shouldUpdateRemoteAgentAccessTimeOnMessageReceived() throws Exception {
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());
        simulateAgentJoiningCloud(remoteAgent, thisCloud);

        fakeSystemTime(12345L);
        simulateMessageFromNetwork(Messages.ping(remoteAgent, thisCloud));

        fakeSystemTime(99999L);
        assertEquals(12345L, getRemoteAgentAccessTime(thisCloud, remoteAgent));
    }

    @Test
    public void shouldPingAgentsWhenAccessTimeIsTooOld() throws Exception {
        fakeSystemTime(12345L);
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());
        simulateAgentJoiningCloud(remoteAgent, thisCloud);

        fakeSystemTime(99999L);
        forceRunCloudPeriodicCheck();

        Message pingExpected = getLastMessageSentToNetwork();
        assertNotNull(pingExpected);
        assertEquals(PIN, pingExpected.getType());
        assertEquals(thisCloud.getIden(), pingExpected.getFrom());
    }

    @Test
    public void shouldRemoveAgentsThatDoNOTRespondToPing() {
        fakeSystemTime(12345L);
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());
        simulateAgentJoiningCloud(remoteAgent, thisCloud);

        fakeSystemTime(999999L);
        forceRunCloudPeriodicCheck();

        assertTrue(!thisCloud.getLocalAgents().contains(remoteAgent));
    }

    @Test
    public void shouldSendFaultWhenAgentRemoved() throws Exception {
        fakeSystemTime(12345L);
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());
        simulateAgentJoiningCloud(remoteAgent, thisCloud);

        fakeSystemTime(99999999L);
        forceRunCloudPeriodicCheck();

        Message message = getLastMessageSentToCloudListeners();
        assertEquals(FLT, message.getType());
        assertEquals(thisCloud.getIden(), message.getFrom());
        assertEquals(remoteAgent.getIden(), ((FltPayload) message.getData()).getAbout());
    }

    @Test
    public void shouldStoreHostInfoWhenRemoteAgentJoins() throws Exception {
        LocalAgent frank = new LocalAgent(UUID.randomUUID());
        Presence presence = (Presence) simulateAgentJoiningCloud(frank, thisCloud).getData();

        RemoteAgent remoteFrank = getRemoteAgent(thisCloud, frank.getIden());

        assertEquals(presence.getNetworks(), remoteFrank.getHosts());
    }

    @Test
    public void shouldUpdateRemoteAgentsWhenARemoteJoins() throws Exception {
        LocalAgent frank = new LocalAgent(UUID.randomUUID());

        simulateAgentJoiningCloud(frank, thisCloud);

        assertEquals(frank.getIden(), thisCloud.getRemoteAgents().iterator().next().getIden());
    }

    private RemoteAgent getRemoteAgent(Cloud thisCloud, Iden iden) {
        for (RemoteAgent agent : thisCloud.getRemoteAgents()) {
            if (agent.getIden().equals(iden)) return agent;
        }
        return null;
    }

    private void forceRunCloudPeriodicCheck() {
        Runnable runnable = capturePeriodicRunableCheck();
        runnable.run();
    }

    private Runnable capturePeriodicRunableCheck() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).scheduleAtFixedRate(captor.capture(), anyInt(), anyInt(), any(TimeUnit.class));
        return captor.getValue();
    }

    private long getRemoteAgentAccessTime(Cloud cloud, LocalAgent agent) {
        Collection<RemoteAgent> agents = cloud.getRemoteAgents();
        for (RemoteAgent a : agents) {
            if (a.getIden().equals(agent.getIden()))
                return a.getAccessTime();
        }

        throw new RuntimeException("Agent " + agent + " not found!");
    }

    private Receipt createMockFuture(final Status status) throws InterruptedException, ExecutionException {
        Receipt value = Mockito.mock(Receipt.class);
        when(value.getStatus()).thenReturn(status);
        return value;
    }

    private Message simulateAgentJoiningCloud(LocalAgent agent, Cloud cloud) {
        Message message = (Messages.presence(agent, cloud));
        simulateMessageFromNetwork(message);
        return message;
    }

    private void simulateAgentLeavingCloud(LocalAgent agent, Cloud cloud) {
        simulateMessageFromNetwork(new Message(PRS, agent.getIden(), cloud.getIden(), 2, false, new Presence(false)));
    }

    private void simulateMessageFromNetwork(final Message message) {
        ArgumentCaptor<Listener> gateListener = ArgumentCaptor.forClass(Listener.class);
        verify(gate1).addListener(gateListener.capture());
        gateListener.getValue().onMessage(message);
    }

    private Message getLastMessageSentToCloudListeners() {
        return messages.get(messages.size() - 1);
    }

    private Message getLastMessageSentToNetwork() throws IOException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(gate1).send(captor.capture());
        return captor.getValue();
    }

    private List<Message> getAllMessagesSent() throws IOException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(gate1, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private Message newMessage(final Message.Type type, final Iden idenFrom, final Iden idenTo) {
        return new Message(type, idenFrom, idenTo, 1, false, null);
    }

    private Message newReliableMessage(final Message.Type type, final Iden idenFrom, final Iden idenTo) {
        return new Message(type, idenFrom, idenTo, 1, true, null);
    }

    private Multicaster synchronousMulticaster() {
        return new Cloud.Multicaster(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
    }

    private void fakeSystemTime(final long time) {
        SystemTime.setTimeSource(new SystemTime.TimeSource() {
            public long millis() {
                return time;
            }
        });
    }
}
