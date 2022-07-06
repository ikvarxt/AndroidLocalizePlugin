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

package com.airsaid.localization.task;

import com.airsaid.localization.constant.Constants;
import com.airsaid.localization.services.AndroidValuesService;
import com.airsaid.localization.translate.lang.Lang;
import com.airsaid.localization.translate.lang.Languages;
import com.airsaid.localization.translate.services.TranslatorService;
import com.airsaid.localization.utils.ExportExcelOperator;
import com.airsaid.localization.utils.TextUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.psi.xml.XmlText;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author airsaid
 */
public class TranslateTask extends Task.Backgroundable {

  private static final String NAME_TAG_STRING = "string";
  private static final String NAME_TAG_PLURALS = "plurals";
  private static final String NAME_TAG_STRING_ARRAY = "string-array";

  private static final Logger LOG = Logger.getInstance(TranslateTask.class);

  private final List<Lang> mToLanguages;
  /**
   * original strings key set
   */
  private final List<PsiElement> mValues;
  private final VirtualFile mValueFile;
  private final TranslatorService mTranslatorService;
  private final AndroidValuesService mValueService;

  private OnTranslateListener mOnTranslateListener;

  public interface OnTranslateListener {
    void onTranslateSuccess();

    void onTranslateError(Throwable e);
  }

  public TranslateTask(@Nullable Project project, @Nls @NotNull String title,
                       List<PsiElement> values, PsiFile valueFile) {
    super(project, title);

    mValues = values;
    mValueFile = valueFile.getVirtualFile();
    mTranslatorService = TranslatorService.getInstance();
    mValueService = AndroidValuesService.getInstance();

    mToLanguages = getExistsLanguages();
  }

  /**
   * Set translate result listener.
   *
   * @param listener callback interface. success or fail.
   */
  public void setOnTranslateListener(OnTranslateListener listener) {
    mOnTranslateListener = listener;
  }

  @Override
  public void run(@NotNull ProgressIndicator progressIndicator) {
    if (myProject == null) return;

    // TODO: 7/4/22 discuss if remove this in the future
    boolean isOverwriteExistingString = PropertiesComponent.getInstance(myProject)
        .getBoolean(Constants.KEY_IS_OVERWRITE_EXISTING_STRING);
    LOG.info("run isOverwriteExistingString: " + isOverwriteExistingString);

    Set<String> changedValue = getVcsChangedValues(myProject, mValueFile);

    ExportExcelOperator operator = new ExportExcelOperator();
    operator.fillDefaultStrings(mValues);

    // start to translate to target languages
    for (int i = 0; i < mToLanguages.size(); i++) {
      if (progressIndicator.isCanceled()) break;

      Lang toLanguage = mToLanguages.get(i);
      progressIndicator.setText("Translating in the " + toLanguage.getEnglishName() + " language...");

      // find res dir
      VirtualFile resourceDir = mValueFile.getParent().getParent();
      String valueFileName = mValueFile.getName();
      // find target values file in psi format
      PsiFile toValuePsiFile = mValueService.getValuePsiFile(myProject, resourceDir, toLanguage, valueFileName);
      LOG.info("Translating language: " + toLanguage.getEnglishName() + ", toValuePsiFile: " + toValuePsiFile);
      // when current language's values xml file is exists
      if (toValuePsiFile != null) {
        // load target strings EntrySet
        List<PsiElement> toValues = mValueService.loadValues(toValuePsiFile);
        Map<String, PsiElement> toValuesMap = toValues.stream().collect(Collectors.toMap(
            psiElement -> {
              if (psiElement instanceof XmlTag) {
                XmlTag xmlTag = (XmlTag) psiElement;
                // read name attribute in <string/> tag
                return ApplicationManager.getApplication().runReadAction((Computable<String>) () ->
                    xmlTag.getAttributeValue("name"));
              } else {
                return UUID.randomUUID().toString();
              }
            },
            Function.identity()
        ));
        List<PsiElement> translatedValues = doTranslate(progressIndicator, toLanguage, toValuesMap, isOverwriteExistingString, changedValue);
        writeTranslatedValues(progressIndicator, new File(toValuePsiFile.getVirtualFile().getPath()), translatedValues);
        // TODO: 7/5/22 select a output store position
        operator.writeLanguageColumn(i + 2, mValues, translatedValues, toLanguage);
      } else {
        List<PsiElement> translatedValues = doTranslate(progressIndicator, toLanguage, null, isOverwriteExistingString, changedValue);
        File valueFile = mValueService.getValueFile(resourceDir, toLanguage, valueFileName);
        writeTranslatedValues(progressIndicator, valueFile, translatedValues);
        operator.writeLanguageColumn(i + 2, mValues, translatedValues, toLanguage);
      }
    }

    File outputPath = mTranslatorService.getOutputExcelPath();
    operator.saveToDisk(outputPath);
  }

  private List<PsiElement> doTranslate(@NotNull ProgressIndicator progressIndicator,
                                       @NotNull Lang toLanguage,
                                       @Nullable Map<String, PsiElement> toValues,
                                       boolean isOverwrite,
                                       @NotNull Set<String> dirtyValues) {
    LOG.info("doTranslate toLanguage: " + toLanguage.getEnglishName() + ", toValues: " + toValues + ", isOverwrite: " + isOverwrite);

    // stores translate result values, add it without translate means it has been translated
    List<PsiElement> translatedValues = new ArrayList<>();
    // TODO: 7/5/22 passing the new original values
    for (PsiElement value : mValues) {
      if (progressIndicator.isCanceled()) break;

      if (value instanceof XmlTag) {
        XmlTag xmlTag = (XmlTag) value;
        // add the value that shouldn't be translated
        if (!mValueService.isTranslatable(xmlTag)) {
          // TODO: 7/5/22 check if this is proper
//          translatedValues.add(value);
          continue;
        }

        String name = ApplicationManager.getApplication().runReadAction((Computable<String>) () ->
            xmlTag.getAttributeValue("name")
        );

        // if it doesn't overwrite and target strings already have the string, add it
        if (!isOverwrite && !dirtyValues.contains(name) && toValues != null && toValues.containsKey(name)) {
          translatedValues.add(toValues.get(name));
          continue;
        }

        XmlTag translateValue = ApplicationManager.getApplication().runReadAction((Computable<XmlTag>) () ->
            (XmlTag) xmlTag.copy()
        );
        translatedValues.add(translateValue);
        switch (translateValue.getName()) {
          case NAME_TAG_STRING:
            // translate the copy of xmlTag
            doTranslate(progressIndicator, toLanguage, translateValue);
          case NAME_TAG_STRING_ARRAY:
          case NAME_TAG_PLURALS: {
            XmlTag[] subTags = ApplicationManager.getApplication()
                .runReadAction((Computable<XmlTag[]>) translateValue::getSubTags);
            for (XmlTag subTag : subTags) {
              doTranslate(progressIndicator, toLanguage, subTag);
            }
          }
        }
      } else {
        translatedValues.add(value);
      }
    }
    return translatedValues;
  }

  private void doTranslate(@NotNull ProgressIndicator progressIndicator,
                           @NotNull Lang toLanguage,
                           @NotNull XmlTag xmlTag) {
    if (progressIndicator.isCanceled()) return;

    XmlTagValue xmlTagValue = ApplicationManager.getApplication()
        .runReadAction((Computable<XmlTagValue>) xmlTag::getValue);
    XmlTagChild[] children = xmlTagValue.getChildren();
    for (XmlTagChild child : children) {
      if (child instanceof XmlText) {
        XmlText xmlText = (XmlText) child;
        String text = ApplicationManager.getApplication().runReadAction((Computable<String>) xmlText::getText);
        // if target value is blank, continue
        if (TextUtil.isEmptyOrSpacesLineBreak(text)) {
          continue;
        }
        String translatedText = mTranslatorService.doTranslate(myProject, Languages.ENGLISH, toLanguage, text);
        ApplicationManager.getApplication().runReadAction(() -> xmlText.setValue(translatedText));
      }
    }
  }

  private void writeTranslatedValues(@NotNull ProgressIndicator progressIndicator,
                                     @NotNull File valueFile,
                                     @NotNull List<PsiElement> translatedValues) {
    LOG.info("writeTranslatedValues valueFile: " + valueFile + ", translatedValues: " + translatedValues);

    if (progressIndicator.isCanceled() || translatedValues.isEmpty()) return;

    progressIndicator.setText("Writing to " + valueFile.getParentFile().getName() + " data...");
    mValueService.writeValueFile(translatedValues, valueFile);

    refreshAndOpenFile(valueFile);
  }

  private void refreshAndOpenFile(File file) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    boolean isOpenTranslatedFile = PropertiesComponent.getInstance(myProject)
        .getBoolean(Constants.KEY_IS_OPEN_TRANSLATED_FILE);
    if (virtualFile != null && isOpenTranslatedFile) {
      ApplicationManager.getApplication().invokeLater(() ->
          FileEditorManager.getInstance(myProject).openFile(virtualFile, true));
    }
  }

  /**
   * get the string names of local vcs changed value
   *
   * @param project    current project
   * @param valuesFile the default values file
   * @return set of changed string names
   */
  private Set<String> getVcsChangedValues(Project project, VirtualFile valuesFile) {
    Set<String> set = new HashSet<>();

    Git git = Git.getInstance();
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(valuesFile);
    if (repository == null) return set;

    GitCommandResult result = git.diff(repository, List.of("-U0"), valuesFile.getPath());
    List<String> resultOutput = result.getOutput();

    Pattern pattern = Pattern.compile("name=\"[\\w]*\"");

    for (String line : resultOutput) {
      // filter out diff output like "+++ b/app/src/main/res/values/strings.xml"
      if (line.startsWith("+") && line.indexOf(1) != '+') {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
          String namePattern = "name=\"";
          int startQuoteIndex = line.indexOf(namePattern) + namePattern.length();
          int endQuoteIndex = line.indexOf("\"", startQuoteIndex);
          String name = line.substring(startQuoteIndex, endQuoteIndex);
          set.add(name);
        }
      }
    }

    return set;
  }

  private List<Lang> getExistsLanguages() {
    List<Lang> res = new ArrayList<>();
    VirtualFile resourceDir = mValueFile.getParent().getParent();
    if (resourceDir == null) return res;

    return mValueService.getExistsLang(resourceDir);
  }

  @Override
  public void onSuccess() {
    super.onSuccess();
    translateSuccess();
  }

  @Override
  public void onThrowable(@NotNull Throwable error) {
    super.onThrowable(error);
    translateError(error);
  }

  private void translateSuccess() {
    if (mOnTranslateListener != null) {
      mOnTranslateListener.onTranslateSuccess();
    }
  }

  private void translateError(Throwable error) {
    if (mOnTranslateListener != null) {
      mOnTranslateListener.onTranslateError(error);
    }
  }
}
