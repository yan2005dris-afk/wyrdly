package com.wyrdly.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MessageRequest(
    @NotBlank(message = "Action is required") String action,
    @Size(max = 5000, message = "Content cannot exceed 5000 characters") String content,
    String recipientId) {

  public String getAction() {
    return action;
  }

  public String getContent() {
    return content;
  }

  public String getRecipientId() {
    return recipientId;
  }
}
