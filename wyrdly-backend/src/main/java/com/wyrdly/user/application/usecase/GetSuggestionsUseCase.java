package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.GraphSuggestionsResponse;

public interface GetSuggestionsUseCase {
  GraphSuggestionsResponse getSuggestions(String userId, int page, int pageSize);
}
