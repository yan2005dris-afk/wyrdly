package com.wyrdly.user.application.service;

import com.wyrdly.user.application.dto.GraphSuggestionUserDto;
import com.wyrdly.user.application.dto.GraphSuggestionsResponse;
import com.wyrdly.user.application.usecase.GetSuggestionsUseCase;
import com.wyrdly.user.domain.repository.SuggestionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class SuggestionsService implements GetSuggestionsUseCase {

  private final SuggestionRepository suggestionRepository;

  @Inject
  public SuggestionsService(SuggestionRepository suggestionRepository) {
    this.suggestionRepository =
        Objects.requireNonNull(suggestionRepository, "suggestionRepository must not be null");
  }

  @Override
  public GraphSuggestionsResponse getSuggestions(String userId, int page, int pageSize) {
    var suggestions = suggestionRepository.findSuggestions(userId, page, pageSize);
    var total = suggestionRepository.countSuggestions(userId);

    var dtos =
        suggestions.stream().map(GraphSuggestionUserDto::fromDomain).collect(Collectors.toList());

    return new GraphSuggestionsResponse(dtos, page, pageSize, total);
  }
}
