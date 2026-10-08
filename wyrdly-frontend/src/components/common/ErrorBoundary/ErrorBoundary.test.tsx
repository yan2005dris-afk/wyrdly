import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

const ProblematicChild = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error("Simulated render crash");
  }
  return <div data-testid="child-content">Normal Content</div>;
};

describe("ErrorBoundary Component", () => {
  const originalConsoleError = console.error;

  beforeEach(() => {
    console.error = vi.fn();
  });

  afterEach(() => {
    console.error = originalConsoleError;
  });

  it("renders children when no error occurs", () => {
    render(
      <ErrorBoundary>
        <ProblematicChild shouldThrow={false} />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId("child-content")).toBeInTheDocument();
    expect(
      screen.queryByTestId("error-boundary-fallback"),
    ).not.toBeInTheDocument();
  });

  it("renders fallback UI when child component throws error during render", () => {
    render(
      <ErrorBoundary>
        <ProblematicChild shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId("error-boundary-fallback")).toBeInTheDocument();
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(screen.getByTestId("error-boundary-message")).toHaveTextContent(
      "Simulated render crash",
    );
    expect(screen.getByTestId("error-boundary-retry-btn")).toBeInTheDocument();
    expect(screen.getByTestId("error-boundary-reload-btn")).toBeInTheDocument();
  });

  it("renders custom fallback when provided", () => {
    render(
      <ErrorBoundary
        fallback={<div data-testid="custom-fallback">Custom Error</div>}
      >
        <ProblematicChild shouldThrow={true} />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId("custom-fallback")).toBeInTheDocument();
    expect(screen.getByText("Custom Error")).toBeInTheDocument();
  });

  it("resets error state when Try Again button is clicked", () => {
    let throwError = true;

    const DynamicChild = () => {
      if (throwError) {
        throw new Error("Temporary error");
      }
      return <div data-testid="recovered-content">Recovered!</div>;
    };

    render(
      <ErrorBoundary>
        <DynamicChild />
      </ErrorBoundary>,
    );

    expect(screen.getByTestId("error-boundary-fallback")).toBeInTheDocument();

    throwError = false;
    fireEvent.click(screen.getByTestId("error-boundary-retry-btn"));

    expect(screen.getByTestId("recovered-content")).toBeInTheDocument();
  });
});
