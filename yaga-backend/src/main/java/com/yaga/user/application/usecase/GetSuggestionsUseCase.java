package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.GraphSuggestionsResponse;

public interface GetSuggestionsUseCase {
  GraphSuggestionsResponse getSuggestions(String userId, int page, int pageSize);
}
