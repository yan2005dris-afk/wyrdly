package com.yaga.chat.application.dto;

import java.util.List;

public class ChatHistoryPage {
  private List<MessageResponse> data;
  private ChatHistoryMeta meta;

  public ChatHistoryPage() {}

  public ChatHistoryPage(List<MessageResponse> data, ChatHistoryMeta meta) {
    this.data = data;
    this.meta = meta;
  }

  public List<MessageResponse> getData() {
    return data;
  }

  public void setData(List<MessageResponse> data) {
    this.data = data;
  }

  public ChatHistoryMeta getMeta() {
    return meta;
  }

  public void setMeta(ChatHistoryMeta meta) {
    this.meta = meta;
  }

  public static class ChatHistoryMeta {
    private long total;
    private int page;
    private int pageSize;
    private long totalPages;

    public ChatHistoryMeta() {}

    public ChatHistoryMeta(long total, int page, int pageSize) {
      this.total = total;
      this.page = page;
      this.pageSize = pageSize;
      this.totalPages = (total + pageSize - 1) / pageSize;
    }

    public long getTotal() {
      return total;
    }

    public void setTotal(long total) {
      this.total = total;
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getPageSize() {
      return pageSize;
    }

    public void setPageSize(int pageSize) {
      this.pageSize = pageSize;
    }

    public long getTotalPages() {
      return totalPages;
    }

    public void setTotalPages(long totalPages) {
      this.totalPages = totalPages;
    }
  }
}
