package me.ksyz.accountmanager.auth;

public interface OAuthHandler {
  void openUrl(final String url);

  void authResult(final String refreshToken, final String clientId, final String scope);

  void authError(final String error);
}
