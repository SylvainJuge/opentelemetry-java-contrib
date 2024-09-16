package io.opentelemetry.contrib.jmxscraper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmxRemoteClient {

  private static final Logger logger = LoggerFactory.getLogger(JmxRemoteClient.class);

  private final String host;
  private final int port;
  private String userName;
  private String password;
  private String profile;
  private String realm;
  private boolean sslRegistry;

  private JmxRemoteClient(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public static JmxRemoteClient createNew(String host, int port) {
    return new JmxRemoteClient(host, port);
  }

  public JmxRemoteClient userCredentials(String userName, String password) {
    this.userName = userName;
    this.password = password;
    return this;
  }

  public JmxRemoteClient withRemoteProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public JmxRemoteClient withRealm(String realm) {
    this.realm = realm;
    return this;
  }

  public JmxRemoteClient withSSLRegistry() {
    this.sslRegistry = true;
    return this;
  }

  public JMXConnector connect() throws IOException {
    Map<String, Object> env = new HashMap<>();
    if (userName != null && password != null) {
      env.put(JMXConnector.CREDENTIALS, new String[] {userName, password});
    }

    if (profile != null) {
      env.put("jmx.remote.profile", profile);
    }

    try {
      // Not all supported versions of Java contain this Provider
      Class<?> klass = Class.forName("com.sun.security.sasl.Provider");
      Provider provider = (Provider) klass.getDeclaredConstructor().newInstance();
      Security.addProvider(provider);

      env.put(
          "jmx.remote.sasl.callback.handler",
          (CallbackHandler) callbacks -> {
            for (Callback callback : callbacks) {
              if (callback instanceof NameCallback) {
                ((NameCallback) callback).setName(userName);
              } else if (callback instanceof PasswordCallback) {
                char[] pwd = password == null ? null : password.toCharArray();
                ((PasswordCallback) callback).setPassword(pwd);
              } else if (callback instanceof RealmCallback) {
                ((RealmCallback) callback).setText(realm);
              } else {
                throw new UnsupportedCallbackException(callback);
              }
            }
          });
    } catch (final ReflectiveOperationException e) {
      logger.warn("SASL unsupported in current environment: " + e.getMessage(), e);
    }

    JMXServiceURL url = buildUrl(host, port);
    try {
      if (sslRegistry) {
        return connectSSLRegistry(url, env);
      } else {
        return JMXConnectorFactory.connect(url, env);
      }
    } catch (IOException e) {
      throw new IOException("Unable to connect to " + url.getHost() + ":" + url.getPort(), e);
    }
  }

  public JMXConnector connectSSLRegistry(JMXServiceURL url, Map<String, Object> env) {
    throw new IllegalStateException("TODO");
  }

  private static JMXServiceURL buildUrl(String host, int port) {
    StringBuilder sb = new StringBuilder("service:jmx:rmi:///jndi/rmi://");
    if (host != null) {
      sb.append(host);
    }
    sb.append(":")
        .append(port)
        .append("/jmxrmi");

    try {
      return new JMXServiceURL(sb.toString());
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("invalid url", e);
    }
  }

}
