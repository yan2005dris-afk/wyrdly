package com.wyrdly.chat.application.dto;

public class MessageRequest {
  private String action;
  private String content;
  private String recipientId;

  public MessageRequest() {}

  public MessageRequest(String action, String content, String recipientId) {
    this.action = action;
    this.content = content;
    this.recipientId = recipientId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getRecipientId() {
    return recipientId;
  }

  public void setRecipientId(String recipientId) {
    this.recipientId = recipientId;
  }
}
