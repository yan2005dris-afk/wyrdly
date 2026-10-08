import { describe, it, expect } from "vitest";
import {
  predictToggle,
  withUserReaction,
  type ReactionState,
} from "./reactionState";

const EMPTY: ReactionState = {
  reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
  userReaction: undefined,
};

describe("withUserReaction", () => {
  it("adds a reaction when the user had none", () => {
    const next = withUserReaction(EMPTY, "LOVE");

    expect(next.userReaction).toBe("LOVE");
    expect(next.reactions).toEqual({
      LIKE: 0,
      LOVE: 1,
      CELEBRATE: 0,
      RETWEET: 0,
    });
  });

  it("removes the reaction when moving to undefined", () => {
    const loved = withUserReaction(EMPTY, "LOVE");
    const next = withUserReaction(loved, undefined);

    expect(next.userReaction).toBeUndefined();
    expect(next.reactions.LOVE).toBe(0);
  });

  it("decrements the previous type and increments the new one on type switch", () => {
    const loved: ReactionState = {
      reactions: { LIKE: 3, LOVE: 2, CELEBRATE: 0, RETWEET: 0 },
      userReaction: "LOVE",
    };
    const next = withUserReaction(loved, "CELEBRATE");

    expect(next.userReaction).toBe("CELEBRATE");
    expect(next.reactions).toEqual({
      LIKE: 3,
      LOVE: 1,
      CELEBRATE: 1,
      RETWEET: 0,
    });
  });

  it("keeps counters consistent across LIKE → CELEBRATE → LIKE", () => {
    let state = withUserReaction(EMPTY, "LIKE");
    state = withUserReaction(state, "CELEBRATE");
    state = withUserReaction(state, "LIKE");

    expect(state.userReaction).toBe("LIKE");
    expect(state.reactions).toEqual({
      LIKE: 1,
      LOVE: 0,
      CELEBRATE: 0,
      RETWEET: 0,
    });
  });

  it("never produces negative counters on inconsistent input", () => {
    const corrupted: ReactionState = {
      reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
      userReaction: "LIKE",
    };
    const next = withUserReaction(corrupted, undefined);

    expect(next.reactions.LIKE).toBe(0);
  });

  it("returns the same reference when the reaction does not change", () => {
    const loved = withUserReaction(EMPTY, "LOVE");

    expect(withUserReaction(loved, "LOVE")).toBe(loved);
    expect(withUserReaction(EMPTY, undefined)).toBe(EMPTY);
  });

  it("preserves unrelated fields of the input object", () => {
    const post = { ...EMPTY, id: "post-1", content: "hello" };
    const next = withUserReaction(post, "LIKE");

    expect(next.id).toBe("post-1");
    expect(next.content).toBe("hello");
  });

  it("does not mutate the input", () => {
    const input: ReactionState = {
      reactions: { LIKE: 1, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
      userReaction: "LIKE",
    };
    withUserReaction(input, "LOVE");

    expect(input.userReaction).toBe("LIKE");
    expect(input.reactions.LIKE).toBe(1);
  });
});

describe("predictToggle", () => {
  it("removes the reaction when clicking the active type", () => {
    expect(predictToggle("LOVE", "LOVE")).toBeUndefined();
  });

  it("sets the clicked type when there is no reaction or a different one", () => {
    expect(predictToggle(undefined, "LIKE")).toBe("LIKE");
    expect(predictToggle("LOVE", "CELEBRATE")).toBe("CELEBRATE");
  });
});
