package com.wyrdly.infrastructure.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StrictObjectMapperCustomizerTest {

  record Counter(int count, boolean active) {}

  record Named(String name, String bio) {}

  enum Color {
    RED,
    BLUE
  }

  record Painted(Color color) {}

  record Stamped(Instant at) {}

  static class Empty {}

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper().findAndRegisterModules();
    new StrictObjectMapperCustomizer().customize(mapper);
  }

  @Test
  void deserialize_RejectsNullForPrimitiveInt() {
    assertThrows(
        MismatchedInputException.class,
        () -> mapper.readValue("{\"count\":null,\"active\":true}", Counter.class));
  }

  @Test
  void deserialize_RejectsNullForPrimitiveBoolean() {
    assertThrows(
        MismatchedInputException.class,
        () -> mapper.readValue("{\"count\":1,\"active\":null}", Counter.class));
  }

  @Test
  void deserialize_AcceptsValidPrimitives() throws Exception {
    Counter counter = mapper.readValue("{\"count\":5,\"active\":true}", Counter.class);

    assertEquals(new Counter(5, true), counter);
  }

  @Test
  void deserialize_RejectsUnknownProperty() {
    assertThrows(
        UnrecognizedPropertyException.class,
        () -> mapper.readValue("{\"name\":\"a\",\"bio\":\"b\",\"extra\":1}", Named.class));
  }

  @Test
  void deserialize_MissingRecordPropertyDefaultsToNull() throws Exception {
    Named named = mapper.readValue("{\"name\":\"a\"}", Named.class);

    assertEquals(new Named("a", null), named);
  }

  @Test
  void deserialize_AcceptsNullForObjectFields() throws Exception {
    Named named = mapper.readValue("{\"name\":\"a\",\"bio\":null}", Named.class);

    assertEquals(new Named("a", null), named);
  }

  @Test
  void deserialize_RejectsNumericEnum() {
    assertThrows(
        MismatchedInputException.class, () -> mapper.readValue("{\"color\":0}", Painted.class));
  }

  @Test
  void deserialize_RejectsUnknownEnumValue() {
    assertThrows(
        InvalidFormatException.class,
        () -> mapper.readValue("{\"color\":\"GREEN\"}", Painted.class));
  }

  @Test
  void deserialize_RejectsCaseMismatchedEnum() {
    assertThrows(
        InvalidFormatException.class, () -> mapper.readValue("{\"color\":\"red\"}", Painted.class));
  }

  @Test
  void serialize_AllowsEmptyBeans() throws Exception {
    assertEquals("{}", mapper.writeValueAsString(new Empty()));
  }

  @Test
  void serialize_WritesDatesAsIso8601() throws Exception {
    Stamped stamped = new Stamped(Instant.parse("2026-10-07T12:00:00Z"));

    assertEquals("{\"at\":\"2026-10-07T12:00:00Z\"}", mapper.writeValueAsString(stamped));
  }
}
