package com.workshare.msnos.core;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.workshare.msnos.core.payloads.NullPayload;
import com.workshare.msnos.soup.time.SystemTime;

// FIXME: kill me and replace 
public class MockMessageHelper {

    private final Message message;
    private final Message.Type type;
    private final Iden from;
    private final Iden to;
    private final UUID uuid;

    private Message.Payload payload;
    private String sig;
    private String rnd;
    private long when;

    public MockMessageHelper(Message.Type type, Iden from, Iden to) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.uuid = new UUID(111, SystemTime.asMillis());
        message = mock(Message.class);
    }

    public MockMessageHelper data(Message.Payload payload) {
        this.payload = payload;
        return this;
    }

    public MockMessageHelper signed(String sig, String rnd) {
        this.sig = sig;
        this.rnd = rnd;
        return this;
    }

    public MockMessageHelper at(long when) {
        this.when = when;
        return this;
    }

    public Message make() {
        when(message.getSig()).thenReturn(sig);
        when(message.getUuid()).thenReturn(uuid);
        when(message.getRnd()).thenReturn(rnd);
        when(message.getWhen()).thenReturn(when);
        when(message.getType()).thenReturn(type);
        when(message.getFrom()).thenReturn(from);
        when(message.getTo()).thenReturn(to);
        if (payload == null) when(message.getData()).thenReturn(new NullPayload());
        else when(message.getData()).thenReturn(payload);
        return message;
    }
}
