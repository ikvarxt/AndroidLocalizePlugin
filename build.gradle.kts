fun properties(key: String) = project.findProperty(key).toString()

plugins {
  // Java support
  id("java")
  // Gradle IntelliJ Plugin
  id("org.jetbrains.intellij") version "1.6.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
  mavenCentral()
}

//configurations{
//  all {
//    // 再也不要让我在项目里看到这个东西，md
//    // 之后如果引入了新依赖，一定要检查是否项目依赖了此包，干掉他！
//    exclude(group = "pull-parser")
//  }
//}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
  pluginName.set(properties("pluginName"))
  version.set(properties("platformVersion"))
  type.set(properties("platformType"))

  // using local idea for develop
//  localPath.set("/Applications/IntelliJ IDEA CE.app/Contents")

  // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
  plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

tasks {
  // Set the JVM compatibility versions
  properties("javaVersion").let {
    withType<JavaCompile> {
      sourceCompatibility = it
      targetCompatibility = it
      options.encoding = "UTF-8"
    }
  }

  wrapper {
    gradleVersion = properties("gradleVersion")
  }

  patchPluginXml {
    version.set(properties("pluginVersion"))
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))
  }

  test {
    useJUnitPlatform()
  }

  publishPlugin {
    token.set(System.getenv("INTELLIJ_PUBLISH_TOKEN"))
  }
}

dependencies {
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("org.apache.poi:poi-ooxml:5.2.2")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}