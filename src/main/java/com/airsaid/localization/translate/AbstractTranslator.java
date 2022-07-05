/*
 * Copyright 2021 Airsaid. https://github.com/airsaid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.airsaid.localization.translate;

import com.airsaid.localization.config.SettingsState;
import com.airsaid.localization.translate.impl.google.GoogleTranslator;
import com.airsaid.localization.translate.lang.Lang;
import com.airsaid.localization.utils.NotificationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author airsaid
 */
public abstract class AbstractTranslator implements Translator, TranslatorConfigurable {

  protected static final Logger LOG = Logger.getInstance(AbstractTranslator.class);

  private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

  @Override
  public String doTranslate(@NotNull Project project, @NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) throws TranslationException {
    final Lang toLanguage = checkSupportedLanguages(fromLang, toLang, text);

    String requestUrl = getRequestUrl(fromLang, toLanguage, text);
    RequestBuilder requestBuilder = HttpRequests.post(requestUrl, CONTENT_TYPE);
    configureRequestBuilder(requestBuilder);

    try {
      return requestBuilder.connect(request -> {
        String requestParams = getRequestParams(fromLang, toLanguage, text)
            .stream()
            .map(pair -> {
              try {
                return pair.first.concat("=").concat(URLEncoder.encode(pair.second, StandardCharsets.UTF_8.name()));
              } catch (UnsupportedEncodingException e) {
                throw new TranslationException(fromLang, toLanguage, text, e);
              }
            })
            .collect(Collectors.joining("&"));
        if (!requestParams.isEmpty()) {
          request.write(requestParams);
        }
        String requestBody = getRequestBody(fromLang, toLanguage, text);
        if (!requestBody.isEmpty()) {
          request.write(URLEncoder.encode(requestBody, StandardCharsets.UTF_8.name()));
        }
        String resultText = request.readString();
        return parsingResult(fromLang, toLanguage, text, resultText);
      });
    } catch (IOException e) {
      e.printStackTrace();
      NotificationUtil.notifyError(project, new TranslationException(fromLang, toLanguage, text, e).toString());
      return "";
    }
  }

  @Override
  public @Nullable Icon getIcon() {
    return null;
  }

  @Override
  public boolean isNeedAppId() {
    return true;
  }

  @Override
  public @Nullable String getAppId() {
    return SettingsState.getInstance().getAppId(getKey());
  }

  @Override
  public String getAppIdDisplay() {
    return "APP ID";
  }

  @Override
  public boolean isNeedAppKey() {
    return true;
  }

  @Override
  public @Nullable String getAppKey() {
    return SettingsState.getInstance().getAppKey(getKey());
  }

  @Override
  public String getAppKeyDisplay() {
    return "APP KEY";
  }

  @Override
  public @Nullable String getApplyAppIdUrl() {
    return null;
  }

  @NotNull
  public String getRequestUrl(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public List<Pair<String, String>> getRequestParams(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public String getRequestBody(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text) {
    return "";
  }

  public void configureRequestBuilder(@NotNull RequestBuilder requestBuilder) {

  }

  @NotNull
  public String parsingResult(@NotNull Lang fromLang, @NotNull Lang toLang, @NotNull String text, @NotNull String resultText) {
    throw new UnsupportedOperationException();
  }

  /**
   * check if current translator supports target language
   *
   * @param fromLang source language
   * @param toLang   target language
   * @param text     text to be translated
   * @return target language instance
   */
  @Nullable
  public Lang checkSupportedLanguages(Lang fromLang, Lang toLang, String text) {
    List<Lang> supportedLanguages = getSupportedLanguages();
    // 原先此处检查没有将 toLang 的更改字段 translationCode 应用到后续流程中
    if (!supportedLanguages.contains(toLang) || !supportedLanguages.contains(fromLang)) {
//      throw new TranslationException(fromLang, toLang, text, toLang.getEnglishName() + " is not supported.");
      return null;
    }
    return supportedLanguages.get(supportedLanguages.indexOf(toLang));
  }

  /**
   * return the config fallback translator when something happened
   *
   * @return the KEY of fallback translator
   */
  @Override
  public @NotNull String getFallbackTranslator() {
    return GoogleTranslator.KEY;
  }
}
