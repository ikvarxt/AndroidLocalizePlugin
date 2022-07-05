package com.airsaid.localization.translate.impl.deepl;

import com.airsaid.localization.translate.AbstractTranslator;
import com.airsaid.localization.translate.TranslationResult;
import com.airsaid.localization.translate.lang.Lang;
import com.airsaid.localization.translate.lang.Languages;
import com.airsaid.localization.translate.util.AgentUtil;
import com.airsaid.localization.translate.util.GsonUtil;
import com.airsaid.localization.translate.util.UrlBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.io.RequestBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ikvarxt
 */
public class DeeplTranslator extends AbstractTranslator {

  private static final Logger LOG = Logger.getInstance(DeeplTranslator.class);

  private static final String KEY = "DeepL";
  private static final String HOST_URL = "https://api-free.deepl.com";
  private static final String BASE_URL = HOST_URL.concat("/v2/translate");

  private List<Lang> supportedLanguages;

  @Override
  public @NotNull String getKey() {
    return KEY;
  }

  @Override
  public @NotNull String getName() {
    return "DeepL";
  }

  @Override
  public @NotNull List<Lang> getSupportedLanguages() {
    if (supportedLanguages == null) {
      supportedLanguages = new ArrayList<>();
      supportedLanguages.add(Languages.BULGARIAN);
      supportedLanguages.add(Languages.CZECH);
      supportedLanguages.add(Languages.DANISH);
      supportedLanguages.add(Languages.GERMAN);
      supportedLanguages.add(Languages.GREEK);
      supportedLanguages.add(Languages.ENGLISH);
      supportedLanguages.add(Languages.SPANISH);
      supportedLanguages.add(Languages.ESTONIAN);
      supportedLanguages.add(Languages.FINNISH);
      supportedLanguages.add(Languages.FRENCH);
      supportedLanguages.add(Languages.HUNGARIAN);
      supportedLanguages.add(Languages.INDONESIAN.setTranslationCode("IN"));
      supportedLanguages.add(Languages.ITALIAN);
      supportedLanguages.add(Languages.JAPANESE);
      supportedLanguages.add(Languages.LITHUANIAN);
      supportedLanguages.add(Languages.LATVIAN);
      supportedLanguages.add(Languages.DUTCH);
      supportedLanguages.add(Languages.POLISH);
      supportedLanguages.add(Languages.PORTUGUESE);
      supportedLanguages.add(Languages.ROMANIAN);
      supportedLanguages.add(Languages.RUSSIAN);
      supportedLanguages.add(Languages.SLOVAK);
      supportedLanguages.add(Languages.SLOVENIAN);
      supportedLanguages.add(Languages.SWEDISH);
      supportedLanguages.add(Languages.TURKISH);
      supportedLanguages.add(Languages.CHINESE);
      supportedLanguages.add(Languages.CHINESE_SIMPLIFIED.setTranslationCode("ZH"));
    }
    return supportedLanguages;
  }

  @Override
  public boolean isNeedAppId() {
    return false;
  }

  @Override
  public boolean isNeedAppKey() {
    return true;
  }

  @Override
  public String getAppKeyDisplay() {
    return "Deepl Auth Key";
  }

  @Override
  public @NotNull String getRequestUrl(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    return new UrlBuilder(BASE_URL)
        // omit for auto-detect language
        // .addQueryParameter("source_lang", fromLang.getTranslationCode().toUpperCase())
        .addQueryParameter("target_lang", toLang.getTranslationCode().toUpperCase())
        .addQueryParameter("auth_key", getAppKey())
        .build();
  }

  @Override
  public @NotNull List<Pair<String, String>> getRequestParams(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    List<Pair<String, String>> list = new ArrayList<>();
    list.add(Pair.create("text", text));
    return list;
  }

  @Override
  public void configureRequestBuilder(@NotNull RequestBuilder requestBuilder) {
    requestBuilder.userAgent(AgentUtil.getUserAgent())
        .tuner(connection -> connection.setRequestProperty("Referer", HOST_URL));
  }

  @Override
  public @NotNull String parsingResult(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text, @NotNull String resultText) {
    LOG.info("parsingResult: " + resultText);
    TranslationResult result = GsonUtil.getInstance().getGson().fromJson(resultText, DeeplTranslationResult.class);
    return result.getTranslationResult();
  }
}
