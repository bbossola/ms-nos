package com.workshare.msnos.core;

import com.workshare.msnos.core.payloads.Presence;

import java.util.Map;

class Messages {

    public static Message presence(Identifiable from, Identifiable to) {
        return new Message(Message.Type.PRS, from.getIden(), to.getIden(), 2, false, new Presence(true));
    }

    public static Message absence(Identifiable from, Identifiable to) {
        return new Message(Message.Type.PRS, from.getIden(), to.getIden(), 2, false, new Presence(false));
    }

    public static Message app(Identifiable from, Identifiable to, Map<String, Object> data) {
        return new Message(Message.Type.APP, from.getIden(), to.getIden(), 2, false, null);
    }

    public static Message discovery(Identifiable from, Identifiable to) {
        return new Message(Message.Type.DSC, from.getIden(), to.getIden(), 2, false, null);
    }

    public static Message ping(Identifiable from, Identifiable to) {
        return new Message(Message.Type.PIN, from.getIden(), to.getIden(), 2, false, null);
    }

    public static Message pong(Identifiable from, Identifiable to) {
        return new Message(Message.Type.PON, from.getIden(), to.getIden(), 2, false, null);
    }

    public static Message enquiry(Identifiable from, Identifiable to) {
        return new Message(Message.Type.ENQ, from.getIden(), to.getIden(), 2, false, null);
    }
}
