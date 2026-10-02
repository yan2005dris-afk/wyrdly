package com.wyrdly.chat.application.dto;

public class SendMessageRequest {
  public String recipientId;
  public String content;

  public SendMessageRequest() {}

  public SendMessageRequest(String recipientId, String content) {
    this.recipientId = recipientId;
    this.content = content;
  }
}
