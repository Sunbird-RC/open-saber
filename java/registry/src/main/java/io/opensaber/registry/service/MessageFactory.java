package io.opensaber.registry.service;

public class MessageFactory {
    private static final MessageFactory instance = new MessageFactory();
    private MessageFactory(){}
    public static MessageFactory instance(){
        return instance;
    }


}
