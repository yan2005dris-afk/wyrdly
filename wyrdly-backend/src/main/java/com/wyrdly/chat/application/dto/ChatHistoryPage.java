package com.wyrdly.chat.application.dto;

import java.util.List;

public record ChatHistoryPage(List<MessageResponse> data, ChatHistoryMeta meta) {

  public List<MessageResponse> getData() {
    return data;
  }

  public ChatHistoryMeta getMeta() {
    return meta;
  }

  public record ChatHistoryMeta(long total, int page, int pageSize, long totalPages) {
    public ChatHistoryMeta(long total, int page, int pageSize) {
      this(total, page, pageSize, (total + pageSize - 1) / (pageSize > 0 ? pageSize : 1));
    }

    public long getTotal() {
      return total;
    }

    public int getPage() {
      return page;
    }

    public int getPageSize() {
      return pageSize;
    }

    public long totalPages() {
      return totalPages;
    }

    public long getTotalPages() {
      return totalPages;
    }
  }
}
