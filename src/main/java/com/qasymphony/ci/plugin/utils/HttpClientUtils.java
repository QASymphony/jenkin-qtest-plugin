package com.qasymphony.ci.plugin.utils;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.kohsuke.stapler.StaplerRequest;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

/**
 * @author trongle
 * @version 10/21/2015 2:09 PM trongle $
 * @since 1.0
 */
public class HttpClientUtils {
  public static Integer RETRY_MAX_COUNT = 5;
  public static Boolean RETRY_REQUEST_SEND_RETRY_ENABLED = false;
  private static final Integer DEFAULT_SOCKET_TIMEOUT = 60;//seconds
  private static HttpClient CLIENT;

  private HttpClientUtils() {
  }

  private static HttpClient getClient() throws ClientRequestException {
    initClient();
    return CLIENT;
  }

  private static synchronized void initClient() throws ClientRequestException {
    if (null == CLIENT) {
      try {
        CLIENT = getHttpClient();
      } catch (Exception e) {
        throw new ClientRequestException(e.getMessage());
      }
    }
  }

  /**
   * Encode url
   *
   * @param url url
   * @return encoded string
   */
  public static String encode(String url) {
    try {
      return URLEncoder.encode(url, "UTF-8");
    } catch (Exception e) {
      return url;
    }
  }

  /**
   * @param request request
   * @return server url
   */
  public static String getServerUrl(StaplerRequest request) {
    return getServerUrl(request.getServerPort(), request.getScheme(), request.getServerName(), request.getContextPath());
  }

  public static String getServerUrl(int serverPort, String scheme, String serverName, String contextPath) {
    Boolean isDefaultPort = serverPort == 443 || serverPort == 80;
    return String.format("%s://%s%s%s", scheme, serverName, isDefaultPort ? "" : ":" + serverPort, contextPath);
  }

  /**
   * get port from url
   *
   * @param url url
   * @return port
   */
  public static int getPort(String url) {
    URL uri = null;
    try {
      uri = new URL(url);
    } catch (Exception e) {
    }
    int port = 0;
    if (uri != null) {
      port = (uri.getPort() > 0 ? uri.getPort() : ("http".equalsIgnoreCase(uri.getProtocol()) ? 80 : 443));
    }
    return port;
  }

  /**
   * get mac address and port
   *
   * @return mac address
   * @throws Exception Exception
   */
  public static String getMacAddress() throws Exception {
    NetworkInterface network = NetworkInterface.getByInetAddress(InetAddress.getLocalHost());

    //if cannot get by localhost, we try to get first NetworkInterface
    if (null == network) {
      network = NetworkInterface.getByIndex(0);
    }

    byte[] mac;
    try {
      mac = network.getHardwareAddress();
    } catch (Exception e) {
      mac = new byte[0];
    }

    if (mac != null && mac.length <= 0) {
      return null;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < mac.length; i++) {
      sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
    }
    return sb.toString();
  }

  /**
   * @param url     url
   * @param headers headers
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity get(String url, Map<String, String> headers) throws ClientRequestException {
    HttpGet request = new HttpGet(url);
    addHeader(request, headers);
    return execute(request);
  }

  /**
   * @param url     url
   * @param headers headers
   * @param data    data
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity post(String url, Map<String, String> headers, String data)
          throws ClientRequestException {
    return post(url, headers, data, ContentType.APPLICATION_JSON);
  }

  /**
   * @param url         url
   * @param headers     headers
   * @param data        data
   * @param contentType contentType
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity post(String url, Map<String, String> headers, String data, ContentType contentType)
          throws ClientRequestException {
    HttpPost request = new HttpPost(url);
    addHeader(request, headers);
    if (!StringUtils.isEmpty(data))
      request.setEntity(new StringEntity(data, contentType));
    return execute(request);
  }

  /**
   * @param url     url
   * @param headers headers
   * @param data    data
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity put(String url, Map<String, String> headers, String data) throws ClientRequestException {
    return put(url, headers, data, ContentType.APPLICATION_JSON);
  }

  /**
   * @param url         url
   * @param headers     headers
   * @param data        data
   * @param contentType contentType
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity put(String url, Map<String, String> headers, String data, ContentType contentType)
          throws ClientRequestException {
    HttpPut request = new HttpPut(url);
    addHeader(request, headers);
    if (!StringUtils.isEmpty(data))
      request.setEntity(new StringEntity(data, contentType));
    return execute(request);
  }

  /**
   * @param url     url
   * @param headers headers
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity delete(String url, Map<String, String> headers) throws ClientRequestException {
    return delete(url, headers, ContentType.APPLICATION_JSON);
  }

  /**
   * @param url         url
   * @param headers     headers
   * @param contentType contentType
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity delete(String url, Map<String, String> headers, ContentType contentType)
          throws ClientRequestException {
    HttpDelete request = new HttpDelete(url);
    addHeader(request, headers);
    return execute(request);
  }

  private static ResponseEntity doExecute(HttpResponse response) throws Exception {
    if (null == response) {
      throw new Exception("response is null.");
    }
    HttpEntity entity = response.getEntity();
    BufferedReader rd = new BufferedReader(new InputStreamReader(entity.getContent()));

    StringBuilder result = new StringBuilder();
    String line = "";
    while ((line = rd.readLine()) != null) {
      result.append(line);
    }
    return new ResponseEntity(result.toString(), response.getStatusLine().getStatusCode());
  }

  /**
   * Execute a request
   *
   * @param request request
   * @return {@link ResponseEntity}
   * @throws ClientRequestException ClientRequestException
   */
  public static ResponseEntity execute(HttpUriRequest request) throws ClientRequestException {
    HttpClient client;
    try {
      client = getClient();
    } catch (Exception e) {
      throw new ClientRequestException("Cannot get HttpClient." + e.getMessage(), e);
    }
    HttpResponse response = null;
    ResponseEntity responseEntity;

    try {
      response = client.execute(request);
      responseEntity = doExecute(response);
    } catch (Exception e) {
      throw new ClientRequestException(e.getMessage(), e);
    } finally {
      if (null != response)
        org.apache.http.client.utils.HttpClientUtils.closeQuietly(response);
    }

    return responseEntity;
  }

  private static void addHeader(HttpRequestBase httpRequestBase, Map<String, String> headers) {
    if (headers != null) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        httpRequestBase.addHeader(entry.getKey(), entry.getValue());
      }
    }
  }

  private static void setHttpProxy(HttpClientBuilder httpClientBuilder) {
    ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
    if (proxyConfig != null) {
      HttpHost proxy = new HttpHost(proxyConfig.name, proxyConfig.port);
      String username = proxyConfig.getUserName();
      String password = proxyConfig.getPassword();

      Credentials credentials;
      if (username != null && StringUtils.isNotEmpty(username) == true) {
        credentials = new UsernamePasswordCredentials(username, password);
      } else  {
        credentials = new UsernamePasswordCredentials("", "");
      }
      AuthScope authScope = new AuthScope(proxyConfig.name, proxyConfig.port);
      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(authScope, credentials);
      httpClientBuilder.setProxy(proxy).setDefaultCredentialsProvider(credsProvider).build();
    }
  }

  public static HttpClient getHttpClient() throws Exception {
    int timeout;
    try {
      timeout = Integer.parseInt(System.getenv("SOCKET_TIMEOUT"));
    } catch (Exception e) {
      timeout = DEFAULT_SOCKET_TIMEOUT;
    }

    HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
            .useSystemProperties();

    setHttpProxy(httpClientBuilder);

    SSLConnectionSocketFactory sslSocketFactory = getSslSocketFactory();
    httpClientBuilder.setSSLSocketFactory(sslSocketFactory)
            .setConnectionReuseStrategy(new NoConnectionReuseStrategy());

    timeout = timeout * 1000;

    httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom()
            .setSocketTimeout(timeout)
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            .build());
    httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler(RETRY_MAX_COUNT, RETRY_REQUEST_SEND_RETRY_ENABLED) {
      @Override
      public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
        if (executionCount > this.getRetryCount())
          return false;
        if (exception instanceof HttpHostConnectException)
          return true;
        return super.retryRequest(exception, executionCount, context);
      }
    });
    return httpClientBuilder.build();
  }

  private static SSLConnectionSocketFactory getSslSocketFactory()
          throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    SSLContext sslContext = getSslContext();
    return new SSLConnectionSocketFactory(sslContext, ALLOW_ALL_HOSTNAME_VERIFIER);
  }

  private static SSLContext getSslContext() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    org.apache.http.ssl.SSLContextBuilder sslContextBuilder = new org.apache.http.ssl.SSLContextBuilder();
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    TrustStrategy trustStrategy = new TrustAllStrategy();
    sslContextBuilder.loadTrustMaterial(keyStore, trustStrategy);
    return sslContextBuilder.build();
  }

  /**
   * Trust all certificates.
   */
  public static class TrustAllStrategy implements TrustStrategy {

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      return true;
    }
  }
}