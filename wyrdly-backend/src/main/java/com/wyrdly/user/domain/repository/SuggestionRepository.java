package com.wyrdly.user.domain.repository;

import com.wyrdly.user.domain.model.GraphSuggestion;
import java.util.List;

public interface SuggestionRepository {

  List<GraphSuggestion> findSuggestions(String userId, int page, int pageSize);

  long countSuggestions(String userId);
}
