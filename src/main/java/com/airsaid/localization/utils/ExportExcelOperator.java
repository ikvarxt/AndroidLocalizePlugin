package com.airsaid.localization.utils;

import com.airsaid.localization.translate.lang.Lang;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExportExcelOperator {

  private final XSSFWorkbook workbook;
  private final XSSFSheet sheet;

  public ExportExcelOperator() {
    workbook = new XSSFWorkbook();
    sheet = workbook.createSheet();
    Row languagesNameRow = sheet.createRow(0);
    languagesNameRow.createCell(0).setCellValue("Translate Output");
    languagesNameRow.createCell(1).setCellValue("Default");
  }

  public XSSFWorkbook getWorkbook() {
    return workbook;
  }

  public XSSFSheet getSheet() {
    return sheet;
  }

  public void fillDefaultStrings(@NotNull List<PsiElement> values) {
    int rowCount = 1;
    for (PsiElement element : values) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag) element;
        Pair<String, String> pair = ApplicationManager.getApplication().runReadAction((Computable<Pair<String, String>>) () ->
            Pair.create(tag.getAttributeValue("name"), tag.getValue().getText())
        );

        Row row = sheet.createRow(rowCount);
        rowCount++;
        row.createCell(0).setCellValue(pair.first);
        row.createCell(1).setCellValue(pair.second);
      }
    }
  }

  // TODO: 7/5/22 figure out how to export plurals data
  public void writeLanguageColumn(int column, @NotNull List<PsiElement> originalValues, @NotNull List<PsiElement> translatedValues, @NotNull Lang toLang) {
    Map<String, PsiElement> translatedValuesMap = translatedValues.stream().collect(Collectors.toMap(
        psiElement -> {
          if (psiElement instanceof XmlTag) {
            XmlTag tag = (XmlTag) psiElement;
            return ApplicationManager.getApplication().runReadAction((Computable<String>) () ->
                tag.getAttributeValue("name"));
          } else {
            return UUID.randomUUID().toString();
          }
        },
        Function.identity()
    ));

    // fill the header row with target language code
    Row headerRow = sheet.getRow(0);
    headerRow.createCell(column).setCellValue(toLang.getCode());

    // started with first row of data
    int rowCount = 1;
    for (PsiElement element : originalValues) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag) element;
        String name = ApplicationManager.getApplication().runReadAction((Computable<String>) () -> tag.getAttributeValue("name"));
        if (translatedValuesMap.containsKey(name)) {
          // skip the header row
          Row currentRow = sheet.getRow(rowCount);
          // find the translated element from map
          PsiElement translatedElement = translatedValuesMap.get(name);
          if (translatedElement instanceof XmlTag) {
            XmlTag translatedTag = (XmlTag) translatedElement;
            String value = ApplicationManager.getApplication().runReadAction(
                (Computable<String>) () -> translatedTag.getValue().getText());
            currentRow.createCell(column).setCellValue(value);
          }
        }
        // encounter with untranslatable strings, row number is still increase
        rowCount++;
      }
    }
  }

  // TODO: 7/6/22 deal with return value
  public void saveToDisk(File outputDirectory) {
    String time = String.valueOf(System.currentTimeMillis());
    File file = new File(outputDirectory, "Export-".concat(time).concat(".xlsx"));

    try {
      if (file.exists()) {
        file.delete();
      }
      boolean isCreated = file.createNewFile();
      if (isCreated) {
        FileOutputStream outputStream = new FileOutputStream(file);
        workbook.write(outputStream);
      }
      workbook.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
