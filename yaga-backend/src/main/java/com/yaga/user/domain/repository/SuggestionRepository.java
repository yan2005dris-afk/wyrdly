package com.yaga.user.domain.repository;

import com.yaga.user.domain.model.GraphSuggestion;
import java.util.List;

public interface SuggestionRepository {

  List<GraphSuggestion> findSuggestions(String userId, int page, int pageSize);

  long countSuggestions(String userId);
}
