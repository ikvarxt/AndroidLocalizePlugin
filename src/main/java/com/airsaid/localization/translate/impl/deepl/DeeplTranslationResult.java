package com.airsaid.localization.translate.impl.deepl;

import com.airsaid.localization.translate.TranslationResult;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public class DeeplTranslationResult implements TranslationResult {

  @SerializedName("translations")
  public Translations result;

  public DeeplTranslationResult(Translations result) {
    this.result = result;
  }

  public Translations getResult() {
    return result;
  }

  @Override
  public @NotNull String getTranslationResult() {
    if (result == null) return "";
    return result.getText();
  }

  static class Translations {

    @SerializedName("detected_source_language")
    public String detectedLanguage;

    /**
     * the translation result
     */
    public String text;

    public Translations(String detectedLanguage, String text) {
      this.detectedLanguage = detectedLanguage;
      this.text = text;
    }

    public String getDetectedLanguage() {
      return detectedLanguage;
    }

    public String getText() {
      return text;
    }
  }

}
