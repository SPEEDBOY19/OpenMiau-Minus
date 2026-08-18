package me.ksyz.accountmanager.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class OAuthServer {
  private static final String AUTH_URL =
      "https://login.live.com/oauth20_authorize.srf?client_id=<client_id>&redirect_uri=<redirect_uri>&response_type=code&display=touch&scope=<scope>&prompt=select_account";

  private final OAuthHandler handler;
  private final String clientId;
  private final String redirectUri;
  private final String scope;
  private HttpServer httpServer;
  private ThreadPoolExecutor threadPoolExecutor;

  public OAuthServer(OAuthHandler handler, String clientId, String redirectUri, String scope)
      throws IOException {
    this.handler = handler;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.scope = scope;
    this.httpServer = HttpServer.create(new InetSocketAddress("localhost", 21919), 0);
    this.threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
  }

  public void start() {
    httpServer.createContext("/login", new OAuthHttpHandler(this));
    httpServer.setExecutor(threadPoolExecutor);
    httpServer.start();
    String url =
        AUTH_URL
            .replace("<client_id>", clientId)
            .replace("<redirect_uri>", redirectUri)
            .replace("<scope>", scope);
    handler.openUrl(url);
  }

  public void stop(boolean isInterrupt) {
    httpServer.stop(0);
    threadPoolExecutor.shutdown();
    if (isInterrupt) {
      handler.authError("Has been interrupted");
    }
  }

  private static class OAuthHttpHandler implements HttpHandler {
    private final OAuthServer server;

    OAuthHttpHandler(OAuthServer server) {
      this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      Map<String, String> query = getQueryParams(exchange.getRequestURI().getQuery());
      if (query.containsKey("code")) {
        try {
          server.handler.authResult(query.get("code"), server.clientId, server.scope);
          String response = "Login Success";
          exchange.sendResponseHeaders(200, response.length());
          OutputStream os = exchange.getResponseBody();
          os.write(response.getBytes());
          os.close();
        } catch (Exception e) {
          server.handler.authError(e.toString());
          String response = "Error: " + e;
          exchange.sendResponseHeaders(500, response.length());
          OutputStream os = exchange.getResponseBody();
          os.write(response.getBytes());
          os.close();
        }
      } else {
        server.handler.authError("No code in the query");
        String response = "No code in the query";
        exchange.sendResponseHeaders(500, response.length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
      }
      server.stop(false);
    }
  }

  private static Map<String, String> getQueryParams(String query) {
    Map<String, String> params = new HashMap<>();
    if (query != null) {
      for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        if (pair.length == 2) {
          params.put(pair[0], pair[1]);
        }
      }
    }
    return params;
  }
}
