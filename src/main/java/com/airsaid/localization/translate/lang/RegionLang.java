package com.airsaid.localization.translate.lang;

/**
 * @author ikvarxt
 */
public class RegionLang extends Lang {

  private final String regionCode;

  public RegionLang(int id, String code, String name, String englishName, String regionCode) {
    super(id, code, name, englishName);

    this.regionCode = regionCode;
  }

  public String getRegionCode() {
    return regionCode;
  }

  @Override
  public String toString() {
    return "Lang{" +
        "id=" + super.id +
        ", code='" + super.code + '\'' +
        ", name='" + super.name + '\'' +
        ", englishName='" + super.englishName + '\'' +
        ", translationCode='" + super.translationCode + '\'' +
        ", regionCode='" + regionCode + '\'' +
        '}';
  }
}
