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

package com.airsaid.localization.action;

import com.airsaid.localization.config.SettingsState;
import com.airsaid.localization.services.AndroidValuesService;
import com.airsaid.localization.task.TranslateTask;
import com.airsaid.localization.utils.NotificationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Translate android string value to other languages that can be used to localize your Android APP.
 *
 * @author airsaid
 */
public class TranslateAction extends AnAction {

  private Project mProject;
  // resources dir file
  private VirtualFile resourcesDir;
  // the original strings.xml file that stores in res/ directory
  private PsiFile mValueFile;
  private List<PsiElement> mValues;
  private final AndroidValuesService mValueService = AndroidValuesService.getInstance();

  @Override
  public void actionPerformed(AnActionEvent e) {
    mProject = e.getRequiredData(CommonDataKeys.PROJECT);
    // TODO: 6/30/22 should here using PSI_FILE
    resourcesDir = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);

    mValueFile = mValueService.getDefaultValuesPsiFile(mProject, resourcesDir);

    SettingsState.getInstance().initSetting();

    // load values file from disk
    mValueService.loadValuesByAsync(mValueFile, values -> {
      if (!isTranslatable(values)) {
        NotificationUtil.notifyInfo(mProject, "The " + mValueFile.getName() + " has no text to translate.");
        return;
      }
      mValues = values;

      doTranslate();
    });
  }

  // Verify that there is a text in the value file that needs to be translated.
  private boolean isTranslatable(@NotNull List<PsiElement> values) {
    for (PsiElement psiElement : values) {
      if (psiElement instanceof XmlTag) {
        if (mValueService.isTranslatable((XmlTag) psiElement)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // The translation option is only show when strings.xml/plurals.xml/arrays.xml is selected
    Project project = e.getData(CommonDataKeys.PROJECT);
    // TODO: 6/30/22 should get file with psi file
    VirtualFile resDirFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

    boolean isValidResDir = mValueService.isValidResDir(resDirFile);
    e.getPresentation().setEnabledAndVisible(project != null && isValidResDir);
  }

  private void doTranslate() {
    TranslateTask translationTask = new TranslateTask(mProject, "Translating...", mValues, mValueFile);
    translationTask.setOnTranslateListener(new TranslateTask.OnTranslateListener() {
      @Override
      public void onTranslateSuccess() {
        NotificationUtil.notifyInfo(mProject, "Translation completed!");

        // TODO: 6/30/22 check if this refresh is correct
        resourcesDir.refresh(false, true);
      }

      @Override
      public void onTranslateError(Throwable e) {
        NotificationUtil.notifyError(mProject, "Translation failure: " + e.getLocalizedMessage());
      }
    });
    translationTask.queue();
  }
}
