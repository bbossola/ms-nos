package com.workshare.msnos.usvc;

import com.workshare.msnos.core.*;
import com.workshare.msnos.core.payloads.FltPayload;
import com.workshare.msnos.core.payloads.QnePayload;
import com.workshare.msnos.core.protocols.ip.Network;
import com.workshare.msnos.core.protocols.ip.udp.UDPGateway;
import com.workshare.msnos.soup.time.SystemTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public class MicroserviceTest {

    Cloud cloud;
    Microservice otherMs;
    UDPGateway gate1;
    Microservice localMicroservice;

    @Before
    public void prepare() throws Exception {
        cloud = Mockito.mock(Cloud.class);
        gate1 = Mockito.mock(UDPGateway.class);

        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());
        Mockito.when(cloud.getLocalAgents()).thenReturn(new HashSet<LocalAgent>(Arrays.asList(remoteAgent)));

        Iden iden = new Iden(Iden.Type.CLD, new UUID(111, 111));

        Mockito.when(cloud.getIden()).thenReturn(iden);

        otherMs = new Microservice("kiki");

        localMicroservice = getLocalMicroservice();

        fakeSystemTime(12345L);
    }

    @After
    public void after() {
        SystemTime.reset();
    }

    @Test
    public void shouldInternalAgentJoinTheCloudOnJoin() throws Exception {
        localMicroservice = new Microservice("jeff");
        cloud = new Cloud(UUID.randomUUID(), Collections.<Gateway>emptySet());

        localMicroservice.join(cloud);

        assertEquals(localMicroservice.getAgent(), cloud.getLocalAgents().iterator().next());
    }

    @Test
    public void shouldSendQNEwhenPublishApi() throws Exception {
        RestApi api = new RestApi("/foo", 8080);
        localMicroservice.publish(api);

        Message msg = getLastMessageSent();
        assertEquals(Message.Type.QNE, msg.getType());
        assertEquals(cloud.getIden(), msg.getTo());

        Set<RestApi> apis = ((QnePayload) msg.getData()).getApis();
        assertTrue(api.equals(apis.iterator().next()));
    }

    @Test
    public void shouldSendENQonJoin() throws Exception {
        Message msg = getLastMessageSent();

        assertEquals(Message.Type.ENQ, msg.getType());
        assertEquals(cloud.getIden(), msg.getTo());
    }

    @Test
    public void shouldProcessQNEMsgs() throws Exception {
        simulateMessageFromCloud(getQNEMessage(otherMs, localMicroservice.getAgent()));

        assertTrue(iterateMicroServiceListGetByName(localMicroservice, otherMs));
    }

    @Test
    public void shouldBeRemovedWhenUnderlyingAgentDies() throws Exception {
        Microservice remoteMicroservice = new Microservice("remote");

        putRemoteAgentInCloudAgentsList(remoteMicroservice.getAgent());

        simulateMessageFromCloud(getQNEMessage(remoteMicroservice, localMicroservice.getAgent()));
        assertTrue(iterateMicroServiceListGetByName(localMicroservice, remoteMicroservice));

        simulateMessageFromCloud(getFaultMessage(remoteMicroservice.getAgent()));

        assertFalse(iterateMicroServiceListGetByName(localMicroservice, remoteMicroservice));
    }

    @Test
    public void shouldSendQNEOnEnquiry() throws Exception {
        Microservice remoteMicroservice = new Microservice("remote");

        simulateMessageFromCloud(getENQMessage(remoteMicroservice.getAgent(), localMicroservice.getAgent()));

        Message msg = getLastMessageSent();

        assertEquals(Message.Type.QNE, msg.getType());
        assertEquals(cloud.getIden(), msg.getTo());
    }

    @Test
    public void shouldNotProcessMessagesFromSelf() throws Exception {
        simulateMessageFromCloud(getENQMessage(localMicroservice.getAgent(), localMicroservice.getAgent()));

        Message msg = getLastMessageSent();

        assertEquals(Message.Type.ENQ, msg.getType());
    }

    @Test
    public void shouldReturnCorrectRestApi() throws Exception {
        String endpoint = "/files";

        setupRemoteMicroservice("content", endpoint);
        setupRemoteMicroservice("content", endpoint);
        setupRemoteMicroservice("peoples", "/users");
        setupRemoteMicroservice("content", "/folders");

        RestApi result = localMicroservice.searchApi("content", endpoint);

        assertFalse(result.getPath().equals("/folders"));
        assertTrue(endpoint.equals(result.getPath()));
    }

    @Test
    public void shouldBeAbleToMarkRestApiAsFaulty() throws Exception {
        setupRemoteMicroservice("content", "/files");
        setupRemoteMicroservice("content", "/files");
        setupRemoteMicroservice("peoples", "/users");
        setupRemoteMicroservice("content", "/folders");

        RestApi result1 = localMicroservice.searchApi("content", "/files");
        result1.markAsFaulty();

        RestApi result2 = localMicroservice.searchApi("content", "/files");
        assertFalse(result2.equals(result1));
    }

    @Test
    public void shouldReturnNullWhenNoWorkingMicroserviceAvailable() throws Exception {
        setupRemoteMicroservice("content", "/files");
        setupRemoteMicroservice("content", "/files");
        setupRemoteMicroservice("peoples", "/users");
        setupRemoteMicroservice("content", "/folders");

        RestApi result1 = localMicroservice.searchApi("peoples", "/users");
        result1.markAsFaulty();

        RestApi result2 = localMicroservice.searchApi("peoples", "/users");

        assertNull(result2);
    }

    @Test
    public void shouldCreateRemoteMicroserviceOnQNE() throws IOException {
        LocalAgent remoteAgent = new LocalAgent(UUID.randomUUID());

        putRemoteAgentInCloudAgentsList(remoteAgent);

        simulateMessageFromCloud(newQNEMessage(remoteAgent.getIden()));

        assertAgentInMicroserviceList(remoteAgent);
    }

    @Test
    public void shouldCreateBoundRestApisWhenRestApiNotBound() throws IOException {
        RemoteAgent remoteAgent = getRemoteAgentWithFakeHosts();
        Mockito.when(cloud.getRemoteAgents()).thenReturn(new HashSet<RemoteAgent>(Arrays.asList(remoteAgent)));

        RestApi unboundApi = new RestApi("/files", 9999);
        simulateMessageFromCloud(newQNEMessage(remoteAgent.getIden(), unboundApi));

        RestApi api = getRestApi();
        assertEquals(api.getHost(), "10.10.10.10/15");
    }

    @Test
    public void shouldFollowPreciseAlgorithmWhenRestApiMarkedAsFaulty() throws Exception {
        setupRemoteMicroserviceWithMultipleRestAPIs("25.25.25.25", "15.15.10.1", "content", "/files");
        setupRemoteMicroserviceWithHost("10.10.10.10", "content", "/files");

        RestApi result1 = localMicroservice.searchApi("content", "/files");
        result1.markAsFaulty();

        RestApi result2 = localMicroservice.searchApi("content", "/files");
        assertEquals("10.10.10.10", result2.getHost());

        RestApi result3 = localMicroservice.searchApi("content", "/files");
        assertEquals("15.15.10.1", result3.getHost());
    }

    private RemoteAgent getRemoteAgentWithFakeHosts() {
        return new RemoteAgent(UUID.randomUUID()).withHosts(new HashSet<Network>(Arrays.asList(new Network(new byte[]{10, 10, 10, 10}, (short) 15))));
    }

    private void putRemoteAgentInCloudAgentsList(Agent agent) {
        RemoteAgent remote = new RemoteAgent(agent.getIden(), cloud);
        Mockito.when(cloud.getRemoteAgents()).thenReturn(new HashSet<RemoteAgent>(Arrays.asList(remote)));
    }

    private RestApi getRestApi() {
        RemoteMicroservice remote = localMicroservice.getMicroServices().get(0);
        Set<RestApi> apis = remote.getApis();
        return apis.iterator().next();
    }

    private void assertAgentInMicroserviceList(Agent remoteAgent) {
        for (RemoteMicroservice remote : localMicroservice.getMicroServices()) {
            assertEquals(remote.getAgent(), remoteAgent);
        }
    }

    private Message newQNEMessage(Iden iden, RestApi... apis) {
        return new Message(Message.Type.QNE, iden, cloud.getIden(), 2, false, new QnePayload("content", apis));
    }

    private Microservice getLocalMicroservice() throws IOException {
        Microservice uService1 = new Microservice("fluffy");
        uService1.join(cloud);
        return uService1;
    }

    private boolean iterateMicroServiceListGetByName(Microservice ms1, Microservice ms2) {
        for (RemoteMicroservice remote : ms1.getMicroServices()) {
            if (remote.getName().equals(ms2.getName())) return true;
        }
        return false;
    }

    private RemoteMicroservice setupRemoteMicroserviceWithHost(String host, String name, String endpoint) {
        RemoteAgent agent = new RemoteAgent(UUID.randomUUID());
        RestApi restApi = new RestApi(endpoint, 9999).host(host);
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        simulateMessageFromCloud(new Message(Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden(), 2, false, new QnePayload(name, restApi)));
        return remote;
    }

    private RemoteMicroservice setupRemoteMicroserviceWithMultipleRestAPIs(String host1, String host2, String name, String endpoint) throws IOException {
        RemoteAgent agent = new RemoteAgent(UUID.randomUUID());
        RestApi restApi = new RestApi(endpoint, 9999).host(host1);
        RestApi restApi2 = new RestApi(endpoint, 9999).host(host2);
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi, restApi2));
        simulateMessageFromCloud(new Message(Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden(), 2, false, new QnePayload(name, restApi, restApi2)));
        return remote;
    }

    private RemoteMicroservice setupRemoteMicroservice(String name, String endpoint) throws IOException {
        RemoteAgent agent = new RemoteAgent(UUID.randomUUID());
        RestApi restApi = new RestApi(endpoint, 9999).host("10.10.10.10");
        RemoteMicroservice remote = new RemoteMicroservice(name, agent, toSet(restApi));
        simulateMessageFromCloud(new Message(Message.Type.QNE, remote.getAgent().getIden(), cloud.getIden(), 2, false, new QnePayload(name, restApi)));
        return remote;
    }

    private Set<RestApi> toSet(RestApi... restApi) {
        return new HashSet<RestApi>(Arrays.asList(restApi));
    }

    private Message getENQMessage(Identifiable from, Identifiable to) {
        return new Message(Message.Type.ENQ, from.getIden(), to.getIden(), 2, false, null);

    }

    private Message getFaultMessage(Agent agent) {
        return new Message(Message.Type.FLT, cloud.getIden(), cloud.getIden(), 2, false, new FltPayload(agent.getIden()));
    }

    private Message getLastMessageSent() throws IOException {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(cloud, atLeastOnce()).send(captor.capture());
        return captor.getValue();
    }

    private Message simulateMessageFromCloud(final Message message) {
        ArgumentCaptor<Cloud.Listener> cloudListener = ArgumentCaptor.forClass(Cloud.Listener.class);
        verify(cloud, atLeastOnce()).addListener(cloudListener.capture());
        cloudListener.getValue().onMessage(message);
        return message;
    }

    private Message getQNEMessage(Microservice from, Identifiable to) {
        return new Message(
                Message.Type.QNE,
                from.getAgent().getIden(), to.getIden(), 2, false,
                new QnePayload(from.getName(), new RestApi("/" + from.getName(), 222).host("10.10.3.2"))
        );
    }

    private void fakeSystemTime(final long time) {
        SystemTime.setTimeSource(new SystemTime.TimeSource() {
            public long millis() {
                return time;
            }
        });
    }
}
