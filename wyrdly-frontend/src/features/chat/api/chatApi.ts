import { apiClient } from "../../../api/axios";
import type { ChatHistoryResponse } from "../types";

export const chatApi = {
  async getChatHistory(
    recipientId: string,
    page: number = 1,
    pageSize: number = 50,
  ): Promise<ChatHistoryResponse> {
    const response = await apiClient.get<ChatHistoryResponse>(
      `/api/chat/${recipientId}/history`,
      {
        params: { page, pageSize },
      },
    );
    return response.data;
  },
};
