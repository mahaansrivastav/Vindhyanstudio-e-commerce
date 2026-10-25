package com.vindhyanstudio.notify;

public record Message(Channel channel, String to, String subject, String body, Long orderId) {

    public enum Channel { EMAIL, SMS }
}
