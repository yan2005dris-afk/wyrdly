import { BrowserRouter, Routes, Route } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./lib/queryClient";
import { AuthProvider, ProtectedRoute } from "./features/auth";
import { AuthPage } from "./pages/AuthPage";
import { FeedPage } from "./pages/FeedPage";
import { ProfilePage } from "./pages/ProfilePage";
import { ChatPage } from "./pages/ChatPage";
import { ExplorePage } from "./pages/ExplorePage";
import { RootRedirect } from "./components/RootRedirect";
import { MainLayout } from "./components/layout";
import { NotFoundPage } from "./pages/NotFoundPage";
import { ErrorBoundary } from "./components/common/ErrorBoundary";
import { useUnreadMessagesStore } from "./features/chat";

/** MainLayout fed with the live sidebar unread badge from the chat store. */
function MainLayoutRoute() {
  const totalUnread = useUnreadMessagesStore((s) => s.totalUnread);
  return <MainLayout unreadMessagesCount={totalUnread} />;
}

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<AuthPage />} />
              <Route path="/register" element={<AuthPage />} />
              <Route path="/" element={<RootRedirect />} />

              <Route element={<ProtectedRoute />}>
                <Route element={<MainLayoutRoute />}>
                  <Route path="/feed" element={<FeedPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route path="/profile/:username" element={<ProfilePage />} />
                  <Route path="/chat" element={<ChatPage />} />
                  <Route path="/explore" element={<ExplorePage />} />
                  <Route path="/notifications" element={<FeedPage />} />
                </Route>
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export default App;
