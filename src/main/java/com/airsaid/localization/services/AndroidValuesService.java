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

package com.airsaid.localization.services;

import com.airsaid.localization.translate.lang.Lang;
import com.airsaid.localization.translate.lang.Languages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Operation service for the android value files. eg: strings.xml or plurals.xml or arrays.xml.
 *
 * @author airsaid
 */
@Service
public final class AndroidValuesService {

  private static final Logger LOG = Logger.getInstance(AndroidValuesService.class);

  public static final String NAME_STRINGS_FILE = "strings.xml";
  public static final String NAME_PLURALS_FILE = "plurals.xml";
  public static final String NAME_ARRAYS_FILE = "arrays.xml";

  private static final String NAME_DEFAULT_VALUES = "values";

  /**
   * Returns the {@link AndroidValuesService} object instance.
   *
   * @return the {@link AndroidValuesService} object instance.
   */
  public static AndroidValuesService getInstance() {
    return ServiceManager.getService(AndroidValuesService.class);
  }

  /**
   * Asynchronous loading the value file as the {@link PsiElement} collection.
   *
   * @param valueFile the value file.
   * @param consumer  load result. called in the event dispatch thread.
   */
  public void loadValuesByAsync(@NotNull PsiFile valueFile, @NotNull Consumer<List<PsiElement>> consumer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
          List<PsiElement> values = loadValues(valueFile);
          ApplicationManager.getApplication().invokeLater(() ->
              consumer.consume(values));
        }
    );
  }

  /**
   * Loading the value file as the {@link PsiElement} collection.
   *
   * @param valueFile the value file.
   * @return {@link PsiElement} collection.
   */
  public List<PsiElement> loadValues(@NotNull PsiFile valueFile) {
    return ApplicationManager.getApplication().runReadAction((Computable<List<PsiElement>>) () -> {
      LOG.info("loadValues valueFile: " + valueFile.getName());
      List<PsiElement> values = parseValuesXml(valueFile);
      LOG.info("loadValues parsed " + valueFile.getName() + " result: " + values);
      return values;
    });
  }

  private List<PsiElement> parseValuesXml(@NotNull PsiFile valueFile) {
    final List<PsiElement> values = new ArrayList<>();
    final XmlFile xmlFile = (XmlFile) valueFile;

    final XmlDocument document = xmlFile.getDocument();
    if (document == null) return values;

    final XmlTag rootTag = document.getRootTag();
    if (rootTag == null) return values;

    PsiElement[] subTags = rootTag.getChildren();
    values.addAll(Arrays.asList(subTags));

    return values;
  }

  /**
   * Write {@link PsiElement} collection data to the specified file.
   *
   * @param values    specified {@link PsiElement} collection data.
   * @param valueFile specified file.
   */
  public void writeValueFile(@NotNull List<PsiElement> values, @NotNull File valueFile) {
    boolean isCreateSuccess = FileUtil.createIfDoesntExist(valueFile);
    if (!isCreateSuccess) {
      LOG.error("Failed to write to " + valueFile.getPath() + " file: create failed!");
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
      try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(valueFile, false), StandardCharsets.UTF_8))) {
        for (PsiElement value : values) {
          bw.write(value.getText());
        }
        bw.flush();
      } catch (IOException e) {
        e.printStackTrace();
        LOG.error("Failed to write to " + valueFile.getPath() + " file.", e);
      }
    }));
  }

  /**
   * Verify that the file is a values/string.xml or plurals.xml or arrays.xml file.
   *
   * @param file the verify file.
   * @return true: the file is values/string.xml or plurals.xml or arrays.xml file.
   */
  public boolean isValueFile(@Nullable PsiFile file) {
    if (file == null) return false;

    PsiDirectory parent = file.getParent();
    if (parent == null) return false;

    String parentName = parent.getName();
    if (!"values".equals(parentName)) return false;

    String fileName = file.getName();
    return NAME_STRINGS_FILE.equals(fileName) ||
        NAME_PLURALS_FILE.equals(fileName) ||
        NAME_ARRAYS_FILE.equals(fileName);
  }

  /**
   * Get the value file of the specified language in the specified project resource directory.
   *
   * @param project     current project.
   * @param resourceDir specified resource directory.
   * @param lang        specified language.
   * @param fileName    the name of value file.
   * @return null if not exist, otherwise return the value file.
   */
  @Nullable
  public PsiFile getValuePsiFile(@NotNull Project project,
                                 @NotNull VirtualFile resourceDir,
                                 @NotNull Lang lang,
                                 @NotNull String fileName) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () -> {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(getValueFile(resourceDir, lang, fileName));
      if (virtualFile == null) {
        return null;
      }
      return PsiManager.getInstance(project).findFile(virtualFile);
    });
  }

  /**
   * Get the value file of app default strings.xml
   *
   * @param project     current project.
   * @param resourceDir default resource directory
   * @return the default value file in PSI formats
   */
  @Nullable
  public PsiFile getDefaultValuesPsiFile(@NotNull Project project, @NotNull VirtualFile resourceDir) {
    return ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () -> {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(getDefaultValueFile(resourceDir));
      if (virtualFile == null) {
        return null;
      }
      return PsiManager.getInstance(project).findFile(virtualFile);
    });
  }

  /**
   * get the default strings xml virtual file
   * @param project current project
   * @param resourceDir resource directory virtual file
   * @return the default strings xml file
   */
  @Nullable
  public VirtualFile getDefaultValuesVirtualFile(@NotNull Project project, @NotNull VirtualFile resourceDir) {
    return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>) () -> {
      return LocalFileSystem.getInstance().findFileByIoFile(getDefaultValueFile(resourceDir));
    });
  }

  /**
   * Get the value file in the {@code values} directory of the specified language in the resource directory.
   *
   * @param resourceDir specified resource directory.
   * @param lang        specified language.
   * @param fileName    the name of value file.
   * @return the value file.
   */
  @NotNull
  public File getValueFile(@NotNull VirtualFile resourceDir, @NotNull Lang lang, String fileName) {
    return new File(resourceDir.getPath().concat(File.separator).concat(getValuesDirectoryName(lang)), fileName);
  }

  private String getValuesDirectoryName(@NotNull Lang lang) {
    return "values-".concat(lang.getCode());
  }

  /**
   * Get the default strings.xml file in the default values directory
   *
   * @param resourceDir project default resource directory
   * @return the default value file
   */
  public File getDefaultValueFile(@NotNull VirtualFile resourceDir) {
    return new File(resourceDir.getPath().concat(File.separator).concat("values"), NAME_STRINGS_FILE);
  }

  /**
   * Returns whether the specified xml tag (string entry) needs to be translated.
   *
   * @param xmlTag the specified xml tag of string entry.
   * @return true: need translation. false: no translation is needed.
   */
  public boolean isTranslatable(@NotNull XmlTag xmlTag) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
      String translatableStr = xmlTag.getAttributeValue("translatable");
      return Boolean.parseBoolean(translatableStr == null ? "true" : translatableStr);
    });
  }

  /**
   * Returns is the given file is correct Android res directory
   *
   * @param file /src/main/res file in Android Project
   * @return if the given directory is Android res directory
   */
  public boolean isValidResDir(@Nullable PsiFile file) {
    if (file == null) return false;

    PsiDirectory resDirs = file.getContainingDirectory();
    if (resDirs == null) return false;

    PsiFile[] files = resDirs.getFiles();

    for (PsiFile item : files) {
      if (item.getName().equals(NAME_DEFAULT_VALUES)) {
        PsiDirectory valuesDir = item.getContainingDirectory();
        for (PsiFile stringFile : valuesDir.getFiles()) {
          if (NAME_STRINGS_FILE.equals(stringFile.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean isValidResDir(@Nullable VirtualFile file) {
    if (file == null) return false;

    VirtualFile valuesDir = file.findChild(NAME_DEFAULT_VALUES);
    if (valuesDir == null) return false;

    VirtualFile stringFile = valuesDir.findChild(NAME_STRINGS_FILE);
    return stringFile != null;
  }

  public List<Lang> getExistsLang(@Nullable VirtualFile resourceDir) {
    List<Lang> res = new ArrayList<>();
    if (resourceDir == null) return res;

    for (VirtualFile subDirs : resourceDir.getChildren()) {
      String dirName = subDirs.getName();
      if (dirName.startsWith(NAME_DEFAULT_VALUES.concat("-"))) {
        // split dir name to three parts: values; language code; region code
        String[] destructDirName = dirName.split("-");

        String langCode = destructDirName[1];
        // if it has region, set it
        if (destructDirName.length > 2) {
          String region = destructDirName[2];

          Lang langWithRegion = Languages.getLang(langCode.concat("-").concat(region));
          if (langWithRegion != null) res.add(langWithRegion);
          continue;
        }

        Lang lang = Languages.getLang(langCode);
        if (lang != null && !res.contains(lang)) {
          res.add(lang);
        }
      }
    }

    return res;
  }
}
