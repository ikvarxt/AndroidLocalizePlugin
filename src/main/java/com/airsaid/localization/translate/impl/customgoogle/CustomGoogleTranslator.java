package com.airsaid.localization.translate.impl.customgoogle;

import com.airsaid.localization.translate.impl.google.AbsGoogleTranslator;
import com.airsaid.localization.translate.impl.google.GoogleTranslationResult;
import com.airsaid.localization.translate.lang.Lang;
import com.airsaid.localization.translate.util.AgentUtil;
import com.airsaid.localization.translate.util.GsonUtil;
import com.airsaid.localization.translate.util.UrlBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.util.io.RequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.List;

/**
 * this couldn't work
 */
public class CustomGoogleTranslator extends AbsGoogleTranslator {

  public static final String HOST_URL = "https://translate.google.cn";
  private static final String BASE_URL = HOST_URL.concat("/translate_a/single");

  @Override
  public @NotNull String getKey() {
    return "Custom Google";
  }

  @Override
  public @NotNull String getName() {
    return "Custom Google";
  }

  @Override
  public boolean isNeedAppId() {
    return false;
  }

  @Override
  public boolean isNeedAppKey() {
    return false;
  }

  @Override
  public @NotNull String getRequestUrl(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    return new UrlBuilder(BASE_URL)
        .addQueryParameter("client", "webapp")
        .addQueryParameter("sl", fromLang.getTranslationCode()) // source language code (auto for auto detection)
        .addQueryParameter("tl", toLang.getTranslationCode()) // translation language
        .addQueryParameters("dt", "at", "bd", "ld", "md", "qca", "rw", "rm", "ss", "t") // specify what to return
        .addQueryParameter("source", "btn")
        .addQueryParameter("ssel", "0")
        .addQueryParameter("tsel", "0")
        .addQueryParameter("kc", "0")
        // from original google translator
        .addQueryParameter("dj", "1") // json response with names
        .addQueryParameter("ie", "UTF-8") // input encoding
        .addQueryParameter("oe", "UTF-8") // output encoding

        .addQueryParameter("tk", getToken(text)) // translate token
        .build();
  }

  @Nullable
  private String getToken(String text) {
    try {
      ScriptEngine engine = new ScriptEngineManager().getEngineByName("Nashorn");
//      FileReader reader = new FileReader("src/main/resources/getGoogleToken.js");
      engine.eval(getTokenJs);

      Invocable invocable = (Invocable) engine;
      String token = (String) invocable.invokeFunction("GetToken", text);
      LOG.info("token: " + token);
      return token;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public @NotNull List<Pair<String, String>> getRequestParams(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    List<Pair<String, String>> params = new ArrayList<>();
    params.add(Pair.create("q", text));
    return params;
  }

  @Override
  public void configureRequestBuilder(@NotNull RequestBuilder requestBuilder) {
    requestBuilder.userAgent(AgentUtil.getUserAgent()).tuner(connection -> {
//      connection.setRequestProperty("Referer", GoogleTranslator.HOST_URL);
      connection.setRequestProperty("authority", "translate.google.com");
      connection.setRequestProperty("method", "GET");
      connection.setRequestProperty("scheme", "https");
      connection.setRequestProperty("accept", "*/*");
      connection.setRequestProperty("accept-encoding", "gzip, deflate, br");
      connection.setRequestProperty("accept-language", "zh-CN,zh;q=0,ja;q=0.8");
      connection.setRequestProperty("cookie", "_ga=GA1.3.110668007.1547438795; _gid=GA1.3.1522575542.1548327032; 1P_JAR=2019-1-24-10; NID=156=ELGmtJHel1YG9Q3RxRI4HTgAc3l1n7Y6PAxGwvecTJDJ2ScgW2p-CXdvh88XFb9dTbYEBkoayWb-2vjJbB-Rhf6auRj-M-2QRUKdZG04lt7ybh8GgffGtepoA4oPN9OO9TeAoWDY0HJHDWCUwCpYzlaQK-gKCh5aVC4HVMeoppI");
      connection.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; WOW64)  AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.108 Safari/537.36");
      connection.setRequestProperty("x-client-data", "CKi1yQEIhrbJAQijtskBCMG2yQEIqZ3KAQioo8oBCL+nygEI7KfKAQjiqMoBGPmlygE=");
    });
  }

  @Override
  public @NotNull String parsingResult(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text, @NotNull String resultText) {
    LOG.info("parsingResult: " + resultText);
    GoogleTranslationResult googleTranslationResult = GsonUtil.getInstance().getGson().fromJson(resultText, GoogleTranslationResult.class);
    return googleTranslationResult.getTranslationResult();
  }

  private static final String getTokenJs = "      function GetToken(a) {\n" +
      "          var k = \"\";\n" +
      "          var b = 406644;\n" +
      "          var b1 = 3293161072;\n" +
      "          var jd = \".\";\n" +
      "          var $b = \"+-a^+6\";\n" +
      "          var Zb = \"+-3^+b+-f\";\n" +
      "          for (var e = [], f = 0, g = 0; g < a.length; g++) {\n" +
      "              var m = a.charCodeAt(g);\n" +
      "              128 > m ? e[f++] = m : (2048 > m ? e[f++] = m >> 6 | 192 : (55296 == (m & 64512) && g + 1 < a.length && 56320 == (a.charCodeAt(g + 1) & 64512) ? (m = 65536 + ((m & 1023) << 10) + (a.charCodeAt(++g) & 1023),\n" +
      "              e[f++] = m >> 18 | 240,\n" +
      "              e[f++] = m >> 12 & 63 | 128) : e[f++] = m >> 12 | 224,\n" +
      "              e[f++] = m >> 6 & 63 | 128),\n" +
      "              e[f++] = m & 63 | 128)\n" +
      "          }\n" +
      "          a = b;\n" +
      "          for (f = 0; f < e.length; f++) a += e[f],\n" +
      "          a = RL(a, $b);\n" +
      "          a = RL(a, Zb);\n" +
      "          a ^= b1 || 0;\n" +
      "          0 > a && (a = (a & 2147483647) + 2147483648);\n" +
      "          a %= 1E6;\n" +
      "          return a.toString() + jd + (a ^ b)\n" +
      "      };\n" +
      "      function RL(a, b) {\n" +
      "          var t = \"a\";\n" +
      "          var Yb = \"+\";\n" +
      "          for (var c = 0; c < b.length - 2; c += 3) {\n" +
      "              var d = b.charAt(c + 2),\n" +
      "              d = d >= t ? d.charCodeAt(0) - 87 : Number(d),\n" +
      "              d = b.charAt(c + 1) == Yb ? a >>> d: a << d;\n" +
      "              a = b.charAt(c) == Yb ? a + d & 4294967295 : a ^ d\n" +
      "          }\n" +
      "          return a\n" +
      "      }";
}
